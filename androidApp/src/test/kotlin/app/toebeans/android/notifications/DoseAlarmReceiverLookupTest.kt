package app.toebeans.android.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import app.toebeans.core.notifications.ReminderLookup
import app.toebeans.core.notifications.ScheduledReminder
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Test-as-spec for [DoseAlarmReceiver] reminder lookup (M1 ROADMAP item 4 prep).
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
    }

    @After
    fun tearDown() {
        DoseAlarmReceiver.lookupOverride = null
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
    fun `placeholder lookup preserves pre-SQLDelight scheduleId until M1_3`() {
        // Documents current production default: separate-process receiver cannot see fakes;
        // PlaceholderReminderLookup keeps legacy empty scheduleId rather than crashing.
        val lookup = DoseAlarmReceiver.defaultReminderLookup()
        val found =
            lookup.lookup("evt-placeholder") ?: error("placeholder lookup must return a row")

        assertEquals("evt-placeholder", found.id)
        assertEquals("", found.scheduleId)
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
