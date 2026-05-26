package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant

/**
 * jvmTest SqlDelight regression gate for [ScheduleRepositoryContract]: JDBC SQLite,
 * `PRAGMA foreign_keys=ON`, and real ADR-0010 CASCADE on case 11. The factory constructs
 * a freshly isolated [SqlDelightScheduleRepository] backed by an in-memory [JdbcSqliteDriver].
 *
 * [InMemoryScheduleRepositoryContractTest] (commonTest) runs the same 11 cases against an
 * in-memory fake for harness/Robolectric paths without JDBC; case 11 cascade there is
 * behavioral simulation only (see [ScheduleRepositoryContract] subclass roles).
 *
 * Lives in `:shared:jvmTest` for the same reason as [SqlDelightPetRepositoryContractTest]:
 * the JDBC SQLite driver is not available in `commonTest`.
 *
 * FK enforcement: [configureDb] enables `PRAGMA foreign_keys=ON` per ADR-0010 so case 11
 * (Medication delete CASCADE → Schedule + SchedulePhase) exercises real SQLite behavior.
 * [seedParentMedication] inserts the minimal Pet + Medication chain so schedule upserts satisfy
 * the `medication_id` FK without widening the abstract contract.
 *
 * [AppModule] remains on [app.toebeans.android.data.FakeScheduleRepository]; DI swap is a
 * follow-up queue row after merge.
 *
 * sdk-review F1: Phase 5/6 future tense removed; SqlDelight subclass is the green FK gate today.
 */
class SqlDelightScheduleRepositoryContractTest : ScheduleRepositoryContract() {
    private lateinit var database: ToebeansDatabase
    private lateinit var driver: SqlDriver

    override fun obtainDriver(): SqlDriver {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        database = ToebeansDatabase(driver)
        return driver
    }

    override fun configureDb(driver: SqlDriver) {
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    override fun createRepository(): ScheduleRepository {
        seedParentMedication()
        return SqlDelightScheduleRepository(
            database = database,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    override suspend fun deleteParentMedication(medicationId: String) {
        database.medicationQueries.deleteMedication(medicationId)
    }

    private fun seedParentMedication() {
        val refCreatedAt = Instant.parse("2026-05-19T00:00:00Z")
        database.petQueries.upsertPet(
            id = "p-contract",
            name = "Contract Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 1.0,
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = "m1",
            pet_id = "p-contract",
            name = "Contract Med",
            dose_amount = "1mg",
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            discontinued_at = null,
        )
    }
}
