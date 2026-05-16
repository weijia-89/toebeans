package app.toebeans.android.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Pet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

public class PetsViewModel(
    petRepository: PetRepository,
    medicationRepository: MedicationRepository,
) : ViewModel() {
    public val uiState: StateFlow<PetsUiState> =
        combine(
            petRepository.observeAll(),
            medicationRepository.observeAll(),
        ) { pets, meds ->
            val countByPet =
                meds
                    .filter { it.discontinuedAt == null }
                    .groupingBy { it.petId }
                    .eachCount()
            PetsUiState(pets = pets, medCountByPetId = countByPet, loading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = PetsUiState(pets = emptyList(), medCountByPetId = emptyMap(), loading = true),
        )
}

public data class PetsUiState(
    public val pets: List<Pet>,
    public val medCountByPetId: Map<String, Int> = emptyMap(),
    public val loading: Boolean,
)
