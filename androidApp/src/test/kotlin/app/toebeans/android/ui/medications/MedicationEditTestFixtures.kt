package app.toebeans.android.ui.medications

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.model.Medication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock

// Shared fixtures for the `MedicationEditViewModel*` test family
// (`MedicationEditViewModelDeleteTest`, `MedicationEditViewModelDiscontinueTest`, and any
// future sibling). Lives in its own file because Kotlin does not allow two top-level
// private classes with the same name in the same package, even across files, so each
// sibling test that wanted its own `InMemoryMedRepo` would collide.
//
// Both helpers are `internal` so they're reachable from any file inside this Gradle test
// source set without leaking outside the medications package.

/** A Medication with sensible defaults, mutable only via .copy(). */
internal fun seedMed(
    id: String,
    petId: String,
    name: String,
): Medication =
    Medication(
        id = id,
        petId = petId,
        name = name,
        doseAmount = "2.5 mg",
        notes = null,
        createdAt = Clock.System.now(),
        discontinuedAt = null,
    )

/**
 * A test double for [MedicationRepository] backed by a `MutableStateFlow<Map>`. Sufficient
 * for the ViewModel's getById/upsert/delete contract; emits per-pet and global observables
 * for tests that need them.
 */
internal class InMemoryMedRepo(
    initial: Medication,
) : MedicationRepository {
    private val store = MutableStateFlow(mapOf(initial.id to initial))

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override fun observeAll(): Flow<List<Medication>> = store.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: String): Medication? = store.value[id]

    override suspend fun upsert(medication: Medication) {
        store.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }
}
