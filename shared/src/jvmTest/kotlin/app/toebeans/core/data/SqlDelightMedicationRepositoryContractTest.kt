package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant

class SqlDelightMedicationRepositoryContractTest : MedicationRepositoryContract() {
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

    override fun createRepository(): MedicationRepository {
        seedParentPet()
        return SqlDelightMedicationRepository(
            database = database,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    private fun seedParentPet() {
        val refCreatedAt = Instant.parse("2026-05-19T00:00:00Z")
        database.petQueries.upsertPet(
            id = "p1",
            name = "Contract Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 1.0,
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            archived_at = null,
        )
        database.petQueries.upsertPet(
            id = "p2",
            name = "Contract Pet Two",
            species = "cat",
            birthdate_iso = null,
            weight_kg = 1.0,
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            archived_at = null,
        )
    }
}
