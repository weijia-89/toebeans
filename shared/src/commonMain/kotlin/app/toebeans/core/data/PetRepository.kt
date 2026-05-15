package app.toebeans.core.data

import app.toebeans.core.model.Pet
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for [Pet] CRUD. Defined in commonMain so future SQLDelight-backed
 * implementations live in :shared while the UI layer in :androidApp consumes the interface.
 *
 * **Flow-based** because the UI needs to react to changes from any source (initial load,
 * background sync, future caregiver-share imports). Stateless callers — repositories
 * hold no UI state.
 *
 * **No network. No analytics.** Per AGENTS.md, all v1 storage is local-only. A repository
 * implementation that attempts a network call will fail the no-network fitness function.
 */
public interface PetRepository {
    /** Observe all pets. Emits the current snapshot immediately, then on each change. */
    public fun observeAll(): Flow<List<Pet>>

    /** One-shot fetch of a single pet, or `null` if not found. */
    public suspend fun getById(id: String): Pet?

    /**
     * Observe a single pet by id. Emits the current value (or `null` if not yet present)
     * immediately, then on each change. Needed for screens that display one pet's state and
     * must react when the user edits that pet in a sibling screen and returns via back-stack.
     */
    public fun observeById(id: String): Flow<Pet?>

    /** Insert or update. Idempotent on [Pet.id]. */
    public suspend fun upsert(pet: Pet)

    /** Hard delete. Cascading rules (Medications, Schedules) live in SQLDelight migrations. */
    public suspend fun delete(id: String)
}
