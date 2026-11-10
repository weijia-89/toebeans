package app.toebeans.android.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for the Pet add/edit screen. Single form state, mutable through onXxxChange
 * callbacks, validated for the "Save" button.
 *
 * For an existing pet, call [load] in a LaunchedEffect; for a new pet, no-op (state defaults
 * to empty).
 */
@OptIn(ExperimentalUuidApi::class)
public class PetEditViewModel(
    private val petRepository: PetRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PetEditUiState())
    public val state: StateFlow<PetEditUiState> = _state.asStateFlow()

    public fun load(petId: String) {
        viewModelScope.launch {
            val pet = petRepository.getById(petId) ?: return@launch
            _state.value =
                PetEditUiState(
                    petId = pet.id,
                    name = pet.name,
                    species = pet.species,
                    birthdate = pet.birthdate,
                    weightKgText = pet.weightKg?.let { "%.1f".format(it) } ?: "",
                    notes = pet.notes.orEmpty(),
                )
        }
    }

    public fun onNameChange(value: String) {
        // Clearing nameError on input gives the user immediate feedback that a previously
        // surfaced validation error has been addressed.
        _state.update { it.copy(name = value, nameError = null) }
    }

    public fun onSpeciesChange(value: Species) {
        _state.update { it.copy(species = value) }
    }

    public fun onBirthdateChange(value: LocalDate?) {
        _state.update { it.copy(birthdate = value) }
    }

    public fun onWeightChange(value: String) {
        // Accept only digits and a single dot. Empty is allowed (weight is optional).
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _state.update { it.copy(weightKgText = value, weightError = null) }
        }
    }

    public fun onNotesChange(value: String) {
        _state.update { it.copy(notes = value) }
    }

    /**
     * Delete the loaded pet. Returns true if a delete was issued, false if the form is in
     * new-pet mode (nothing to delete).
     *
     * v0.1: the fake repo hard-removes from its in-memory map. M1: the SQLDelight repo
     * will soft-delete via `Pet.archivedAt` (schema column already present) so historical
     * dose-events keep referential integrity. The UI doesn't change — the repo is the
     * seam.
     *
     * Cascade considerations (M1, not here): a pet has medications, which have schedules,
     * which have dose-events. The M1 SQLDelight migration must decide whether deleting a
     * pet cascades to its children or just orphans them with a "deleted pet" placeholder.
     * Until then, the in-memory fake leaves orphans, which is fine because nothing reads
     * a med without joining through its pet first (the Home worklist filters viable
     * bundles in `HomeViewModel.computeDueToday`).
     */
    public suspend fun delete(): Boolean {
        val id = _state.value.petId ?: return false
        petRepository.delete(id)
        return true
    }

    /** Persist. Returns true on success; false on validation failure. */
    public suspend fun save(): Boolean {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Required") }
            return false
        }
        val weightKg = s.weightKgText.toDoubleOrNull()
        if (s.weightKgText.isNotEmpty() && (weightKg == null || weightKg <= 0.0)) {
            _state.update { it.copy(weightError = "Must be a positive number") }
            return false
        }
        // For an existing pet, preserve createdAt and archivedAt from the persisted record.
        // Without this, every edit resets the pet's "created" timestamp and silently
        // un-archives a previously archived pet.
        val existing = s.petId?.let { petRepository.getById(it) }
        val pet =
            Pet(
                id = s.petId ?: "pet-${Uuid.random()}",
                name = s.name.trim(),
                species = s.species,
                birthdate = s.birthdate,
                weightKg = weightKg,
                notes = s.notes.trim().ifEmpty { null },
                createdAt = existing?.createdAt ?: Clock.System.now(),
                archivedAt = existing?.archivedAt,
            )
        petRepository.upsert(pet)
        return true
    }
}

public data class PetEditUiState(
    public val petId: String? = null,
    public val name: String = "",
    public val species: Species = Species.DOG,
    public val birthdate: LocalDate? = null,
    public val weightKgText: String = "",
    public val notes: String = "",
    public val nameError: String? = null,
    public val weightError: String? = null,
) {
    public val isNew: Boolean get() = petId == null
}
