package app.toebeans.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseStatus
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test-as-spec for migration 5 (ADR-0020 F5a: the VetVisit / Vaccine / VaccinationGiven
 * schema), mirroring [DoseEventMigrationSafetyTest].
 *
 * The pre-migration shape is produced by dropping the three vet tables from a fresh
 * version-6 schema and setting `user_version = 5`. That keeps every other table at its
 * shipped shape and makes the migration add only the vet surface.
 *
 * The audit row is seeded under a different Pet than the vet rows. Under ADR-0010 a Pet
 * delete cascades through Medication -> DoseEvent -> NotificationAudit, so a Pet that owns
 * the dose chain cannot also be the Pet whose vet rows are deleted if the audit row must
 * survive the assertion.
 */
class VetVisitMigrationSafetyTest {
    private fun createOldVersionFiveSchema(driver: SqlDriver) {
        driver.execute(null, "DROP TABLE VaccinationGiven", 0)
        driver.execute(null, "DROP TABLE VetVisit", 0)
        driver.execute(null, "DROP TABLE Vaccine", 0)
        driver.execute(null, "PRAGMA user_version = 5", 0)
    }

    private data class DoseChainSeed(
        val db: ToebeansDatabase,
        val petId: String,
        val medId: String,
        val schedId: String,
        val doseId: String,
        val now: Long,
    )

    private fun seedDoseChain(seed: DoseChainSeed) {
        val db = seed.db
        val petId = seed.petId
        val medId = seed.medId
        val schedId = seed.schedId
        val doseId = seed.doseId
        val now = seed.now
        db.petQueries.insertPet(
            id = petId,
            name = "Migration Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 5.0,
            notes = null,
            created_at = now,
            archived_at = null,
        )
        db.medicationQueries.insertMedication(
            id = medId,
            pet_id = petId,
            name = "Migration Med",
            dose_amount = "1",
            dose_unit = "MG",
            notes = null,
            created_at = now,
            discontinued_at = null,
        )
        db.scheduleQueries.insertSchedule(
            id = schedId,
            medication_id = medId,
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = now,
            anchor_mode = "FOLLOW_PHONE",
        )
        db.doseEventQueries.insertDoseEvent(
            id = doseId,
            schedule_id = schedId,
            medication_id = medId,
            scheduled_at = now,
            fired_at = now,
            resolved_at = now,
            status = DoseStatus.GIVEN.wireName,
            note = null,
        )
        db.notificationAuditQueries.recordDelivery(id = doseId, deliveredAt = now)
    }

    private fun seedVetRows(
        driver: SqlDriver,
        petId: String,
    ) {
        driver.execute(
            null,
            """
            INSERT INTO VetVisit (id, pet_id, kind, scheduled_at, occurred_at, provider, notes, created_at)
            VALUES ('vv-mig', '$petId', 'vaccination', NULL, 1779148800000, 'Lakeside Veterinary Clinic', 'Rabies booster', 1779148800000)
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO Vaccine (id, name, species, core_or_non_core, default_interval_months, regulatory_region)
            VALUES ('rabies-us', 'Rabies (US-regulated)', 'dog', 'core', 36, '["US-rabies"]')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO VaccinationGiven (vet_visit_id, vaccine_id, lot_number, expiration_date, dose_route)
            VALUES ('vv-mig', 'rabies-us', 'LOT-4471', '2029-05-01', 'IM')
            """.trimIndent(),
            0,
        )
    }

