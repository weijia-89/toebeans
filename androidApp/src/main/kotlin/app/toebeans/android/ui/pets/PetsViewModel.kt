package app.toebeans.android.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.android.preferences.UnitPreferences
import app.toebeans.android.preferences.WeightUnit
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
    unitPreferences: UnitPreferences,
) : ViewModel() {
    public val uiState: StateFlow<PetsUiState> =
        combine(
            petRepository.observeAll(),
            medicationRepository.observeAll(),
            unitPreferences.weightUnit,
        ) { pets, meds, unit ->
            val countByPet =
                meds
                    .filter { it.discontinuedAt == null }
                    .groupingBy { it.petId }
                    .eachCount()
            PetsUiState(
                pets = pets,
                medCountByPetId = countByPet,
                loading = false,
                weightUnit = unit,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5_000L,
                    replayExpirationMillis = 0L,
                ),
            initialValue =
                PetsUiState(
                    pets = emptyList(),
                    medCountByPetId = emptyMap(),
                    loading = true,
                    weightUnit = unitPreferences.weightUnit.value,
                ),
        )
}

public data class PetsUiState(
    public val pets: List<Pet>,
    public val medCountByPetId: Map<String, Int> = emptyMap(),
    public val loading: Boolean,
    public val weightUnit: WeightUnit = WeightUnit.POUNDS,
)
