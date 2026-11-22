package app.toebeans.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseStatus
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test-as-spec for ADR-0007 migration 6 -> 7: the nullable `Pet.home_timezone` column.
 *
 * Pre-migration shape is produced by rebuilding `Pet` without the new column from a
 * fresh version-7 schema and setting `user_version = 6`. FK enforcement is toggled off
 * only for that rebuild (otherwise DROP TABLE Pet would cascade away the children the
 * test needs to prove are preserved), then back on for the assertions.
 */
class HomeTimezoneMigrationSafetyTest {
    private fun createOldVersionSixSchema(driver: SqlDriver) {
        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
        driver.execute(null, "DROP INDEX Pet_archived_at_idx", 0)
        driver.execute(
            null,
            """
            CREATE TABLE Pet_v6 (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                species TEXT NOT NULL,
                birthdate_iso TEXT,
                weight_kg REAL,
                notes TEXT,
                created_at INTEGER NOT NULL,
                archived_at INTEGER
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO Pet_v6 (id, name, species, birthdate_iso, weight_kg, notes, created_at, archived_at)
            SELECT id, name, species, birthdate_iso, weight_kg, notes, created_at, archived_at FROM Pet
            """.trimIndent(),
            0,
        )
        driver.execute(null, "DROP TABLE Pet", 0)
        driver.execute(null, "ALTER TABLE Pet_v6 RENAME TO Pet", 0)
        driver.execute(null, "CREATE INDEX Pet_archived_at_idx ON Pet(archived_at)", 0)
        driver.execute(null, "PRAGMA user_version = 6", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    private fun tableInfo(driver: SqlDriver): List<List<String?>> =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA table_info(Pet)",
                mapper = { cursor ->
                    QueryResult.Value(
                        buildList {
                            while (cursor.next().value) {
                                add(
                                    listOf(
                                        cursor.getString(1),
                                        cursor.getString(2),
                                        cursor.getString(3),
                                        cursor.getString(4),
                                        cursor.getString(5),
                                    ),
                                )
                            }
                        },
                    )
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

    @Test
    fun `migration 6 adds a nullable home_timezone column and preserves pet and child rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        createOldVersionSixSchema(driver)

        assertEquals(7L, ToebeansDatabase.Schema.version)
        val db = ToebeansDatabase(driver)
        val now = Instant.parse("2026-11-22T08:00:00Z").toEpochMilliseconds()
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
            start_date_iso = "2026-11-22",
            end_date_iso = null,
            created_at = now,
            anchor_mode = "STAY_HOME_TZ",
        )
        db.doseEventQueries.insertDoseEvent(
            id = "dose-mig",
            schedule_id = "sched-mig",
            medication_id = "med-mig",
            scheduled_at = now,
            fired_at = null,
            resolved_at = null,
            status = DoseStatus.PENDING.wireName,
            note = null,
        )

        ToebeansDatabase.Schema.migrate(driver, 6, 7)

        assertTrue(
            tableInfo(driver).any { it[0] == "home_timezone" },
            "migration 6 must append a home_timezone column to Pet",
        )
        val column = tableInfo(driver).single { it[0] == "home_timezone" }
        assertEquals("TEXT", column[1], "home_timezone must be TEXT")
        assertEquals("0", column[2], "home_timezone must be nullable")
        assertEquals(null, column[3], "home_timezone must have no default")
        assertEquals(
            null,
            scalarText(driver, "SELECT home_timezone FROM Pet WHERE id = 'pet-mig'"),
            "legacy rows must read NULL, not a fabricated device-zone default",
        )
        assertEquals(
            "Migration Pet",
            db.petQueries
                .selectPetById("pet-mig")
                .executeAsOne()
                .name,
        )
        assertNotNull(db.medicationQueries.selectMedicationById("med-mig").executeAsOneOrNull())
        assertNotNull(db.scheduleQueries.selectScheduleById("sched-mig").executeAsOneOrNull())
        assertEquals(
            "dose-mig",
            db.doseEventQueries
                .selectDoseEventById("dose-mig")
                .executeAsOne()
                .id,
        )
        assertEquals(emptyList(), stringList(driver, "PRAGMA foreign_key_check"))
        assertEquals("ok", scalarText(driver, "PRAGMA integrity_check"))
    }

    @Test
    fun `fresh install and migrated database report identical Pet table shape`() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(fresh)
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(migrated)
        migrated.execute(null, "PRAGMA foreign_keys=ON", 0)
        createOldVersionSixSchema(migrated)
        migrated.execute(null, "PRAGMA foreign_keys=OFF", 0)
        // Seed before migration so the row survives the rebuild-free ADD COLUMN.
        migrated.execute(null, "PRAGMA foreign_keys=ON", 0)
        migrated.execute(
            null,
            "INSERT INTO Pet (id, name, species, created_at) VALUES ('p-shape', 'Shape', 'cat', 1)",
            0,
        )
        ToebeansDatabase.Schema.migrate(migrated, 6, 7)

        assertEquals(
            tableInfo(fresh),
            tableInfo(migrated),
            "Schema.create and migration 6 must produce the same Pet column shape and order",
        )
        assertEquals("ok", scalarText(migrated, "PRAGMA integrity_check"))
    }
}
