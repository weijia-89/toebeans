package app.toebeans.android.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Pet detail screen. Observes the pet and its medications from the two
 * repositories. The detail screen displays the pet's identity card and a LazyColumn of
 * medications (each tappable to edit, with an Add FAB to create a new one).
 */
public class PetDetailViewModel(
    private val petRepository: PetRepository,
    private val medicationRepository: MedicationRepository,
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
                        flowOf(id).flatMapLatest { flowOf(petRepository.getById(it)) },
                        medicationRepository.observeForPet(id),
                    ) { pet, meds -> PetDetailUiState(pet = pet, medications = meds) }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                initialValue = PetDetailUiState(loading = true),
            )

    public fun load(petId: String) {
        petIdFlow.value = petId
    }
}

public data class PetDetailUiState(
    public val pet: Pet? = null,
    public val medications: List<Medication> = emptyList(),
    public val loading: Boolean = false,
)
