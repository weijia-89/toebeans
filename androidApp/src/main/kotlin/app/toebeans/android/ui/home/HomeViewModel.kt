package app.toebeans.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Pet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Today-view state. The real Today view (milestone 1.5+) will compute today's doses from
 * the scheduler + ScheduleRepository + DoseEventRepository. For the scaffold we just show
 * a pet count so the wiring is end-to-end testable.
 */
public class HomeViewModel(
    petRepository: PetRepository,
) : ViewModel() {
    public val uiState: StateFlow<HomeUiState> =
        petRepository
            .observeAll()
            .map { pets -> HomeUiState(pets = pets) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                initialValue = HomeUiState(pets = emptyList(), loading = true),
            )
}

/** UI state for Home. Immutable snapshot. */
public data class HomeUiState(
    public val pets: List<Pet>,
    public val loading: Boolean = false,
)
