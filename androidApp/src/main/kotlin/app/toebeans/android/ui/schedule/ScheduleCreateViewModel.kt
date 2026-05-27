package app.toebeans.android.ui.schedule

import androidx.lifecycle.ViewModel
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.scheduler.ReminderRescheduler
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.MalformedScheduleException
import app.toebeans.core.scheduler.ScheduleCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Schedule-create form state. Holds a mutable list of phase drafts. On save, the drafts are
 * validated and converted to immutable [SchedulePhase] rows.
 *
 * Why a separate draft type ([PhaseDraft]) rather than mutating [SchedulePhase] directly:
 * SchedulePhase has an `init` block that rejects invalid combinations. While the user is
 * editing, intermediate values WILL be invalid (e.g. empty dose-time list, durationDays = 0).
 * The draft type permits invalid intermediate states; conversion to SchedulePhase happens
 * only on submit, where validation messages are surfaced to the user.
 */
@OptIn(ExperimentalUuidApi::class)
public class ScheduleCreateViewModel(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
    private val scheduleCalculator: ScheduleCalculator,
    private val notificationActuator: NotificationActuator,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            ScheduleCreateUiState(
                startDate =
                    Clock.System
                        .now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date,
                phases = listOf(blankPhaseDraft()),
            ),
        )
    public val state: StateFlow<ScheduleCreateUiState> = _state.asStateFlow()

    public fun setMedicationId(medicationId: String) {
        _state.update { it.copy(medicationId = medicationId) }
    }

    public suspend fun loadMedication() {
        val id = _state.value.medicationId ?: return
        _state.update { it.copy(medication = medicationRepository.getById(id)) }
    }

    public fun onStartDateChange(value: LocalDate?) {
        _state.update { it.copy(startDate = value, startDateError = null, formError = null) }
    }

    public fun onEndDateChange(value: LocalDate?) {
        _state.update { it.copy(endDate = value, formError = null) }
    }

    public fun addPhase() {
        _state.update { it.copy(phases = it.phases + blankPhaseDraft(), formError = null) }
    }

    public fun removePhase(index: Int) {
        _state.update { state ->
            if (state.phases.size <= 1) {
                state
            } else {
                state.copy(
                    phases = state.phases.filterIndexed { i, _ -> i != index },
                    formError = null,
                )
            }
        }
    }

    public fun updatePhase(
        index: Int,
        transform: (PhaseDraft) -> PhaseDraft,
    ) {
        // Clear the per-phase error on any field change so the red message disappears as
        // soon as the user begins addressing what caused it. The error re-renders only if
        // a subsequent save() attempt still fails. Same for the form-level [formError]
        // banner — any field touch resets the calculator-preflight verdict.
        //
        // Also recompute the night-dose warning (B9): any LocalTime in [00:00, 06:00)
        // triggers the warning, and any edit resets [PhaseDraft.nightDoseAffirmed] to
        // false so the user re-confirms after changes. The recomputation is authoritative
        // — even if the caller's transform set nightDoseWarning to a stale value via
        // copy(), the values below overwrite it.
        //
        // Additionally recompute the midnight-straddle flag (v0.1-followups #9). Same
        // authoritative-overwrite pattern as nightDoseWarning. The straddle warning is
        // informational only — no affirmation flag, no save-time gating — so it does
        // not need an "affirmed" companion field.
        _state.update { state ->
            state.copy(
                phases =
                    state.phases.mapIndexed { i, phase ->
                        if (i == index) {
                            val transformed = transform(phase).copy(error = null)
                            val hasNightDose = transformed.doseTimes.any { it.isInNightWindow() }
                            val straddles = MidnightStraddleDetection.crossesMidnight(transformed.doseTimes)
                            transformed.copy(
                                nightDoseWarning = hasNightDose,
                                nightDoseAffirmed = false,
                                crossesMidnight = straddles,
                            )
                        } else {
                            phase
                        }
                    },
                formError = null,
            )
        }
    }

    /**
     * Affirm the night-dose warning on the phase at [index]. Per ADR-0004 D2 +
     * v0.1-followups #1, this is the explicit "Yes, that's intentional" action — it
     * clears [PhaseDraft.nightDoseWarning] and sets [PhaseDraft.nightDoseAffirmed].
     *
     * Affirmation is reset by any subsequent edit via [updatePhase]; see the KDoc on
     * [PhaseDraft] for the rationale.
     *
     * No-op if [index] is out of range or if the phase has no warning to affirm (calling
     * affirm on a phase with all-daytime doses just sets the affirmed flag harmlessly).
     */
    public fun affirmNightDose(index: Int) {
        _state.update { state ->
            if (index !in state.phases.indices) {
                state
            } else {
                state.copy(
                    phases =
                        state.phases.mapIndexed { i, phase ->
                            if (i == index) {
                                phase.copy(nightDoseWarning = false, nightDoseAffirmed = true)
                            } else {
                                phase
                            }
                        },
                )
            }
        }
    }

    /**
     * `[00:00, 06:00)` — inclusive at midnight, exclusive at 6am, per ADR-0004 D2's
     * worked example ("first dose at startDate 0000 local" must trigger) and the natural
     * UX read of "6am is when people wake up on purpose, nothing to nudge about."
     */
    private fun LocalTime.isInNightWindow(): Boolean = this.hour < 6

    /**
     * Convert the form state to a [Schedule] + [SchedulePhase] list and persist.
     * Returns the created Schedule.id on success, or null if validation failed.
     */
    public suspend fun save(): String? {
        val s = _state.value
        val medId = s.medicationId ?: return null

        if (s.startDate == null) {
            _state.update { it.copy(startDateError = "Required") }
            return null
        }

        // Validate phases. The first error per phase is surfaced.
        val phasesWithErrors =
            s.phases.mapIndexed { idx, draft ->
                validatePhase(draft, idx)
            }
        if (phasesWithErrors.any { it.second != null }) {
            _state.update {
                it.copy(
                    phases =
                        phasesWithErrors.map { (draft, err) ->
                            if (err != null) draft.copy(error = err) else draft
                        },
                )
            }
            return null
        }

        val (schedule, phases) = newSchedulePayload(s, medId)

        // Pre-flight the calculator across a representative window before persisting.
        // The Reminders/Today renderers run the same calculator at view-time; if it would
        // throw a MalformedScheduleException there, surface that here as an inline form
        // error rather than letting the user save a schedule that explodes downstream.
        //
        // The window is `[startDate 00:00, startDate + MAX_WINDOW_DAYS)` in the user's
        // local zone — the same shape Home/Reminders ask for. Most realistic schedules
        // produce zero or a few thousand events here; the EventCountExceeded path fires
        // only on pathological combinations (e.g. day-interval=1 × max-doses-per-day ×
        // long durations stacked into many phases) that we want to catch at create-time.
        //
        // Note: DuplicatePhaseOrder and PhaseOrderGap cannot fire because save() assigns
        // phaseOrder = idx in a dense sequence by construction. But running the full
        // calculator anyway keeps the preflight resilient to future refactors that might
        // let user-supplied phaseOrder values back into the form.
        val preflightError = runPreflight(schedule, phases)
        if (preflightError != null) {
            _state.update { it.copy(formError = preflightError) }
            return null
        }

        upsertMaterializeAndSchedule(schedule, phases, medId)
        return schedule.id
    }

    private fun newSchedulePayload(
        s: ScheduleCreateUiState,
        medId: String,
    ): Pair<Schedule, List<SchedulePhase>> {
        val scheduleId = "sched-${Uuid.random()}"
        val phases =
            s.phases.mapIndexed { idx, draft ->
                SchedulePhase(
                    id = "phase-${Uuid.random()}",
                    scheduleId = scheduleId,
                    phaseOrder = idx,
                    durationDays = draft.durationDaysText.toInt(),
                    dosesPerDay = draft.doseTimes.size,
                    doseTimesLocal = draft.doseTimes.sorted(),
                    doseAmount = draft.doseAmount.trim().ifEmpty { null },
                    dayInterval = draft.dayIntervalText.toIntOrNull() ?: 1,
                )
            }
        val schedule =
            Schedule(
                id = scheduleId,
                medicationId = medId,
                startDate = s.startDate!!,
                endDate = s.endDate,
                createdAt = Clock.System.now(),
            )
        return schedule to phases
    }

    private suspend fun upsertMaterializeAndSchedule(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        medicationId: String,
    ) {
        scheduleRepository.upsert(schedule, phases)
        val reminders =
            ReminderRescheduler.materializeHorizonForSchedule(
                schedule = schedule,
                phases = phases,
                medicationId = medicationId,
                doseEventRepository = doseEventRepository,
                scheduleCalculator = scheduleCalculator,
                timeZone = timeZone,
                now = Clock.System.now(),
            )
        for (reminder in reminders) {
            notificationActuator.schedule(reminder)
        }
    }

    /**
     * Execute the calculator against a 30-day window starting at [Schedule.startDate].
     * Returns a user-facing error string keyed off the [MalformedScheduleException]
     * subclass, or `null` if the calculator succeeds.
     *
     * Pulled out so tests can call it without going through full persistence.
     */
    internal fun runPreflight(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ): String? {
        val from = schedule.startDate.atStartOfDayIn(timeZone)
        val to = from + DefaultScheduleCalculator.MAX_WINDOW_DAYS.days
        return try {
            scheduleCalculator.computeScheduledDoses(
                schedule = schedule,
                phases = phases,
                timeZone = timeZone,
                fromInclusive = from,
                toExclusive = to,
            )
            null
        } catch (e: MalformedScheduleException.EventCountExceeded) {
            "This schedule would generate ${e.attemptedCount} doses in 30 days — more than " +
                "the safe limit (${e.maxCount}). Reduce the number of phases, doses per day, " +
                "or stretch the skip-day interval."
        } catch (e: MalformedScheduleException.DuplicatePhaseOrder) {
            // Defense-in-depth: not reachable from the current save() loop, but if a
            // future refactor exposes user-supplied phaseOrder values this would catch
            // them before persistence.
            "Two phases share the same position (${e.phaseOrder}). Each phase needs its own slot."
        } catch (e: MalformedScheduleException.PhaseOrderGap) {
            "Phases are out of order (${e.phaseOrders}). Remove the gaps and try again."
        } catch (_: MalformedScheduleException.WindowNotPositive) {
            // Should be impossible: we construct a 30-day window. Surfaces as a generic
            // failure if it ever does fire, so the user isn't stuck staring at a silent
            // form. The exception is intentionally not threaded into the user message
            // because window arithmetic isn't actionable; the `_` binding tells detekt
            // we're aware of SwallowedException and the swallow is by design.
            "Couldn't validate this schedule — the preview window was invalid. Please retry."
        } catch (_: MalformedScheduleException.WindowTooLarge) {
            // Same swallow rationale as WindowNotPositive above: window construction is
            // not user-controllable, so the exception detail isn't useful to surface.
            "Couldn't validate this schedule — the preview window was too large. Please retry."
        } catch (e: MalformedScheduleException) {
            // Catch-all for future MalformedScheduleException subclasses so we don't leak
            // a generic IllegalArgumentException through onto the user's screen.
            "This schedule isn't valid: ${e.message ?: e.code}"
        }
    }

    private fun validatePhase(
        draft: PhaseDraft,
        index: Int,
    ): Pair<PhaseDraft, String?> {
        val durationOk = draft.durationDaysText.toIntOrNull()?.let { it in 1..SchedulePhase.MAX_DURATION_DAYS } == true
        if (!durationOk) {
            return draft to "Phase ${index + 1}: duration must be 1..${SchedulePhase.MAX_DURATION_DAYS} days"
        }
        if (draft.doseTimes.isEmpty()) {
            return draft to "Phase ${index + 1}: add at least one dose time"
        }
        if (draft.doseTimes.size > SchedulePhase.MAX_DOSES_PER_DAY) {
            return draft to "Phase ${index + 1}: max ${SchedulePhase.MAX_DOSES_PER_DAY} doses per day"
        }
        val distinct = draft.doseTimes.toSet()
        if (distinct.size != draft.doseTimes.size) {
            return draft to "Phase ${index + 1}: dose times must be unique"
        }
        val intervalOk = draft.dayIntervalText.toIntOrNull()?.let { it in 1..SchedulePhase.MAX_DAY_INTERVAL } == true
        if (!intervalOk) {
            return draft to "Phase ${index + 1}: skip-day interval must be 1..${SchedulePhase.MAX_DAY_INTERVAL}"
        }
        return draft to null
    }

    private fun blankPhaseDraft(): PhaseDraft =
        PhaseDraft(
            durationDaysText = "7",
            doseTimes = listOf(LocalTime(8, 0)),
            dayIntervalText = "1",
            doseAmount = "",
            error = null,
        )
}

