package app.toebeans.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import app.toebeans.core.db.Pet as PetRow

/**
 * SQLDelight-backed [PetRepository] implementation. M1 Decision 4a Phase 2.
 *
 * Backs every query against the generated `petQueries` API from `Pet.sq` (in
 * `shared/src/commonMain/sqldelight/app/toebeans/core/db/`). Phase 2 of M1 Decision 4a; Phase 1
 * shipped [PetRepositoryContract] which this implementation must satisfy. The sibling
 * `SqlDelightPetRepositoryContractTest` in `:shared:jvmTest` proves it does.
 *
 * Threading and dispatcher choice:
 *   - Suspending writes ([upsert], [delete]) and one-shot reads ([getById]) execute on the
 *     injected [dispatcher]. The Android caller (`AppModule`) injects `Dispatchers.IO`. JVM
 *     tests inject `Dispatchers.Unconfined` for determinism under `runTest`.
 *   - Flow reads ([observeAll], [observeById]) use SQLDelight's coroutine extensions
 *     (`asFlow().mapToList(dispatcher)` and `mapToOneOrNull(dispatcher)`). The dispatcher is
 *     where the SELECT runs and where downstream `map` collectors observe; consumers can add
 *     their own `.flowOn(...)` if they need a different observation thread.
 *
 * Ordering: [observeAll] delegates to `Pet.sq selectAllPets` which orders by
 * `name COLLATE NOCASE` (case-insensitive). This matches contract test 4 in
 * [PetRepositoryContract]. The query intentionally does NOT filter on `archived_at`;
 * [observeAll] returns active and archived pets alike. UI surfaces wanting active-only
 * should call a future `observeActive()` method (not on the contract today) or use
 * `selectAllActivePets` via a separate repository surface.
 *
 * FK cascade: [delete] is hard delete. ADR-0010 mandates that the platform's
 * `DatabaseFactory` enables `PRAGMA foreign_keys=ON` so dependents (Medication, Schedule,
 * SchedulePhase, DoseEvent) cascade-delete when the parent Pet is removed. The cascade
 * behavior is NOT asserted in this repository's contract; it is asserted in the dependent
 * repository contracts (Phases 3, 5, 7). The Android `AppModule` wires the FK callback and
 * binds this class at runtime.
 *
 * Schema row to domain mapping:
 *   - `created_at` (INTEGER epoch ms UTC) ↔ [Pet.createdAt] (Instant)
 *   - `archived_at` (INTEGER epoch ms UTC, nullable) ↔ [Pet.archivedAt] (Instant?)
 *   - `birthdate_iso` (TEXT ISO yyyy-MM-dd, nullable) ↔ [Pet.birthdate] (LocalDate?)
 *   - `species` (TEXT, e.g. "dog" / "cat") ↔ [Pet.species] (Species enum, via wireName)
 *
 * The [Pet] domain init block enforces `id.isNotBlank()`, `name.isNotBlank()`, and
 * `weightKg > 0.0`. A row that violates any of these will fail to deserialize with an
 * `IllegalArgumentException`. The schema does NOT enforce these constraints (no CHECK on
 * TEXT NOT NULL allows empty strings); a defensive future change would be CHECK constraints
 * in `Pet.sq` plus a schema migration. Out of scope for Phase 2.
 */
public class SqlDelightPetRepository(
    private val database: ToebeansDatabase,
    private val dispatcher: CoroutineDispatcher,
) : PetRepository {
    private val queries get() = database.petQueries

    override fun observeAll(): Flow<List<Pet>> =
        queries
            .selectAllPets()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(PetRow::toDomain) }

    override suspend fun getById(id: String): Pet? =
        withContext(dispatcher) {
            queries.selectPetById(id).executeAsOneOrNull()?.toDomain()
        }

    override fun observeById(id: String): Flow<Pet?> =
        queries
            .selectPetById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }

    override suspend fun upsert(pet: Pet): Unit =
        withContext(dispatcher) {
            queries.upsertPet(
                id = pet.id,
                name = pet.name,
                species = pet.species.wireName,
                birthdate_iso = pet.birthdate?.toString(),
                weight_kg = pet.weightKg,
                notes = pet.notes,
                created_at = pet.createdAt.toEpochMilliseconds(),
                archived_at = pet.archivedAt?.toEpochMilliseconds(),
            )
        }

    override suspend fun delete(id: String): Unit =
        withContext(dispatcher) {
            queries.deletePet(id)
        }
}

/**
 * Convert a SQLDelight-generated row to the domain [Pet]. Free function (not a member of
 * [SqlDelightPetRepository]) so it can be unit-tested in isolation and so the repository
 * class stays focused on Flow plumbing.
 *
 * Throws [IllegalArgumentException] if the row violates the [Pet] domain invariants
 * (blank id, blank name, non-positive weight). The schema does not enforce these today;
 * the domain init block is the load-bearing guard.
 */
internal fun PetRow.toDomain(): Pet =
    Pet(
        id = id,
        name = name,
        species = Species.fromWireName(species),
        birthdate = birthdate_iso?.let(LocalDate::parse),
        weightKg = weight_kg,
        notes = notes,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        archivedAt = archived_at?.let(Instant::fromEpochMilliseconds),
    )
