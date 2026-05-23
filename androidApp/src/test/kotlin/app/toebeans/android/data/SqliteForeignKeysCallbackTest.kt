package app.toebeans.android.data

import android.content.Context
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.data.db.probeForeignKeysEnabled
import app.toebeans.core.db.ToebeansDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ADR-0010 test contract for [SqliteForeignKeysCallback]. Robolectric exercises
 * SQLite-on-JVM with the same Android driver the app uses at runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SqliteForeignKeysCallbackTest {
    private lateinit var context: Context
    private lateinit var database: ToebeansDatabase
    private val dbName = "fk-callback-test.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
        database =
            DatabaseFactory(
                context = context,
                databaseName = dbName,
                callback = SqliteForeignKeysCallback(),
            ).create()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `fresh connection has foreign_keys pragma enabled`() {
        assertEquals(
            1L,
            probeForeignKeysEnabled(
                context = context,
                databaseName = dbName,
                callback = SqliteForeignKeysCallback(),
            ),
        )
    }

    @Test
    fun `deleting pet cascades medication schedule phase and dose event`() {
        seedCascadeChain(database)
        database.petQueries.deletePet("pet-cascade")
        assertCascadeTablesEmpty(database)
    }

    private fun seedCascadeChain(database: ToebeansDatabase) {
        val createdAt = 1_715_616_000_000L
        database.petQueries.upsertPet(
            id = "pet-cascade",
            name = "Cascade Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 5.0,
            notes = null,
            created_at = createdAt,
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = "med-cascade",
            pet_id = "pet-cascade",
            name = "Cascade Med",
            dose_amount = "1mg",
            notes = null,
            created_at = createdAt,
            discontinued_at = null,
        )
        database.scheduleQueries.upsertSchedule(
            id = "sched-cascade",
            medication_id = "med-cascade",
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = createdAt,
        )
        database.scheduleQueries.upsertSchedulePhase(
            id = "phase-cascade",
            schedule_id = "sched-cascade",
            phase_order = 0,
            duration_days = 7,
            doses_per_day = 1,
            dose_times_local = "[\"08:00\"]",
            dose_amount = "1mg",
        )
        database.doseEventQueries.insertDoseEvent(
            id = "dose-cascade",
            schedule_id = "sched-cascade",
            medication_id = "med-cascade",
            scheduled_at = createdAt,
            fired_at = null,
            resolved_at = null,
            status = "pending",
            note = null,
        )
    }

    private fun assertCascadeTablesEmpty(database: ToebeansDatabase) {
        assertTrue(
            database.medicationQueries
                .selectAllMedications()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            database.scheduleQueries
                .selectAllSchedules()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            database.scheduleQueries
                .selectAllSchedulePhases()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            database.doseEventQueries
                .selectAllDoseEvents()
                .executeAsList()
                .isEmpty(),
        )
    }
}
