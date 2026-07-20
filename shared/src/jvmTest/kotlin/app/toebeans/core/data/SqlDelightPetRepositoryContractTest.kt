package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 2 concrete subclass of [PetRepositoryContract]. The factory constructs a freshly
 * isolated [SqlDelightPetRepository] backed by an in-memory [JdbcSqliteDriver] (via the JVM
 * `actual` of [DatabaseFactory]). Every inherited contract test should turn GREEN; this is
 * the proof that the SQLDelight implementation satisfies the contract Wei reviewed and
 * approved in PR #29 (Phase 1).
 *
 * Why this lives in `:shared:jvmTest` and not `:shared:commonTest`:
 *   - [DatabaseFactory] is `expect`/`actual`; its `actual` for JVM uses [JdbcSqliteDriver]
 *     which is JVM-only. There is no actual for `commonTest` because there is no
 *     platform-agnostic in-memory SQLite driver at v0.1 (Android needs Robolectric;
 *     iOS is disabled at M1 per gradle.properties).
 *   - Same pattern as [app.toebeans.core.data.db.SchemaSmokeTest] which lives in jvmTest for
 *     the same reason. The brief for that file documented the choice; this file follows it.
 *   - The `:shared:jvmTest` source set has access to `:shared:commonTest` declarations
 *     (including [PetRepositoryContract]), so the abstract contract pattern works across
 *     source-set boundaries.
 *
 * Dispatcher choice: [Dispatchers.Unconfined] keeps the test deterministic under
 * `runTest`; the contract tests do not assert on threading, only on observable behavior.
 * Production Android wiring should inject `Dispatchers.IO` for the disk-bound SQLDelight
 * calls (see [SqlDelightPetRepository] KDoc § Threading and dispatcher choice).
 *
 * Database isolation: each test gets a fresh in-memory database with `PRAGMA foreign_keys=ON`
 * so FK regression cases exercise real SQLite CASCADE behavior.
 */
class SqlDelightPetRepositoryContractTest : PetRepositoryContract() {
    private lateinit var database: ToebeansDatabase
    private lateinit var driver: SqlDriver

    override fun createRepository(): PetRepository {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        database = ToebeansDatabase(driver)
        return SqlDelightPetRepository(
            database = database,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `upsert update with renamed pet preserves medication child rows`() =
        runTest {
            val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            ToebeansDatabase.Schema.create(freshDriver)
            freshDriver.execute(null, "PRAGMA foreign_keys=ON", 0)
            val localDb = ToebeansDatabase(freshDriver)
            val repo =
                SqlDelightPetRepository(
                    database = localDb,
                    dispatcher = Dispatchers.Unconfined,
                )
            val createdAt = Instant.parse("2026-05-19T00:00:00Z")
            val pet =
                Pet(
                    id = "p-rename-fk",
                    name = "Before",
                    species = Species.DOG,
                    birthdate = null,
                    weightKg = 5.0,
                    notes = null,
                    createdAt = createdAt,
                    archivedAt = null,
                )
            repo.upsert(pet)
            localDb.medicationQueries.insertMedication(
                id = "m-child",
                pet_id = pet.id,
                name = "Child Med",
                dose_amount = "1mg",
                dose_unit = "MG",
                notes = null,
                created_at = createdAt.toEpochMilliseconds(),
                discontinued_at = null,
            )
            repo.upsert(pet.copy(name = "After"))

            assertEquals(
                1,
                localDb.medicationQueries
                    .selectAllMedications()
                    .executeAsList()
                    .size,
            )
            assertEquals("After", repo.getById(pet.id)?.name)
        }
}
