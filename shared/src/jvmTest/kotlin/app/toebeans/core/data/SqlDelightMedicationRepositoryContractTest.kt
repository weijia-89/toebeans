package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `upsert update with discontinuedAt preserves schedule and dose rows`() =
        runTest {
            val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            ToebeansDatabase.Schema.create(freshDriver)
            freshDriver.execute(null, "PRAGMA foreign_keys=ON", 0)
            val localDb = ToebeansDatabase(freshDriver)
            val createdAt = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
            localDb.petQueries.insertPet(
                id = "p1",
                name = "Contract Pet",
                species = "dog",
                birthdate_iso = null,
                weight_kg = 1.0,
                notes = null,
                created_at = createdAt,
                archived_at = null,
            )
            val repo =
                SqlDelightMedicationRepository(
                    database = localDb,
                    dispatcher = Dispatchers.Unconfined,
                )
            val med =
                Medication(
                    id = "m-discontinue-fk",
                    petId = "p1",
                    name = "FK Med",
                    doseAmount = "1mg",
                    notes = null,
                    createdAt = Instant.fromEpochMilliseconds(createdAt),
                    discontinuedAt = null,
                )
            repo.upsert(med)
            localDb.scheduleQueries.insertSchedule(
                id = "sched-fk",
                medication_id = med.id,
                start_date_iso = "2026-05-01",
                end_date_iso = null,
                created_at = createdAt,
            )
            localDb.doseEventQueries.insertDoseEvent(
                id = "dose-fk",
                schedule_id = "sched-fk",
                medication_id = med.id,
                scheduled_at = createdAt,
                fired_at = null,
                resolved_at = null,
                status = "pending",
                note = null,
            )
            val discontinuedAt = Instant.parse("2026-05-20T12:00:00Z")
            repo.upsert(med.copy(discontinuedAt = discontinuedAt))

            assertEquals(
                1,
                localDb.scheduleQueries
                    .selectAllSchedules()
                    .executeAsList()
                    .size,
            )
            assertEquals(
                1,
                localDb.doseEventQueries
                    .selectAllDoseEvents()
                    .executeAsList()
                    .size,
            )
            assertEquals(
                discontinuedAt.toEpochMilliseconds(),
                repo.getById(med.id)?.discontinuedAt?.toEpochMilliseconds(),
            )
        }

    private fun seedParentPet() {
        val refCreatedAt = Instant.parse("2026-05-19T00:00:00Z")
        database.petQueries.insertPet(
            id = "p1",
            name = "Contract Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 1.0,
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            archived_at = null,
        )
        database.petQueries.insertPet(
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
