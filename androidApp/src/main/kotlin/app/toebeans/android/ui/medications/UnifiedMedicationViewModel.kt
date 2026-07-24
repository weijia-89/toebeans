package app.toebeans.android.ui.medications

import androidx.lifecycle.ViewModel
import app.toebeans.android.ui.schedule.MidnightStraddleDetection
import app.toebeans.android.ui.schedule.PhaseDraft
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationNameIndexRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.MalformedScheduleException
import app.toebeans.core.scheduler.ReminderRescheduler
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

public data class UnifiedMedicationUiState(
    public val petId: String? = null,
    public val name: String = "",
    public val doseAmount: String = "",
    public val doseUnit: DoseUnit? = null,
    public val notes: String = "",
    public val nameError: String? = null,
    public val doseAmountError: String? = null,
    public val doseUnitError: String? = null,
    public val startDate: LocalDate? =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date,
    public val endDate: LocalDate? = null,
    public val anchorMode: AnchorMode = AnchorMode.FOLLOW_PHONE,
    public val startDateError: String? = null,
    public val endDateError: String? = null,
    public val phases: List<PhaseDraft> = listOf(blankPhaseDraft()),
    public val isSaving: Boolean = false,
    public val formError: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
@Suppress("LongParameterList")
public class UnifiedMedicationViewModel(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
    private val scheduleCalculator: ScheduleCalculator,
    private val notificationActuator: NotificationActuator,
    public val medicationNameIndex: MedicationNameIndexRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    public companion object {
        /**
         * Maximum number of phases in a single schedule. Ten is generous for any realistic
         * pet-medication taper (most have 1-3 phases) and keeps recomposition and validation
         * bounded.
         */
        public const val MAX_PHASES: Int = 10
    }

    private val _state = MutableStateFlow(UnifiedMedicationUiState())
    public val state: StateFlow<UnifiedMedicationUiState> = _state.asStateFlow()

    public fun setPetId(petId: String) {
        _state.update { it.copy(petId = petId) }
    }

    public fun onNameChange(value: String) {
        _state.update { it.copy(name = value, nameError = null, formError = null) }
    }

    public fun onDoseAmountChange(value: String) {
        _state.update { it.copy(doseAmount = value, doseAmountError = null, doseUnitError = null, formError = null) }
    }

    public fun onDoseUnitChange(value: DoseUnit?) {
        _state.update { it.copy(doseUnit = value, doseUnitError = null, doseAmountError = null, formError = null) }
    }

    public fun onNotesChange(value: String) {
        _state.update { it.copy(notes = value, formError = null) }
    }

    public fun onStartDateChange(value: LocalDate?) {
        _state.update { it.copy(startDate = value, startDateError = null, endDateError = null, formError = null) }
    }

    public fun onEndDateChange(value: LocalDate?) {
        _state.update { it.copy(endDate = value, endDateError = null, formError = null) }
    }

    public fun onAnchorModeChange(anchorMode: AnchorMode) {
        _state.update { it.copy(anchorMode = anchorMode, formError = null) }
    }

    public fun addPhase() {
        _state.update { it.copy(phases = it.phases + blankPhaseDraft(), formError = null) }
    }

    public fun removePhase(index: Int) {
        _state.update { state ->
            if (state.phases.size <= 1) {
                state
            } else {
                state.copy(phases = state.phases.filterIndexed { i, _ -> i != index }, formError = null)
            }
        }
    }

    public fun updatePhase(
        index: Int,
        transform: (PhaseDraft) -> PhaseDraft,
    ) {
        _state.update { state ->
            state.copy(
                phases =
                    state.phases.mapIndexed { i, phase ->
                        if (i == index) {
                            val transformed = transform(phase).copy(error = null)
                            val doseTimesChanged = transformed.doseTimes != phase.doseTimes
                            val affirmed = if (doseTimesChanged) false else phase.nightDoseAffirmed
                            val hasNightDose = transformed.doseTimes.any { it.isInNightWindow() }
                            val straddles = MidnightStraddleDetection.crossesMidnight(transformed.doseTimes)
                            val straddleDismissed = if (doseTimesChanged) false else phase.midnightStraddleDismissed
                            transformed.copy(
                                nightDoseWarning = hasNightDose && !affirmed,
                                nightDoseAffirmed = affirmed,
                                crossesMidnight = straddles,
                                midnightStraddleDismissed = straddleDismissed,
                            )
                        } else {
                            phase
                        }
                    },
                formError = null,
            )
        }
    }

    public fun affirmNightDose(index: Int) {
        _state.update { state ->
            if (index !in state.phases.indices) {
                state
            } else {
                state.copy(
                    phases =
                        state.phases.mapIndexed { i, phase ->
                            if (i == index) {
                                phase.copy(nightDoseAffirmed = true, nightDoseWarning = false)
                            } else {
                                phase
                            }
                        },
                )
            }
        }
    }

    public fun dismissMidnightStraddle(index: Int) {
        _state.update { state ->
            if (index !in state.phases.indices) {
                state
            } else {
                state.copy(
                    phases =
                        state.phases.mapIndexed { i, phase ->
                            if (i == index) {
                                phase.copy(midnightStraddleDismissed = true)
                            } else {
                                phase
                            }
                        },
                )
            }
        }
    }

    private fun LocalTime.isInNightWindow(): Boolean = this.hour < 6

    public suspend fun save(): String? {
        val petId = _state.value.petId ?: return null

        // Atomic check-and-set for isSaving to prevent double-submit races.
        while (true) {
            val current = _state.value
            if (current.isSaving) return null
            if (_state.compareAndSet(current, current.copy(isSaving = true))) {
                break
            }
        }

        try {
            val s = _state.value
            if (!validateMedicationFields(s)) return null
            if (!validatePhases(s.phases)) return null

            val medication = buildMedication(s, petId)
            val (schedule, phases) = newSchedulePayload(s, medication.id)

            val preflightError = runPreflight(schedule, phases)
            if (preflightError != null) {
                _state.update { it.copy(formError = preflightError) }
                return null
            }

            // TODO(transaction): medication + schedule upserts should be atomic.
            // Current architecture uses separate repository interfaces without a
            // cross-repository transaction boundary. A future refactor should
            // introduce a MedScheduleRepository facade or SQLDelight Transacter
            // to guarantee atomicity and prevent orphaned medications on failure.
            medicationRepository.upsert(medication)
            scheduleRepository.upsert(schedule, phases)
            val reminders =
                ReminderRescheduler.materializeHorizonForSchedule(
                    schedule = schedule,
                    phases = phases,
                    medicationId = medication.id,
                    doseEventRepository = doseEventRepository,
                    scheduleCalculator = scheduleCalculator,
                    timeZone = timeZone,
                    now = Clock.System.now(),
                )
            for (reminder in reminders) {
                notificationActuator.schedule(reminder)
            }
            return schedule.id
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun validateMedicationFields(s: UnifiedMedicationUiState): Boolean {
        var valid = true
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Required") }
            valid = false
        }
        if (s.doseAmount.isBlank()) {
            _state.update { it.copy(doseAmountError = "Required") }
            valid = false
        }
        if (s.doseUnit == null) {
            _state.update { it.copy(doseUnitError = "Required") }
            valid = false
        }
        if (s.startDate == null) {
            _state.update { it.copy(startDateError = "Required") }
            valid = false
        }
        if (s.endDate != null && s.startDate != null && s.endDate < s.startDate) {
            _state.update { it.copy(endDateError = "End date must be on or after start date") }
            valid = false
        }
        return valid
    }

    private fun validatePhases(phases: List<PhaseDraft>): Boolean {
        if (phases.size > MAX_PHASES) {
            _state.update { it.copy(formError = "Maximum $MAX_PHASES phases allowed") }
            return false
        }
        val phasesWithErrors = phases.mapIndexed { idx, draft -> validatePhase(draft, idx) }
        if (phasesWithErrors.any { it.second != null }) {
            _state.update {
                it.copy(
                    phases =
                        phasesWithErrors.map { (draft, err) ->
                            if (err != null) draft.copy(error = err) else draft
                        },
                )
            }
            return false
        }
        return true
    }

    private fun buildMedication(
        s: UnifiedMedicationUiState,
        petId: String,
    ): Medication {
        val doseUnit = checkNotNull(s.doseUnit) { "doseUnit must not be null after validation" }
        return Medication(
            id = "med-${Uuid.random()}",
            petId = petId,
            name = s.name.trim(),
            doseAmount = s.doseAmount.trim(),
            doseUnit = doseUnit,
            notes = s.notes.trim().ifEmpty { null },
            createdAt = Clock.System.now(),
            discontinuedAt = null,
        )
    }

    private fun newSchedulePayload(
        s: UnifiedMedicationUiState,
        medId: String,
    ): Pair<Schedule, List<SchedulePhase>> {
        val startDate = checkNotNull(s.startDate) { "startDate must not be null after validation" }
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
                    doseUnit = draft.doseUnit,
                    dayInterval = draft.dayIntervalText.toIntOrNull() ?: 1,
                )
            }
        val schedule =
            Schedule(
                id = scheduleId,
                medicationId = medId,
                startDate = startDate,
                endDate = s.endDate,
                createdAt = Clock.System.now(),
                anchorMode = s.anchorMode,
            )
        return schedule to phases
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
            "This schedule would generate ${e.attemptedCount} doses in " +
                "${DefaultScheduleCalculator.MAX_WINDOW_DAYS} days — more than " +
                "the safe limit (${e.maxCount}). Reduce the number of phases, doses per day, " +
                "or stretch the skip-day interval."
        } catch (e: MalformedScheduleException.DuplicatePhaseOrder) {
            "Two phases share the same position (${e.phaseOrder}). Each phase needs its own slot."
        } catch (e: MalformedScheduleException.PhaseOrderGap) {
            "Phases are out of order (${e.phaseOrders}). Remove the gaps and try again."
        } catch (_: MalformedScheduleException.WindowNotPositive) {
            "Couldn't validate this schedule — the preview window was invalid. Please retry."
        } catch (_: MalformedScheduleException.WindowTooLarge) {
            "Couldn't validate this schedule — the preview window was too large. Please retry."
        } catch (e: MalformedScheduleException) {
            "This schedule isn't valid: ${e.message ?: e.code}"
        }
    }
}

private fun blankPhaseDraft(): PhaseDraft =
    PhaseDraft(
        durationDaysText = "7",
        doseTimes = listOf(LocalTime(8, 0)),
        dayIntervalText = "1",
        doseAmount = "",
        doseUnit = null,
        error = null,
    )
