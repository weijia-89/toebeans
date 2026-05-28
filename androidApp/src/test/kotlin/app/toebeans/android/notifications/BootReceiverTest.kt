package app.toebeans.android.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import app.toebeans.android.ToebeansApp
import app.toebeans.android.data.SqliteForeignKeysCallback
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.db.ToebeansDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.hours

/**
 * Test-as-spec for [BootReceiver] (M1 ROADMAP sequencing item 5).
 *
 * **Contract:**
 * After [Intent.ACTION_BOOT_COMPLETED], the receiver rehydrates the 72-hour alarm horizon
 * from SQLDelight: query upcoming pending [DoseEvent] rows, call [AndroidNotificationActuator.schedule]
 * for each, and log replay outcome (ADR-0012 `BOOT_REPLAY_OK` marker; LocalCrashLog append
 * deferred until that API exists).
 *
 * **Phase 3 (shipped):**
 * - [BootReceiver.onReceive] delegates to [app.toebeans.android.ToebeansApp.rehydrateBootAlarms].
 * - [app.toebeans.android.ToebeansApp.loadPendingRemindersInHorizon] queries
 *   `selectPendingDoseEventsInRange` on the receiver-process SQLDelight database.
 * - Empty DB → zero alarms, no crash. Seeded pending rows inside the horizon → AlarmManager
 *   entries scheduled.
 *
 * **Follow-on slices (not this PR):**
 * - ADR-0012 disabled-by-default lifecycle (enable after first dose schedule).
 * - LocalCrashLog non-crash append for BOOT_REPLAY_OK.
 *
 * Per AGENTS.md vibe-dangerous protocol: human review of assertions before extending coverage.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var database: ToebeansDatabase
    private val dbName = "boot-receiver-test.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(RequestCodeAllocator.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ToebeansApp.resetReceiverDatabaseCacheForTests()
        context.deleteDatabase(dbName)
        database =
            DatabaseFactory(
                context = context,
                databaseName = dbName,
                callback = SqliteForeignKeysCallback(),
            ).create()
        ToebeansApp.receiverDatabaseFactory = { database }
    }

    @After
    fun tearDown() {
        ToebeansApp.resetReceiverDatabaseCacheForTests()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `manifest declares RECEIVE_BOOT_COMPLETED permission`() {
        val manifest = mainManifestText()
        assertTrue(
            "BootReceiver consumer must declare RECEIVE_BOOT_COMPLETED",
            manifest.contains("android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\""),
        )
    }

    @Test
    fun `manifest registers BootReceiver for BOOT_COMPLETED`() {
        val manifest = mainManifestText()
        assertTrue(
            "BootReceiver must be declared in AndroidManifest",
            manifest.contains("android:name=\".notifications.BootReceiver\""),
        )
        assertTrue(
            "BootReceiver intent-filter must include ACTION_BOOT_COMPLETED",
            manifest.contains("android:name=\"android.intent.action.BOOT_COMPLETED\""),
        )
    }

    @Test
    fun `onReceive with BOOT_COMPLETED does not crash with empty database`() {
        dispatchBootCompleted()
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `onReceive with BOOT_COMPLETED schedules alarms for pending dose events in horizon`() {
        val scheduledAt = Clock.System.now() + 12.hours
        seedPendingDoseEvent(
            eventId = "evt-boot-rehydrate",
            scheduleId = "sched-luna-methimazole",
            scheduledAt = scheduledAt,
        )

        dispatchBootCompleted()

        val scheduledAlarms = shadowOf(alarmManager).scheduledAlarms
        assertTrue(
            "Boot rehydration must schedule AlarmManager entries for pending doses in the 72h window",
            scheduledAlarms.size > 0,
        )
        assertEquals(1, scheduledAlarms.size)
        assertEquals(scheduledAt.toEpochMilliseconds(), scheduledAlarms[0].triggerAtTime)
    }

    @Test
    fun `onReceive ignores unrelated actions`() {
        seedPendingDoseEvent(
            eventId = "evt-unrelated-action",
            scheduleId = "sched-luna-methimazole",
            scheduledAt = Clock.System.now() + 12.hours,
        )
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent("app.toebeans.action.UNRELATED"))
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }

    private fun dispatchBootCompleted() {
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    private fun seedPendingDoseEvent(
        eventId: String,
        scheduleId: String,
        scheduledAt: Instant,
    ) {
        val createdAt = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
        database.petQueries.upsertPet(
            id = "pet-luna",
            name = "Luna",
            species = "cat",
            birthdate_iso = null,
            weight_kg = 4.0,
            notes = null,
            created_at = createdAt,
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = "med-luna-methimazole",
            pet_id = "pet-luna",
            name = "Methimazole",
            dose_amount = "2.5mg",
            notes = null,
            created_at = createdAt,
            discontinued_at = null,
        )
        database.scheduleQueries.upsertSchedule(
            id = scheduleId,
            medication_id = "med-luna-methimazole",
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = createdAt,
        )
        database.doseEventQueries.insertDoseEvent(
            id = eventId,
            schedule_id = scheduleId,
            medication_id = "med-luna-methimazole",
            scheduled_at = scheduledAt.toEpochMilliseconds(),
            fired_at = null,
            resolved_at = null,
            status = "pending",
            note = null,
        )
    }

    private fun mainManifestText(): String {
        val candidates =
            listOf(
                File("src/main/AndroidManifest.xml"),
                File("androidApp/src/main/AndroidManifest.xml"),
            )
        val manifest =
            candidates.firstOrNull { it.isFile }
                ?: error(
                    "Could not locate AndroidManifest.xml from ${System.getProperty("user.dir")}",
                )
        return manifest.readText()
    }
}
