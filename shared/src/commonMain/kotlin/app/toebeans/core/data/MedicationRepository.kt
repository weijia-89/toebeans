package app.toebeans.core.data

import app.toebeans.core.model.Medication
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for [Medication] CRUD, scoped by pet.
 *
 * Same posture as [PetRepository]: local-only, Flow-based, no network/analytics.
 */
public interface MedicationRepository {
    /** Observe all medications for a single pet, ordered by name. */
    public fun observeForPet(petId: String): Flow<List<Medication>>

    /**
     * Observe every medication across every pet, ordered by name. The caller groups
     * by [Medication.petId] when it needs counts or cross-pet rollups (e.g. the
     * Home-screen "N meds" subtitle on each pet chip).
     *
     * Returns active and discontinued medications alike — UI filters on
     * [Medication.discontinuedAt] when "active only" semantics are wanted.
     */
    public fun observeAll(): Flow<List<Medication>>

    /** One-shot fetch of a single medication. */
    public suspend fun getById(id: String): Medication?

    /** Insert or update. Idempotent on [Medication.id]. */
    public suspend fun upsert(medication: Medication)

    /** Hard delete. Schedules referencing this medication are cascaded by the schema. */
    public suspend fun delete(id: String)
}
