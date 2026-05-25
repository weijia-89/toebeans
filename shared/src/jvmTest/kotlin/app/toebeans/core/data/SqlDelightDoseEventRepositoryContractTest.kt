package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.notifications.SqlDelightReminderLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SQLDelight concrete subclass of [DoseEventRepositoryContract].
 *
 * F4: shared contract tests live on the abstract base; this class adds JDBC/receiver
 * assertions that need [ToebeansDatabase] queries outside the repository interface.
 */
class SqlDelightDoseEventRepositoryContractTest : DoseEventRepositoryContract() {
    private lateinit var database: ToebeansDatabase
    private lateinit var driver: SqlDriver

    override val contractScheduleId: String = SCHEDULE_ID
    override val contractMedicationId: String = MED_ID
    override val contractDoseId: String = DOSE_ID

    override fun obtainDriver(): SqlDriver {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        database = ToebeansDatabase(driver)
        return driver
    }

    override fun configureDb(driver: SqlDriver) {
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    override fun createRepository(): DoseEventRepository {
        seedParentChain(scheduleId = SCHEDULE_ID)
        return SqlDelightDoseEventRepository(
            database = database,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `recordGivenNow persists row visible to selectDoseEventById for receiver lookup`() =
        runTest {
            val repo = createRepository()
            val at = Instant.parse("2026-05-24T08:00:00Z")
            repo.recordGivenNow(
                doseEventId = DOSE_ID,
                scheduleId = SCHEDULE_ID,
                medicationId = MED_ID,
                at = at,
                note = "quick log",
            )

            val row =
                database.doseEventQueries
                    .selectDoseEventById(DOSE_ID)
                    .executeAsOneOrNull()
            assertNotNull(row, "insert must be visible to indexed receiver lookup query")
            assertEquals(DOSE_ID, row.id)
            assertEquals(SCHEDULE_ID, row.schedule_id)
            assertEquals(MED_ID, row.medication_id)
            assertEquals("given", row.status)

            val lookup = SqlDelightReminderLookup(database)
            val reminder = lookup.lookup(DOSE_ID)
            assertNotNull(reminder, "SqlDelightReminderLookup must resolve persisted dose id")
            assertEquals(SCHEDULE_ID, reminder.scheduleId)
            assertEquals(at, reminder.scheduledAt)
        }

    @Test
    fun `delete removes row from selectDoseEventById`() =
        runTest {
            val repo = createRepository()
            val at = Instant.parse("2026-05-24T10:00:00Z")
            repo.recordGivenNow(
                doseEventId = DOSE_ID,
                scheduleId = SCHEDULE_ID,
                medicationId = MED_ID,
                at = at,
            )
            repo.delete(DOSE_ID)

            val row =
                database.doseEventQueries
                    .selectDoseEventById(DOSE_ID)
                    .executeAsOneOrNull()
            assertNull(row)
        }

    private fun seedParentChain(scheduleId: String) {
        val createdAt = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
        database.petQueries.upsertPet(
            id = PET_ID,
            name = "Contract Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 10.0,
            notes = null,
            created_at = createdAt,
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = MED_ID,
            pet_id = PET_ID,
            name = "Contract Med",
            dose_amount = "5mg",
            notes = null,
            created_at = createdAt,
            discontinued_at = null,
        )
        database.scheduleQueries.upsertSchedule(
            id = scheduleId,
            medication_id = MED_ID,
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = createdAt,
        )
    }

    private companion object {
        const val PET_ID = "pet-dose-contract"
        const val MED_ID = "med-dose-contract"
        const val SCHEDULE_ID = "sched-dose-contract"
        const val DOSE_ID = "dose-contract-1"
    }
}
