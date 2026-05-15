package app.toebeans.android.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Pet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

public class PetsViewModel(
    private val petRepository: PetRepository,
) : ViewModel() {
    public val uiState: StateFlow<PetsUiState> =
        petRepository
            .observeAll()
            .map { pets -> PetsUiState(pets = pets, loading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                initialValue = PetsUiState(pets = emptyList(), loading = true),
            )
}

public data class PetsUiState(
    public val pets: List<Pet>,
    public val loading: Boolean,
)