public data class ScheduleCreateUiState(
    public val medicationId: String? = null,
    public val medication: Medication? = null,
    public val startDate: LocalDate?,
    public val endDate: LocalDate? = null,
    public val phases: List<PhaseDraft>,
    public val startDateError: String? = null,
    /**
     * Form-level error surfaced by the calculator pre-flight in [ScheduleCreateViewModel.save].
     * Cleared by any field mutation so the banner disappears as the user begins addressing the
     * underlying configuration. Independent of [PhaseDraft.error] (which is per-phase
     * field-validation feedback).
     */
    public val formError: String? = null,
)

/**
 * Per-phase form draft.
 *
 * ### Night-dose warning fields (B9, per ADR-0004 D2 + v0.1-followups #1)
 *
 * `nightDoseWarning` is a derived flag — `updatePhase` recomputes it after every edit
 * by scanning [doseTimes] for any entry in `[00:00, 06:00)`. Callers do NOT set it
 * directly; passing a value through `copy()` will be overwritten by the next
 * `updatePhase` call (this is intentional — `copy` callers shouldn't try to lie about
 * whether the warning applies).
 *
 * `nightDoseAffirmed` is set ONLY by [ScheduleCreateViewModel.affirmNightDose]. Any
 * subsequent edit via `updatePhase` resets it to `false` so the user re-sees the
 * warning if they add another night dose. Spec is silent on persistence; we default
 * to the safer "reset on edit" policy. See `ScheduleCreateNightDoseTest` test #5.
 *
 * Both flags default to `false`. The warning is non-blocking — it does not affect
 * [ScheduleCreateViewModel.save]'s validation path.
 */
public data class PhaseDraft(
    public val durationDaysText: String,
    public val doseTimes: List<LocalTime>,
    public val dayIntervalText: String,
    public val doseAmount: String,
    public val error: String?,
    public val nightDoseWarning: Boolean = false,
    public val nightDoseAffirmed: Boolean = false,
    /**
     * Derived flag, recomputed by [ScheduleCreateViewModel.updatePhase] on every edit,
     * based on whether [doseTimes] straddles midnight per
     * [MidnightStraddleDetection.crossesMidnight] (v0.1-followups #9). Independent of
     * [nightDoseWarning]: a phase can straddle midnight without containing an early-hours
     * dose (e.g. `[23:00, 01:00]` triggers both; `[23:00, 23:30]` triggers neither;
     * `[03:00]` triggers night-dose only; `[00:00, 23:00]` triggers straddle only).
     * Callers do NOT set this directly; passing a value through `copy()` will be
     * overwritten by the next `updatePhase` call.
     */
    public val crossesMidnight: Boolean = false,
)
