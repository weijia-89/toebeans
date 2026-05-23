package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant

/**
 * Phase 6 concrete subclass of [ScheduleRepositoryContract]. The factory constructs a freshly
 * isolated [SqlDelightScheduleRepository] backed by an in-memory [JdbcSqliteDriver]. Every
 * proof that the SQLDelight implementation satisfies the contract Wei reviewed in Phase 5.
 *
 * Lives in `:shared:jvmTest` for the same reasons documented on
 * [SqlDelightPetRepositoryContractTest].
 *
 * FK enforcement: [configureDb] enables `PRAGMA foreign_keys=ON` per ADR-0010 and seeds the
 * parent Pet + Medication rows that case 11's cascade and every upsert depend on. The contract
 * itself uses medication id `"m1"` without inserting parents; seeding here keeps the abstract
 * contract stable across stub and SqlDelight subclasses.
 */
class SqlDelightScheduleRepositoryContractTest : ScheduleRepositoryContract() {
    private lateinit var database: ToebeansDatabase
    private lateinit var testDriver: SqlDriver

    override fun obtainDriver(): SqlDriver {
        if (!::testDriver.isInitialized) {
            testDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            ToebeansDatabase.Schema.create(testDriver)
            database = ToebeansDatabase(testDriver)
        }
        return testDriver
    }

    override fun configureDb(driver: SqlDriver) {
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        seedParentRows()
    }

    override fun createRepository(): ScheduleRepository {
        if (!::database.isInitialized) {
            obtainDriver()
        }
        return SqlDelightScheduleRepository(
            database = database,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    override suspend fun deleteParentMedication(medicationId: String) {
        database.medicationQueries.deleteMedication(medicationId)
    }

    private fun seedParentRows() {
        val refCreatedAt = Instant.parse("2026-05-19T00:00:00Z")
        database.petQueries.upsertPet(
            id = "p1",
            name = "Contract Pet",
            species = Species.DOG.wireName,
            birthdate_iso = null,
            weight_kg = 10.0,
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = "m1",
            pet_id = "p1",
            name = "Contract Med",
            dose_amount = "1mg",
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            discontinued_at = null,
        )
    }
}
