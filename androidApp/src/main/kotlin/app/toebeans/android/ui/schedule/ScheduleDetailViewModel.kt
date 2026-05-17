package app.toebeans.android.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Schedule Detail screen (B7).
 *
 * Reached from the Reminder List (B6) when the user taps a schedule row. Renders:
 *   - The pet name + medication name (resolved through the relationship chain).
 *   - The schedule's start + end dates.
 *   - The ordered list of phases, with each phase's cadence + duration + dose times.
 *   - A delete affordance (top-bar action with confirmation dialog).
 *
 * ## Load model
 *
 * Mirrors [app.toebeans.android.ui.pets.PetEditViewModel.load] — the screen passes the
 * `scheduleId` from the nav-arg via a `LaunchedEffect` and the VM kicks off the
 * observation. The state flow then re-emits whenever any of the upstream rows change,
 * so a concurrent edit (e.g. an upsert from another screen) would refresh the detail
 * without manual reload.
 *
 * ## Stale-row handling
 *
 * Unlike Home/Reminders, this screen is the *terminal* view for a single schedule. If
 * the schedule has been deleted (e.g. via this screen's own Delete button, or
 * concurrently from another path), [state.value.schedule] becomes null and the screen
 * navigates back — `onScheduleDeleted` in the composable wires this. We deliberately
 * do NOT throw via `StaleEventGuard` here because the deletion is the *expected*
 * terminal state, not a bug.
 *
 * ## Delete contract
 *
 * `delete()` is a suspend function returning Boolean — `true` if the deletion took
 * effect, `false` if there was nothing to delete (e.g. the schedule was already gone).
 * The caller signals back-navigation on `true`. Matches
 * [app.toebeans.android.ui.pets.PetEditViewModel.delete] for code-review familiarity.
 *
 * ## Why not StateFlow.stateIn
 *
 * The lookup happens AFTER `load(scheduleId)` is called, not at construction time —
 * we don't know the schedule id until the route arg arrives. A mutable
 * `_scheduleId` StateFlow drives a `flatMapLatest` over the three repos so callers
 * pay no cost before `load`.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
public class ScheduleDetailViewModel(
    private val petRepository: PetRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val scheduleIdFlow = MutableStateFlow<String?>(null)
    private val _state = MutableStateFlow(ScheduleDetailUiState())
    public val state: StateFlow<ScheduleDetailUiState> = _state.asStateFlow()

    init {
        // Drive UI state from the scheduleId. flatMapLatest cancels previous
        // subscriptions when load() is called a second time (defensive: the screen
        // calls load once via LaunchedEffect keyed on scheduleId, so this only
        // matters if the user navigates between two different detail screens).
        viewModelScope.launch {
            scheduleIdFlow
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(ScheduleDetailUiState(loading = false))
                    } else {
                        combine(
                            scheduleRepository.observeById(id),
                            scheduleRepository.observePhases(id),
                            medicationRepository.observeAll(),
                            petRepository.observeAll(),
                        ) { schedule, phases, meds, pets ->
                            if (schedule == null) {
                                // The schedule has been deleted (terminal state). The
                                // composable will observe `schedule == null && !loading`
                                // and pop back. We don't throw — deletion is expected
                                // here, not a bug.
                                ScheduleDetailUiState(
                                    scheduleId = id,
                                    schedule = null,
                                    loading = false,
                                )
                            } else {
                                val med = meds.firstOrNull { it.id == schedule.medicationId }
                                val pet = med?.let { m -> pets.firstOrNull { it.id == m.petId } }
                                ScheduleDetailUiState(
                                    scheduleId = id,
                                    schedule = schedule,
                                    phases = phases,
                                    medicationName = med?.name ?: "(unknown medication)",
                                    petName = pet?.name ?: "(unknown pet)",
                                    loading = false,
                                )
                            }
                        }
                    }
                }
                .collect { snapshot -> _state.value = snapshot }
        }
    }

    /**
     * Begin observing the schedule with the given id. Safe to call repeatedly — the
     * StateFlow dedups identical inputs, so re-invocations on the same id are no-ops.
     *
     * Implementation note on ordering: we flip `loading = true` BEFORE updating
     * [scheduleIdFlow], because on an unconfined coroutine dispatcher (test rigs) the
     * collector reacts synchronously to the id change. If we set loading=true after,
     * the collector's emission (which sets loading=false) would be overwritten by
     * this stale "true". Sequence: set loading → swap id → collector overwrites.
     */
    public fun load(scheduleId: String) {
        _state.update { it.copy(loading = true) }
        scheduleIdFlow.value = scheduleId
    }

    /**
     * Delete the currently-loaded schedule. Returns `true` if the deletion was
     * performed, `false` if there's nothing to delete (no schedule loaded, or the row
     * has already been removed). Caller pops back-stack on `true`.
     */
    public suspend fun delete(): Boolean {
        val id = scheduleIdFlow.value ?: return false
        val current = _state.value.schedule ?: return false
        if (current.id != id) return false
        scheduleRepository.delete(id)
        return true
    }
}

/**
 * Immutable UI state for the Schedule Detail screen.
 *
 * `schedule == null && !loading` means the schedule has been deleted — the screen
 * should navigate back. Other components ([phases], [medicationName], [petName]) are
 * defensive defaults for the transient pre-load frame.
 */
public data class ScheduleDetailUiState(
    public val scheduleId: String? = null,
    public val schedule: Schedule? = null,
    public val phases: List<SchedulePhase> = emptyList(),
    public val medicationName: String = "",
    public val petName: String = "",
    public val loading: Boolean = true,
)
