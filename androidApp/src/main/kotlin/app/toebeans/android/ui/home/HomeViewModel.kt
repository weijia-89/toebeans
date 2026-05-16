package app.toebeans.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Pet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn

/**
 * Home / Today screen state. Joins three repositories so the screen can render:
 *
 *   1. The pet roster with a per-pet medication count ("Luna · 1 med").
 *   2. Today's logged doses across every pet ("Luna · Methimazole · 2h ago").
 *
 * The forward-looking projection of upcoming doses (the materialized worklist) waits
 * for the schedule calculator to land in core/scheduler. Until then, Home is the
 * **retrospective** surface — what care has been completed today — and Pet Detail is
 * the active surface where doses get logged. Once the materializer lands, this VM
 * gains a third flow for pending doses and Home becomes both.
 *
 * "Today" is anchored to local midnight at observation time. The window does not
 * roll over during a session — that's intentional: a midnight rollover that hid a
 * dose the user just logged at 23:58 would be more confusing than helpful. The
 * window refreshes when the screen re-enters composition (which happens on tab
 * switch and process resume, the points where a date change actually matters).
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class HomeViewModel(
    petRepository: PetRepository,
    medicationRepository: MedicationRepository,
    doseEventRepository: DoseEventRepository,
) : ViewModel() {
    public val uiState: StateFlow<HomeUiState> =
        buildUiState(petRepository, medicationRepository, doseEventRepository)

    private fun buildUiState(
        petRepository: PetRepository,
        medicationRepository: MedicationRepository,
        doseEventRepository: DoseEventRepository,
    ): StateFlow<HomeUiState> {
        val pets = petRepository.observeAll()
        val medications = medicationRepository.observeAll()
        // The "Today" window is captured per subscription. flatMapLatest re-derives
        // the dose-events flow whenever the pets flow re-emits with a new midnight
        // boundary (effectively never within a session); the indirection lets the
        // dose-events flow be re-subscribed cleanly on process resume without leaking
        // the prior anchor.
        val recentDoses =
            pets.flatMapLatest { _ ->
                doseEventRepository.observeAllRecent(localMidnightToday())
            }
        return combine(pets, medications, recentDoses) { petList, medList, doses ->
            joinToUiState(petList, medList, doses)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue =
                HomeUiState(
                    pets = emptyList(),
                    medCountByPetId = emptyMap(),
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

        /**
         * Pure join of pets, medications, and recent dose events into the UI state.
         * Lives on the companion (not inside the VM) so it's directly unit-testable
         * without a viewModelScope.
         *
         * Schedule → medication lookup: for v0.1 the dose-event row carries scheduleId,
         * not medicationId. To avoid a fourth flow, the seed schedule ID format encodes
         * the medication ID ("sched-luna-methimazole" → "med-luna-methimazole"). The
         * real impl will join through schedules in SQLDelight once the materializer
         * lands; we skip the row if the lookup misses.
         */
        internal fun joinToUiState(
            petList: List<Pet>,
            medList: List<app.toebeans.core.model.Medication>,
            doses: List<app.toebeans.core.model.DoseEvent>,
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
                    val medId = event.scheduleId.replaceFirst("sched-", "med-")
                    val med = medById[medId] ?: return@mapNotNull null
                    val pet = petById[med.petId] ?: return@mapNotNull null
                    val speciesLabel =
                        pet.species.name
                            .lowercase()
                            .replaceFirstChar(Char::titlecase)
                    RecentDoseUi(
                        id = event.id,
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
    }
}

/** Compact UI projection of a logged dose, ready for the Home "Logged today" card. */
public data class RecentDoseUi(
    public val id: String,
    public val petName: String,
    public val petSpecies: String,
    public val medicationName: String,
    public val givenAt: Instant,
)

/** UI state for Home. Immutable snapshot. */
public data class HomeUiState(
    public val pets: List<Pet>,
    public val medCountByPetId: Map<String, Int> = emptyMap(),
    public val recentDoses: List<RecentDoseUi> = emptyList(),
    public val loading: Boolean = false,
)
