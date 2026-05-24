package app.toebeans.core.notifications

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import kotlinx.datetime.Instant

/**
 * M1.3 concrete subclass of [ReminderLookupContract]. Proves [SqlDelightReminderLookup]
 * satisfies the contract approved in Phase 1 against an isolated in-memory JDBC driver.
 */
class SqlDelightReminderLookupContractTest : ReminderLookupContract() {
    private lateinit var database: ToebeansDatabase
    private var driver: SqlDriver? = null

    override fun createLookup(): ReminderLookup {
        driver?.close()
        val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(freshDriver)
        freshDriver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver = freshDriver
        database = ToebeansDatabase(freshDriver)
        return SqlDelightReminderLookup(database)
    }

    override fun seedReminder(reminder: ScheduledReminder) {
        seedParentChain(reminder.scheduleId)
        database.doseEventQueries.insertDoseEvent(
            id = reminder.id,
            schedule_id = reminder.scheduleId,
            medication_id = "med-lookup-contract",
            scheduled_at = reminder.scheduledAt.toEpochMilliseconds(),
            fired_at = null,
            resolved_at = null,
            status = "pending",
            note = null,
        )
    }

    override fun removeSeededReminder(reminderId: String) {
        database.doseEventQueries.deleteDoseEvent(reminderId)
    }

    private fun seedParentChain(scheduleId: String) {
        val createdAt = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
        database.petQueries.upsertPet(
            id = "pet-lookup-contract",
            name = "Lookup Pet",
            species = "cat",
            birthdate_iso = null,
            weight_kg = 4.0,
            notes = null,
            created_at = createdAt,
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = "med-lookup-contract",
            pet_id = "pet-lookup-contract",
            name = "Lookup Med",
            dose_amount = "2.5mg",
            notes = null,
            created_at = createdAt,
            discontinued_at = null,
        )
        database.scheduleQueries.upsertSchedule(
            id = scheduleId,
            medication_id = "med-lookup-contract",
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = createdAt,
        )
    }
}
