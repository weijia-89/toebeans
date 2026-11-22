package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.HomeTimezone
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Round-trip and quarantine behavior for the persisted [Pet.homeTimezone] value. */
class HomeTimezoneRepositoryTest {
    private fun newDatabase(): Pair<SqlDriver, ToebeansDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        return driver to ToebeansDatabase(driver)
    }

    private fun repo(db: ToebeansDatabase) = SqlDelightPetRepository(database = db, dispatcher = Dispatchers.Unconfined)

    private fun pet(zoneId: String?) =
        Pet(
            id = "p-tz",
            name = "TZ Pet",
            species = Species.CAT,
            birthdate = null,
            weightKg = null,
            notes = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            archivedAt = null,
            homeTimezone = zoneId,
        )

    @Test
    fun `home timezone round trips through upsert and can be cleared to null`() =
        runTest {
            val (_, db) = newDatabase()
            val repository = repo(db)

            repository.upsert(pet("America/New_York"))
            assertEquals("America/New_York", repository.getById("p-tz")?.homeTimezone)

            repository.upsert(pet(null))
            assertNull(repository.getById("p-tz")?.homeTimezone)
        }

    @Test
    fun `invalid stored id is kept verbatim and reads back as quarantined`() =
        runTest {
            val (driver, db) = newDatabase()
            val repository = repo(db)

            // Backup-import shape: the raw value lands in storage without a device-zone
            // fallback. Quarantine is derived from the persisted value, so it survives
            // process restarts.
            driver.execute(
                null,
                """
                INSERT INTO Pet (id, name, species, created_at, home_timezone)
                VALUES ('p-bad', 'Imported Pet', 'dog', 1, 'Mars/Olympus_Mons')
                """.trimIndent(),
                0,
            )

            val imported = repository.getById("p-bad")!!
            assertEquals("Mars/Olympus_Mons", imported.homeTimezone)
            assertTrue(HomeTimezone.isQuarantined(imported))
            assertEquals(
                "Mars/Olympus_Mons",
                driver
                    .executeQuery(
                        identifier = null,
                        sql = "SELECT home_timezone FROM Pet WHERE id = 'p-bad'",
                        mapper = { cursor ->
                            app.cash.sqldelight.db.QueryResult.Value(
                                if (cursor.next().value) cursor.getString(0) else null,
                            )
                        },
                        parameters = 0,
                    ).value,
                "quarantine must never rewrite the stored id",
            )
        }
}
