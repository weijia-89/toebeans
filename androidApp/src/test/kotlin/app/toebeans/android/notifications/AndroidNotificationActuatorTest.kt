package app.toebeans.android.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.toebeans.core.notifications.ScheduledReminder
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [AndroidNotificationActuator]. Runs on a JVM via Robolectric's emulated
 * Android runtime — no device or emulator required.
 *
 * Test-as-spec for the medication-critical fire path. Per AGENTS.md:
 *  - These tests MUST be reviewed by a human before any change to AndroidNotificationActuator.
 *  - Mutation testing (deferred per ADR-0006) will eventually run against these assertions.
 *
 * SDK 33 chosen because POST_NOTIFICATIONS gained runtime-grant semantics there, which is the
 * trickiest fork in [AndroidNotificationActuator.show].
 *
 * Note: Robolectric exposes alarm trigger time + type via deprecated Java-side fields on
 * ScheduledAlarm. Migrating away requires Robolectric to publish a non-deprecated accessor;
 * for now we silence the warning at the file level. Tracked with the API-35 deprecation sweep.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidNotificationActuatorTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManagerCompat: NotificationManagerCompat
    private lateinit var systemNotificationManager: NotificationManager
    private lateinit var allocator: RequestCodeAllocator
    private lateinit var actuator: AndroidNotificationActuator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Each test gets a fresh prefs file so allocator state doesn't bleed across cases.
        // We achieve this by using a uniquely-named in-memory prefs the allocator owns.
        context
            .getSharedPreferences(RequestCodeAllocator.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManagerCompat = NotificationManagerCompat.from(context)
        systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        allocator = RequestCodeAllocator.fromContext(context)
        actuator =
            AndroidNotificationActuator(
                context = context,
                alarmManager = alarmManager,
                notificationManager = notificationManagerCompat,
                requestCodeAllocator = allocator,
            )
    }

    @Test
    fun `init creates the medication-critical notification channel`() {
        val channel =
            systemNotificationManager.getNotificationChannel(
                AndroidNotificationActuator.CHANNEL_ID_MEDICATION_CRITICAL,
            )
        assertNotNull("medication-critical channel must be created on construction", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
    }

    @Test
    fun `schedule registers an exact alarm at the expected time`() {
        val reminder =
            ScheduledReminder(
                id = "evt-1",
                scheduleId = "sched-1",
                scheduledAt = Instant.fromEpochMilliseconds(1_800_000_000_000L),
            )
        actuator.schedule(reminder)

        val scheduledAlarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals("exactly one alarm should be scheduled", 1, scheduledAlarms.size)
        val alarm = scheduledAlarms[0]
        assertEquals(1_800_000_000_000L, alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
    }

    @Test
    fun `schedule is idempotent — same id twice yields one alarm at the new time`() {
        val first = ScheduledReminder("evt-1", "sched-1", Instant.fromEpochMilliseconds(1_000_000L))
        val second = ScheduledReminder("evt-1", "sched-1", Instant.fromEpochMilliseconds(2_000_000L))

        actuator.schedule(first)
        actuator.schedule(second)

        val scheduledAlarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            "second schedule must replace the first, not add to it",
            1,
            scheduledAlarms.size,
        )
        assertEquals(2_000_000L, scheduledAlarms[0].triggerAtTime)
    }

    @Test
    fun `schedule with different ids produces independent alarms`() {
        actuator.schedule(ScheduledReminder("a", "s", Instant.fromEpochMilliseconds(1_000_000L)))
        actuator.schedule(ScheduledReminder("b", "s", Instant.fromEpochMilliseconds(2_000_000L)))

        val triggers = shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }.sorted()
        assertEquals(listOf(1_000_000L, 2_000_000L), triggers)
    }

    @Test
    fun `cancel removes the alarm for the given id`() {
        val reminder = ScheduledReminder("evt-1", "sched-1", Instant.fromEpochMilliseconds(1L))
        actuator.schedule(reminder)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        actuator.cancel(reminder.id)

        assertTrue(
            "cancelled alarm must be removed",
            shadowOf(alarmManager).scheduledAlarms.isEmpty(),
        )
    }

    @Test
    fun `cancel with an unknown id is a no-op (does not throw)`() {
        // Schedule one, then cancel a DIFFERENT id; the first must survive.
        actuator.schedule(ScheduledReminder("real", "s", Instant.fromEpochMilliseconds(1L)))
        actuator.cancel("phantom")
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `show posts a notification when notifications are enabled`() {
        val reminder = ScheduledReminder("evt-show", "sched-1", Instant.fromEpochMilliseconds(1L))
        actuator.show(reminder)

        val active = shadowOf(systemNotificationManager).activeNotifications
        assertEquals(1, active.size)
        val n = active[0]
        assertEquals(
            "notification id must be reminder id hash for cancellability",
            reminder.id.hashCode(),
            n.id,
        )
        assertEquals(
            AndroidNotificationActuator.CHANNEL_ID_MEDICATION_CRITICAL,
            n.notification.channelId,
        )
    }

    @Test
    fun `show with the same id replaces the prior notification`() {
        val r1 = ScheduledReminder("evt-1", "sched-1", Instant.fromEpochMilliseconds(1L))
        actuator.show(r1)
        actuator.show(r1)

        val active = shadowOf(systemNotificationManager).activeNotifications
        assertEquals("re-showing same id must not stack", 1, active.size)
    }

    @Test
    fun `re-scheduling after cancel works correctly`() {
        val reminder = ScheduledReminder("evt-1", "sched-1", Instant.fromEpochMilliseconds(1L))
        actuator.schedule(reminder)
        actuator.cancel(reminder.id)
        actuator.schedule(reminder.copy(scheduledAt = Instant.fromEpochMilliseconds(99_000_000L)))

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(99_000_000L, alarms[0].triggerAtTime)
    }

    /**
     * Regression for the PendingIntent collision class. "Aa" and "BB" are the canonical
     * minimal pair of Java strings with colliding hashCodes (both 2112). Under the previous
     * `reminderId.hashCode()` scheme, scheduling the second alarm silently overwrote the
     * first. The allocator must give them distinct request codes so both alarms survive.
     */
    @Test
    fun `regression — reminders with colliding hashCodes both schedule successfully`() {
        // Sanity check: pre-condition this regression test depends on. If Kotlin's String
        // hashing ever diverges from Java's, this test should fail loudly so we re-pick the
        // collision pair.
        assertEquals(
            "test depends on Aa and BB having equal hashCodes (Java spec)",
            "Aa".hashCode(),
            "BB".hashCode(),
        )

        val a = ScheduledReminder("Aa", "sched-a", Instant.fromEpochMilliseconds(1_000_000L))
        val b = ScheduledReminder("BB", "sched-b", Instant.fromEpochMilliseconds(2_000_000L))
        actuator.schedule(a)
        actuator.schedule(b)

        val triggers = shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }.sorted()
        assertEquals(
            "both colliding-hash reminders must produce independent alarms",
            listOf(1_000_000L, 2_000_000L),
            triggers,
        )

        // And the allocator must have given them distinct codes — defends against a future
        // refactor that "helpfully" falls back to hashing.
        val codeA = allocator.peek("Aa")
        val codeB = allocator.peek("BB")
        assertNotNull("Aa must be assigned a code after schedule()", codeA)
        assertNotNull("BB must be assigned a code after schedule()", codeB)
        assertTrue("colliding-hash ids must receive distinct codes", codeA != codeB)
    }
}
