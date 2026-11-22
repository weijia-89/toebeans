package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseStatus
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Test-as-spec for migration 4 (dose history survives schedule delete).
 *
 * Two properties are pinned here, because a schema change on the medication-firing path
 * can fail in either direction:
 *
 *  1. Migration from the old CASCADE schema keeps dose rows and NotificationAudit rows.
 *  2. An orphaned dose row is invisible to the enriched receiver lookup, so deleting a
 *     schedule cancels its alarm rather than notifying for a plan that no longer exists.
 */
class DoseEventMigrationSafetyTest {
    private fun createOldVersionFourSchema(driver: SqlDriver) {
        // Build the pre-migration shape from the current schema: NOT NULL schedule_id
        // with ON DELETE CASCADE. All other tables stay at the current version.
        driver.execute(
            null,
            """
            CREATE TABLE DoseEvent_old (
                id TEXT NOT NULL PRIMARY KEY,
                schedule_id TEXT NOT NULL,
                medication_id TEXT NOT NULL,
                scheduled_at INTEGER NOT NULL,
                fired_at INTEGER,
                resolved_at INTEGER,
                status TEXT NOT NULL CHECK (status IN ('pending', 'given', 'skipped', 'missed')),
                note TEXT,
                FOREIGN KEY (schedule_id) REFERENCES Schedule(id) ON DELETE CASCADE,
                FOREIGN KEY (medication_id) REFERENCES Medication(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """INSERT INTO DoseEvent_old SELECT * FROM DoseEvent""",
            0,
        )
        driver.execute(null, "DROP TABLE DoseEvent", 0)
        driver.execute(null, "ALTER TABLE DoseEvent_old RENAME TO DoseEvent", 0)
        driver.execute(null, "PRAGMA user_version = 4", 0)
    }

    @Test
    fun `migration keeps dose rows and delivery audit rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        createOldVersionFourSchema(driver)

        val db = ToebeansDatabase(driver)
        val now = Instant.parse("2026-05-24T08:00:00Z").toEpochMilliseconds()
        db.petQueries.insertPet(
            id = "pet-mig",
            name = "Migration Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 5.0,
            notes = null,
            created_at = now,
            archived_at = null,
        )
        db.medicationQueries.insertMedication(
            id = "med-mig",
            pet_id = "pet-mig",
            name = "Migration Med",
            dose_amount = "1",
            dose_unit = "MG",
            notes = null,
            created_at = now,
            discontinued_at = null,
        )
        db.scheduleQueries.insertSchedule(
            id = "sched-mig",
            medication_id = "med-mig",
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = now,
            anchor_mode = "FOLLOW_PHONE",
        )
        db.doseEventQueries.insertDoseEvent(
            id = "dose-mig",
            schedule_id = "sched-mig",
            medication_id = "med-mig",
            scheduled_at = now,
            fired_at = now,
            resolved_at = now,
            status = DoseStatus.GIVEN.wireName,
            note = null,
        )
        db.notificationAuditQueries.recordDelivery(id = "dose-mig", deliveredAt = now)

        ToebeansDatabase.Schema.migrate(driver, 4, 5)

        val doseRows = db.doseEventQueries.selectAllDoseEvents().executeAsList()
        assertEquals(listOf("dose-mig"), doseRows.map { it.id })
        assertEquals("sched-mig", doseRows.single().schedule_id)
        assertNotNull(
            db.notificationAuditQueries.selectDelivery("dose-mig").executeAsOneOrNull(),
            "migration must not erase the delivery audit row",
        )

        db.scheduleQueries.deleteSchedule("sched-mig")

        assertNull(
            db.doseEventQueries
                .selectAllDoseEvents()
                .executeAsList()
                .single()
                .schedule_id,
        )
        assertNotNull(
            db.notificationAuditQueries.selectDelivery("dose-mig").executeAsOneOrNull(),
            "deleting a schedule must not erase the delivery audit row",
        )
    }

    @Test
    fun `orphaned dose row is invisible to the receiver lookup`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        val db = ToebeansDatabase(driver)
        val now = Instant.parse("2026-05-24T08:00:00Z").toEpochMilliseconds()
        db.petQueries.insertPet(
            id = "pet-orphan",
            name = "Orphan Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 5.0,
            notes = null,
            created_at = now,
            archived_at = null,
        )
        db.medicationQueries.insertMedication(
            id = "med-orphan",
            pet_id = "pet-orphan",
            name = "Orphan Med",
            dose_amount = "1",
            dose_unit = "MG",
            notes = null,
            created_at = now,
            discontinued_at = null,
        )
        db.scheduleQueries.insertSchedule(
            id = "sched-orphan",
            medication_id = "med-orphan",
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = now,
            anchor_mode = "FOLLOW_PHONE",
        )
        db.doseEventQueries.insertDoseEvent(
            id = "dose-orphan",
            schedule_id = "sched-orphan",
            medication_id = "med-orphan",
            scheduled_at = now,
            fired_at = null,
            resolved_at = null,
            status = DoseStatus.PENDING.wireName,
            note = null,
        )

        db.scheduleQueries.deleteSchedule("sched-orphan")

        assertEquals(
            1,
            db.doseEventQueries
                .selectAllDoseEvents()
                .executeAsList()
                .size,
        )
        assertNull(
            db.doseEventQueries.selectEnrichedReminderById("dose-orphan").executeAsOneOrNull(),
            "an orphaned dose must not fire for a deleted schedule",
        )
    }
}
