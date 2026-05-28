package app.toebeans.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.android.util.StaleEventGuard
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.scheduler.ScheduleCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Home / Today screen state. Joins four flows so the screen can render:
 *
 *   1. The pet roster with a per-pet medication count ("Luna · 1 med").
 *   2. **Today's due-doses worklist** — the calculator-projected schedule for the
 *      current day, with each row tagged pending or given for inline mark-taken.
 *   3. Today's logged doses across every pet ("Luna · Methimazole · 2h ago"), the
 *      retrospective surface that shows completed care.
 *
 * Home is now **both** the active surface (worklist + mark-taken) and the
 * retrospective surface (logged today). Pet Detail's "Log dose now" remains the
 * fallback for ad-hoc / off-schedule doses; the Today worklist is the on-rails path.
 *
 * "Today" is anchored to local midnight at first subscription. The window does not
 * roll over during a session — that's intentional: a midnight rollover that hid a
 * dose the user just logged at 23:58 would be more confusing than helpful. The
 * window refreshes when the screen re-enters composition (which happens on tab
 * switch and process resume, the points where a date change actually matters).
 *
 * Why all the inputs flow through pure helpers ([joinToUiState], [computeDueToday])
 * on the companion: the calculator is pure, the matching is pure, and the projection
 * is pure. Segregating the I/O (the flows in [buildUiState]) from the compute (the
 * companion functions) keeps the compute directly unit-testable.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