    private fun scalarLong(
        driver: SqlDriver,
        sql: String,
    ): Long =
        driver
            .executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else -1L)
                },
                parameters = 0,
            ).value

    private fun scalarText(
        driver: SqlDriver,
        sql: String,
    ): String? =
        driver
            .executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
                },
                parameters = 0,
            ).value

    private fun stringList(
        driver: SqlDriver,
        sql: String,
    ): List<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    QueryResult.Value(
                        buildList {
                            while (cursor.next().value) add(cursor.getString(0)!!)
                        },
                    )
                },
                parameters = 0,
            ).value

    private fun foreignKeyViolations(driver: SqlDriver): List<String> = stringList(driver, "PRAGMA foreign_key_check")

    private fun seedVetPet(
        db: ToebeansDatabase,
        now: Long,
    ) {
        db.petQueries.insertPet(
            id = "pet-vet",
            name = "Vet Pet",
            species = "cat",
            birthdate_iso = null,
            weight_kg = 2.0,
            notes = null,
            created_at = now,
            archived_at = null,
        )
    }

    private fun assertVetSchema(driver: SqlDriver) {
        val tables = stringList(driver, "SELECT name FROM sqlite_master WHERE type = 'table'")
        assertTrue(
            setOf("VetVisit", "Vaccine", "VaccinationGiven").all(tables::contains),
            "migration 5 must create all three vet tables: $tables",
        )
        val indexes = stringList(driver, "SELECT name FROM sqlite_master WHERE type = 'index'")
        assertTrue(
            setOf("VetVisit_pet_id_idx", "Vaccine_species_idx", "VaccinationGiven_vaccine_id_idx")
                .all(indexes::contains),
            "migration 5 must create the vet indexes: $indexes",
        )
        assertEquals(emptyList(), foreignKeyViolations(driver))
        assertEquals("ok", scalarText(driver, "PRAGMA integrity_check"))
    }

    @Test
    fun `migration 5 preserves seeded rows and adds the vet tables with intact constraints`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        createOldVersionFiveSchema(driver)

        val db = ToebeansDatabase(driver)
        val now = Instant.parse("2026-05-24T08:00:00Z").toEpochMilliseconds()
        seedDoseChain(DoseChainSeed(db, "pet-mig", "med-mig", "sched-mig", "dose-mig", now))
        seedVetPet(db, now)

        ToebeansDatabase.Schema.migrate(driver, 5, 6)

        // The repo's hand-rolled migration harness calls Schema.migrate directly, which
        // bypasses the driver callback that writes user_version in production. The
        // generated Schema.version is the chain proof available in this harness.
        assertEquals(7L, ToebeansDatabase.Schema.version)
        assertEquals(
            "Migration Pet",
            db.petQueries
                .selectPetById("pet-mig")
                .executeAsOne()
                .name,
        )
        assertEquals(
            "Migration Med",
            db.medicationQueries
                .selectMedicationById("med-mig")
                .executeAsOne()
                .name,
        )
        assertEquals(
            "sched-mig",
            db.scheduleQueries
                .selectScheduleById("sched-mig")
                .executeAsOne()
                .id,
        )
        val dose =
            db.doseEventQueries
                .selectAllDoseEvents()
                .executeAsList()
                .single()
        assertEquals("dose-mig", dose.id)
        assertEquals("sched-mig", dose.schedule_id)
        assertNotNull(
            db.notificationAuditQueries.selectDelivery("dose-mig").executeAsOneOrNull(),
            "migration must not erase the delivery audit row",
        )

        assertVetSchema(driver)

        seedVetRows(driver, "pet-vet")
        db.petQueries.deletePet("pet-vet")

        assertEquals(0L, scalarLong(driver, "SELECT COUNT(*) FROM VetVisit WHERE pet_id = 'pet-vet'"))
        assertEquals(0L, scalarLong(driver, "SELECT COUNT(*) FROM VaccinationGiven"))
        assertNotNull(
            db.notificationAuditQueries.selectDelivery("dose-mig").executeAsOneOrNull(),
            "deleting an unrelated Pet must not erase the delivery audit row",
        )
        assertEquals(emptyList(), foreignKeyViolations(driver))
    }

    @Test
    fun `vet foreign keys cascade from visits and restrict vaccine deletes`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        val db = ToebeansDatabase(driver)
        val now = Instant.parse("2026-05-24T08:00:00Z").toEpochMilliseconds()
        db.petQueries.insertPet(
            id = "pet-fk",
            name = "FK Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 4.0,
            notes = null,
            created_at = now,
            archived_at = null,
        )
        seedVetRows(driver, "pet-fk")

        val restriction =
            assertFailsWith<Exception> {
                driver.execute(null, "DELETE FROM Vaccine WHERE id = 'rabies-us'", 0)
            }
        assertTrue(
            restriction.message?.contains("FOREIGN KEY", ignoreCase = true) == true,
            "deleting a vaccine with administered records must fail loudly: ${restriction.message}",
        )
        assertEquals(1L, scalarLong(driver, "SELECT COUNT(*) FROM Vaccine"))
        assertEquals(1L, scalarLong(driver, "SELECT COUNT(*) FROM VaccinationGiven"))

        driver.execute(null, "DELETE FROM VetVisit WHERE id = 'vv-mig'", 0)

        assertEquals(0L, scalarLong(driver, "SELECT COUNT(*) FROM VaccinationGiven"))
        assertEquals(1L, scalarLong(driver, "SELECT COUNT(*) FROM Vaccine"))
        assertEquals(emptyList(), foreignKeyViolations(driver))
        assertEquals("ok", scalarText(driver, "PRAGMA integrity_check"))
    }

    @Test
    fun `repeating migration at version six is a no-op`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        createOldVersionFiveSchema(driver)
        val db = ToebeansDatabase(driver)
        val now = Instant.parse("2026-05-24T08:00:00Z").toEpochMilliseconds()
        seedDoseChain(DoseChainSeed(db, "pet-mig", "med-mig", "sched-mig", "dose-mig", now))

        ToebeansDatabase.Schema.migrate(driver, 5, 6)
        seedVetRows(driver, "pet-mig")
        val before =
            listOf(
                "Pet",
                "Medication",
                "Schedule",
                "DoseEvent",
                "NotificationAudit",
                "VetVisit",
                "Vaccine",
                "VaccinationGiven",
            ).associateWith { scalarLong(driver, "SELECT COUNT(*) FROM $it") }

        ToebeansDatabase.Schema.migrate(driver, 6, 6)

        assertEquals(7L, ToebeansDatabase.Schema.version)
        assertEquals(
            before,
            listOf(
                "Pet",
                "Medication",
                "Schedule",
                "DoseEvent",
                "NotificationAudit",
                "VetVisit",
                "Vaccine",
                "VaccinationGiven",
            ).associateWith { scalarLong(driver, "SELECT COUNT(*) FROM $it") },
        )
        assertEquals(emptyList(), foreignKeyViolations(driver))
        assertEquals("ok", scalarText(driver, "PRAGMA integrity_check"))
    }
}
