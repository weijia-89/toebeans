package app.toebeans.android.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random

/**
 * ViewModel for the Pet detail screen. Observes the pet, its medications, and the
 * dose-log status for each medication.
 *
 * Each medication carries a [MedicationWithStatus]:
 *   - `medication`           — the persisted Medication row
 *   - `activeScheduleId`     — most-recently-created Schedule for this medication
 *                              (or null if no schedules exist yet)
 *   - `lastDose`             — most recent GIVEN DoseEvent (or null)
 *
 * Why pre-compute on the VM rather than per-row in Compose: the dose-log query joins
 * across DoseEvent + Schedule + Medication, which would mean N+1 flows in the UI if
 * computed per row. Doing it once in the VM yields a clean Compose tree and lets us
 * test the join logic without spinning up Compose.
 */
public class PetDetailViewModel(
    private val petRepository: PetRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
) : ViewModel() {
    private val petIdFlow = MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    public val state: StateFlow<PetDetailUiState> =
        petIdFlow
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(PetDetailUiState(loading = false))
                } else {
                    combine(
                        petRepository.observeById(id),
                        medicationRepository.observeForPet(id),
                    ) { pet, meds -> pet to meds }.flatMapLatest { (pet, meds) ->
                        if (meds.isEmpty()) {
                            // No medications -> no dose-log joins needed. Short-circuit so
                            // we don't spin up an empty combine() with zero source flows.
                            flowOf(PetDetailUiState(pet = pet, medications = emptyList()))
                        } else {
                            // For each medication, combine its latest schedule and last-given
                            // dose into a single MedicationWithStatus. This is N joins for N
                            // meds, which is fine at owner scale (typical: 1-3 meds per pet,
                            // max realistic: ~6).
                            val perMedFlows =
                                meds.map { med ->
                                    combine(
                                        scheduleRepository.observeForMedication(med.id),
                                        doseEventRepository.observeLastGivenForMedication(med.id),
                                    ) { schedules, lastDose ->
                                        MedicationWithStatus(
                                            medication = med,
                                            // observeForMedication is already sorted by
                                            // createdAt DESC; first() is the most recent.
                                            activeScheduleId = schedules.firstOrNull()?.id,
                                            lastDose = lastDose,
                                        )
                                    }
                                }
                            combine(perMedFlows) { array ->
                                PetDetailUiState(pet = pet, medications = array.toList())
                            }
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                initialValue = PetDetailUiState(loading = true),
            )

    public fun load(petId: String) {
        petIdFlow.value = petId
    }

    /**
     * Quick-log a dose for [medicationId] at the current instant.
     *
     * Caller is responsible for ensuring the medication has at least one schedule
     * (the UI gates the Log Dose button on that condition). If [activeScheduleId] is
     * null, this is a no-op rather than throwing — the schedule may have been deleted
     * between the UI rendering and the user tapping the button.
     */
    public fun logDose(
        medicationId: String,
        activeScheduleId: String?,
    ) {
        if (activeScheduleId == null) return
        viewModelScope.launch {
            doseEventRepository.recordGivenNow(
                doseEventId = randomDoseEventId(),
                scheduleId = activeScheduleId,
                at = Clock.System.now(),
            )
        }
    }

    /**
     * UUID-ish dose event id. Using Random.nextLong() over a real UUID lib because
     * pulling in a UUID dep just for this is overkill — collisions across a single
     * user's lifetime are astronomically improbable at 64 bits.
     */
    private fun randomDoseEventId(): String = "dose-${Random.nextLong().toULong().toString(16)}"
}

public data class PetDetailUiState(
    public val pet: Pet? = null,
    public val medications: List<MedicationWithStatus> = emptyList(),
    public val loading: Boolean = false,
)

/**
 * A medication enriched with its dose-log status for display on the Pet detail screen.
 */
public data class MedicationWithStatus(
    public val medication: Medication,
    public val activeScheduleId: String?,
    public val lastDose: DoseEvent?,
)

// `Schedule` and `Instant` are referenced in kdoc only; importing keeps them visible
// from the public API surface for callers that want to construct test states.
@Suppress("unused")
private typealias _ScheduleRef = Schedule

@Suppress("unused")
private typealias _InstantRef = Instant