public class HomeViewModel(
    petRepository: PetRepository,
    medicationRepository: MedicationRepository,
    scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
    private val scheduleCalculator: ScheduleCalculator,
) : ViewModel() {
    private val filterPetId = MutableStateFlow<String?>(null)

    public val uiState: StateFlow<HomeUiState> =
        buildUiState(petRepository, medicationRepository, scheduleRepository, doseEventRepository, filterPetId)

    /** Filter due + logged lists to [petId]. Pet chips on Today use this instead of Pet Detail nav. */
    public fun selectPetFilter(petId: String) {
        filterPetId.update { petId }
    }

    /** Clears the in-page pet filter (Today header tap when a filter is active). */
    public fun clearPetFilter() {
        filterPetId.update { null }
    }

    /**
     * Mark the dose at [scheduledAt] on [scheduleId] as GIVEN, now. Fire-and-forget
     * from the UI's perspective — the repository flow will re-emit and the worklist
     * row flips from pending to given. Idempotent on `(scheduleId, scheduledAt)` per
     * the [DoseEventRepository.recordGivenForSlot] contract, so a double-tap is safe.
     *
     * Pet-Detail's "Log dose now" goes through [DoseEventRepository.recordGivenNow]
     * (no slot context). A user who logs there won't see the corresponding worklist
     * row flip to given (different `scheduledAt`). That's an accepted v0.1 wart
     * documented in the contract KDoc; both surfaces still produce a row in the
     * "Logged today" card so the user sees their action either way.
     */
    public fun markGiven(
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
    ) {
        viewModelScope.launch {
            doseEventRepository.recordGivenForSlot(
                // KMP-native UUID; matches Uuid.random() used elsewhere in the app
                // (ScheduleCreateViewModel, MedicationEditViewModel, PetEditViewModel).
                doseEventId = Uuid.random().toString(),
                scheduleId = scheduleId,
                medicationId = medicationId,
                scheduledAt = scheduledAt,
                resolvedAt = Clock.System.now(),
            )
        }
    }

    private fun buildUiState(
        petRepository: PetRepository,
        medicationRepository: MedicationRepository,
        scheduleRepository: ScheduleRepository,
        doseEventRepository: DoseEventRepository,
        filterPetId: MutableStateFlow<String?>,
    ): StateFlow<HomeUiState> {
        val pets = petRepository.observeAll()
        val medications = medicationRepository.observeAll()
        // The today-anchored flows are flatMapLatest-rebuilt off the pets flow. This
        // re-evaluates `localMidnightToday()` and `localToday()` whenever the pets
        // flow re-emits, so a midnight rollover (which would re-emit on the next pet
        // upsert, or on screen re-entry via subscription restart) picks up the new
        // window. Within a single session the window stays fixed.
        val recentDoses =
            pets.flatMapLatest { _ ->
                doseEventRepository.observeAllRecent(localMidnightToday())
            }
        val schedulesWithPhases =
            pets.flatMapLatest { _ ->
                scheduleRepository.observeActiveWithPhases(localToday())
            }
        return combine(
            pets,
            medications,
            recentDoses,
            schedulesWithPhases,
            filterPetId,
        ) { petList, medList, doses, swp, activePetFilter ->
            val zone = TimeZone.currentSystemDefault()
            val dueToday =
                computeDueToday(
                    schedulesWithPhases = swp,
                    medications = medList,
                    pets = petList,
                    recentDoses = doses,
                    calculator = scheduleCalculator,
                    timeZone = zone,
                    todayStart = localMidnightToday(),
                    todayEnd = localMidnightTomorrow(),
                )
            val base = joinToUiState(petList, medList, doses).copy(dueDoses = dueToday)
            applyPetFilter(base, activePetFilter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue =
                HomeUiState(
                    pets = emptyList(),
                    medCountByPetId = emptyMap(),
                    dueDoses = emptyList(),
                    recentDoses = emptyList(),
                    loading = true,
                ),
        )
    }

    public companion object {
        /**
         * Anchor "today" at local midnight. The clock and zone are read at call time
         * so a process resume after a date rollover picks up the new window.
         */
        private fun localMidnightToday(): Instant {
            val zone = TimeZone.currentSystemDefault()
            val today: LocalDate = Clock.System.todayIn(zone)
            return LocalDateTime(today, LocalTime(0, 0)).toInstant(zone)
        }

        /** Local midnight tomorrow — the exclusive upper bound of today's window. */
        private fun localMidnightTomorrow(): Instant {
            val zone = TimeZone.currentSystemDefault()
            val tomorrow: LocalDate = Clock.System.todayIn(zone).plus(1, DateTimeUnit.DAY)
            return LocalDateTime(tomorrow, LocalTime(0, 0)).toInstant(zone)
        }

        /** Local-zone "today" calendar date. */
        private fun localToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

        /**
         * Pure join of pets, medications, and recent dose events into the UI state.
         * Lives on the companion (not inside the VM) so it's directly unit-testable
         * without a viewModelScope.
         *
         * The Logged-Today row resolves through `event.medicationId` directly, then
         * through the medication's `petId`. Prior to the dose-event schema change
         * this code computed `event.scheduleId.replaceFirst("sched-", "med-")` to
         * find the medication — that only worked for the seeded demo IDs and silently
         * dropped every user-created medication's dose from the retrospective view.
         * The medicationId is now denormalized onto the event row; see [DoseEvent] KDoc.
         */
        internal fun joinToUiState(
            petList: List<Pet>,
            medList: List<Medication>,
            doses: List<DoseEvent>,
        ): HomeUiState {
            val medCountByPet =
                medList
                    .filter { it.discontinuedAt == null }
                    .groupingBy { it.petId }
                    .eachCount()
            val petById = petList.associateBy { it.id }
            val medById = medList.associateBy { it.id }
            val recentDoseUis =
                doses.mapNotNull { event ->
                    val med =
                        medById[event.medicationId]
                            ?: return@mapNotNull StaleEventGuard.reportMissing(
                                site = "HomeViewModel.joinToUiState",
                                eventId = event.id,
                                missingFieldName = "medicationId",
                                missingValue = event.medicationId,
                            )
                    val pet =
                        petById[med.petId]
                            ?: return@mapNotNull StaleEventGuard.reportMissing(
                                site = "HomeViewModel.joinToUiState",
                                eventId = event.id,
                                missingFieldName = "petId",
                                missingValue = med.petId,
                            )
                    val speciesLabel =
                        pet.species.name
                            .lowercase()
                            .replaceFirstChar(Char::titlecase)
                    RecentDoseUi(
                        id = event.id,
                        petId = pet.id,
                        petName = pet.name,
                        petSpecies = speciesLabel,
                        medicationName = med.name,
                        givenAt = event.resolvedAt ?: event.scheduledAt,
                    )
                }
            return HomeUiState(
                pets = petList,
                medCountByPetId = medCountByPet,
                recentDoses = recentDoseUis,
                loading = false,
            )
        }

        /**
         * Pure computation of today's due-dose worklist. For each active schedule, runs
         * [calculator] over the half-open window `[todayStart, todayEnd)`, then matches
         * each scheduled-dose against the recorded GIVEN dose events. Pending rows
         * have `givenEventId == null`; given rows carry the event id and resolvedAt.
         *
         * Result is globally ascending by `scheduledAt`. Schedules whose medication or
         * pet cannot be resolved are reported via [StaleEventGuard]: debug builds throw
         * to surface join bugs in CI; release builds log + skip so transient inter-Flow
         * races during deletion don't crash a tester mid-medication-log.
         *
         * Match key: `(scheduleId, scheduledAt)`. The worklist flips a row to "given"
         * when a GIVEN event exists for that exact slot.
         * [DoseEventRepository.recordGivenForSlot] is the write path that produces
         * these matches; [DoseEventRepository.recordGivenNow] (Pet Detail's ad-hoc
         * log) deliberately does NOT match here (different `scheduledAt` semantics).
         *
         * Detekt's LongParameterList fires here because pure projection helpers
         * naturally carry their inputs explicitly. Wrapping seven domain inputs in
         * a context object would obscure the data flow rather than clarify it.
         */
        @Suppress("LongParameterList")
        internal fun computeDueToday(
            schedulesWithPhases: List<ScheduleWithPhases>,
            medications: List<Medication>,
            pets: List<Pet>,
            recentDoses: List<DoseEvent>,
            calculator: ScheduleCalculator,
            timeZone: TimeZone,
            todayStart: Instant,
            todayEnd: Instant,
        ): List<DueDoseUi> {
            val petById = pets.associateBy { it.id }
            val medById = medications.associateBy { it.id }
            val givenBySlot: Map<Pair<String, Instant>, DoseEvent> =
                recentDoses
                    .filter { it.status == DoseStatus.GIVEN }
                    .associateBy { it.scheduleId to it.scheduledAt }

            // Pre-filter to schedules with a still-existing med + pet. Two-stage
            // mapNotNull avoids the dual-`continue` pattern and reads declaratively:
            // "for each viable bundle, project its slots."
            data class ViableBundle(
                val swp: ScheduleWithPhases,
                val med: Medication,
                val pet: Pet,
            )
            val viable =
                schedulesWithPhases.mapNotNull { swp ->
                    val med =
                        medById[swp.schedule.medicationId]
                            ?: return@mapNotNull StaleEventGuard.reportMissing(
                                site = "HomeViewModel.computeDueToday",
                                eventId = swp.schedule.id,
                                missingFieldName = "medicationId",
                                missingValue = swp.schedule.medicationId,
                            )
                    val pet =
                        petById[med.petId]
                            ?: return@mapNotNull StaleEventGuard.reportMissing(
                                site = "HomeViewModel.computeDueToday",
                                eventId = swp.schedule.id,
                                missingFieldName = "petId",
                                missingValue = med.petId,
                            )
                    ViableBundle(swp, med, pet)
                }
            val rows =
                viable.flatMap { (swp, med, pet) ->
                    calculator
                        .computeScheduledDoses(
                            schedule = swp.schedule,
                            phases = swp.phases,
                            timeZone = timeZone,
                            fromInclusive = todayStart,
                            toExclusive = todayEnd,
                        ).map { dose ->
                            val given = givenBySlot[swp.schedule.id to dose.scheduledAt]
                            DueDoseUi(
                                scheduleId = swp.schedule.id,
                                medicationId = med.id,
                                petId = pet.id,
                                scheduledAt = dose.scheduledAt,
                                petName = pet.name,
                                medicationName = med.name,
                                doseAmount = dose.doseAmount ?: med.doseAmount,
                                givenEventId = given?.id,
                                resolvedAt = given?.resolvedAt,
                            )
                        }
                }
            // Calculator outputs are per-schedule-ascending; merging across schedules
            // requires a final global sort. Stable sort preserves intra-schedule order
            // when two schedules happen to share a scheduledAt instant.
            return rows.sortedBy { it.scheduledAt }
        }

        /**
         * When [petId] is non-null, narrows due + logged lists to that pet. Lives on the
         * companion for direct unit tests without a ViewModel scope.
         */
        internal fun applyPetFilter(
            state: HomeUiState,
            petId: String?,
        ): HomeUiState {
            if (petId == null) {
                return state.copy(filterPetId = null)
            }
            // Stale filter after pet archive/delete: show full lists, not an empty screen.
            if (state.pets.none { it.id == petId }) {
                return state.copy(filterPetId = null)
            }
            return state.copy(
                filterPetId = petId,
                dueDoses = state.dueDoses.filter { it.petId == petId },
                recentDoses = state.recentDoses.filter { it.petId == petId },
            )
        }
    }
}

/** Compact UI projection of a logged dose, ready for the Home "Logged today" card. */
public data class RecentDoseUi(
    public val id: String,
    public val petId: String,
    public val petName: String,
    public val petSpecies: String,
    public val medicationName: String,
    public val givenAt: Instant,
)

/**
 * A single row in the Today-screen due-doses worklist. Pending when [givenEventId] is
 * null; given when it carries the matching DoseEvent id and resolvedAt.
 *
 * [scheduleId] + [scheduledAt] together form the slot identity used by
 * [HomeViewModel.markGiven] when the user taps the inline Log button. The UI never
 * needs to look up either independently.
 */
public data class DueDoseUi(
    public val scheduleId: String,
    public val medicationId: String,
    public val petId: String,
    public val scheduledAt: Instant,
    public val petName: String,
    public val medicationName: String,
    public val doseAmount: String,
    public val givenEventId: String?,
    public val resolvedAt: Instant?,
) {
    public val isGiven: Boolean get() = givenEventId != null
}

/** UI state for Home. Immutable snapshot. */
public data class HomeUiState(
    public val pets: List<Pet>,
    public val medCountByPetId: Map<String, Int> = emptyMap(),
    public val dueDoses: List<DueDoseUi> = emptyList(),
    public val recentDoses: List<RecentDoseUi> = emptyList(),
    public val filterPetId: String? = null,
    public val loading: Boolean = false,
)
