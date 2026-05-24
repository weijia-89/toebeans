package app.toebeans.android.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import app.toebeans.android.ToebeansApp
import app.toebeans.android.data.SqliteForeignKeysCallback
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.notifications.ReminderLookup
import app.toebeans.core.notifications.ScheduledReminder
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Test-as-spec for [DoseAlarmReceiver] reminder lookup (M1 ROADMAP item 4).
 *
 * **Contract (followups § 3):**
 * - Happy path: [ReminderLookup] returns a [ScheduledReminder] → [AndroidNotificationActuator.show]
 *   posts a user-visible notification.
 * - Row gone: [ReminderLookup] returns null → receiver silently cancels the pending alarm, no crash,
 *   no notification.
 *
 * **ADR-0011 (deferred):** `DoseEvent.fired_at` write-before-show and LocalCrashLog markers for
 * [AndroidNotificationActuator.show] permission denial are not asserted here; they ship with the
 * ADR-0011 wire-up PR once SQLDelight is reachable in the receiver process.
 *
 * Per AGENTS.md vibe-dangerous protocol: human review of assertions before extending coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DoseAlarmReceiverLookupTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var systemNotificationManager: NotificationManager
    private lateinit var database: ToebeansDatabase
    private val dbName = "dose-receiver-lookup-test.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(RequestCodeAllocator.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        DoseAlarmReceiver.lookupOverride = null
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
        DoseAlarmReceiver.lookupOverride = null
        ToebeansApp.resetReceiverDatabaseCacheForTests()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `happy path posts notification when lookup returns reminder`() {
        val reminder =
            ScheduledReminder(
                id = "evt-happy",
                scheduleId = "sched-luna-methimazole",
                scheduledAt = Instant.parse("2026-05-23T08:00:00Z"),
            )
        DoseAlarmReceiver.lookupOverride =
            object : ReminderLookup {
                override fun lookup(reminderId: String): ScheduledReminder? = reminder
            }

        dispatchDoseFire(reminder.id)

        val active = shadowOf(systemNotificationManager).activeNotifications
        assertEquals(1, active.size)
        assertEquals(
            AndroidNotificationActuator.CHANNEL_ID_MEDICATION_CRITICAL,
            active[0].notification.channelId,
        )
    }

    @Test
    fun `row gone silently cancels alarm without posting notification`() {
        val reminder =
            ScheduledReminder(
                id = "evt-gone",
                scheduleId = "sched-deleted",
                scheduledAt = Instant.parse("2026-05-23T09:00:00Z"),
            )
        val actuator =
            AndroidNotificationActuator(
                context = context,
                alarmManager = alarmManager,
                notificationManager =
                    androidx.core.app.NotificationManagerCompat
                        .from(context),
                requestCodeAllocator = RequestCodeAllocator.fromContext(context),
            )
        actuator.schedule(reminder)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        DoseAlarmReceiver.lookupOverride =
            object : ReminderLookup {
                override fun lookup(reminderId: String): ScheduledReminder? = null
            }

        dispatchDoseFire(reminder.id)

        assertEquals(
            "row-gone path must cancel the stale alarm",
            0,
            shadowOf(alarmManager).scheduledAlarms.size,
        )
        assertEquals(
            "row-gone path must not post a notification",
            0,
            shadowOf(systemNotificationManager).activeNotifications.size,
        )
    }

    @Test
    fun `onReceive ignores unrelated actions`() {
        DoseAlarmReceiver.lookupOverride =
            object : ReminderLookup {
                override fun lookup(reminderId: String): ScheduledReminder? =
                    error("lookup must not run for unrelated actions")
            }
        val receiver = DoseAlarmReceiver()
        receiver.onReceive(context, Intent("app.toebeans.action.UNRELATED"))
    }

    @Test
    fun `onReceive ignores intents missing reminder id extra`() {
        DoseAlarmReceiver.lookupOverride =
            object : ReminderLookup {
                override fun lookup(reminderId: String): ScheduledReminder? =
                    error("lookup must not run when reminder id extra is absent")
            }
        val receiver = DoseAlarmReceiver()
        val intent =
            Intent(context, DoseAlarmReceiver::class.java).apply {
                action = AndroidNotificationActuator.ACTION_DOSE_FIRE
            }
        receiver.onReceive(context, intent)
    }

    @Test
    fun `default lookup resolves persisted dose event with non-empty scheduleId`() {
        seedDoseEvent(
            eventId = "evt-sqldelight",
            scheduleId = "sched-luna-methimazole",
            scheduledAt = Instant.parse("2026-05-23T08:00:00Z"),
        )

        val found = DoseAlarmReceiver.defaultReminderLookup(context).lookup("evt-sqldelight")

        assertEquals(
            ScheduledReminder(
                id = "evt-sqldelight",
                scheduleId = "sched-luna-methimazole",
                scheduledAt = Instant.parse("2026-05-23T08:00:00Z"),
            ),
            found,
        )
    }

    @Test
    fun `onReceive uses SQLDelight lookup to post notification for persisted dose event`() {
        seedDoseEvent(
            eventId = "evt-on-receive",
            scheduleId = "sched-luna-methimazole",
            scheduledAt = Instant.parse("2026-05-23T10:00:00Z"),
        )

        dispatchDoseFire("evt-on-receive")

        val active = shadowOf(systemNotificationManager).activeNotifications
        assertEquals(1, active.size)
    }

    @Test
    fun `default lookup returns null after schedule delete cascades dose event`() {
        seedDoseEvent(
            eventId = "evt-cascade-gone",
            scheduleId = "sched-luna-methimazole",
            scheduledAt = Instant.parse("2026-05-23T11:00:00Z"),
        )
        database.scheduleQueries.deleteSchedule("sched-luna-methimazole")

        val found = DoseAlarmReceiver.defaultReminderLookup(context).lookup("evt-cascade-gone")

        assertNull(found)
    }

    private fun seedDoseEvent(
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

    private fun dispatchDoseFire(reminderId: String) {
        val receiver = DoseAlarmReceiver()
        val intent =
            Intent(context, DoseAlarmReceiver::class.java).apply {
                action = AndroidNotificationActuator.ACTION_DOSE_FIRE
                putExtra(AndroidNotificationActuator.EXTRA_REMINDER_ID, reminderId)
            }
        receiver.onReceive(context, intent)
    }
}
