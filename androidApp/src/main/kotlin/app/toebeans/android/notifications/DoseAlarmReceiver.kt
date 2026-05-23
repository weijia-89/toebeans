package app.toebeans.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import app.toebeans.core.notifications.ReminderLookup
import app.toebeans.core.notifications.ScheduledReminder
import kotlinx.datetime.Clock

/**
 * BroadcastReceiver fired by AlarmManager when a dose's scheduled time arrives.
 *
 * Vibe-dangerous: the dispatch logic here is the medication-critical fire path. Any change
 * requires the test-as-spec protocol in AGENTS.md.
 *
 * Lifecycle:
 *  1. AlarmManager triggers this receiver's [onReceive] at the wall-clock instant.
 *  2. [ReminderLookup.lookup] re-fetches authoritative reminder data (SQLDelight in M1.3).
 *  3. We delegate to [AndroidNotificationActuator.show], which populates the visible notification.
 *  4. We mark the DoseEvent.fired_at in the database (ADR-0011 slice; requires SQLDelight here).
 */
public class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != AndroidNotificationActuator.ACTION_DOSE_FIRE) {
            return
        }
        val reminderId =
            intent.getStringExtra(AndroidNotificationActuator.EXTRA_REMINDER_ID)
                ?: return

        dispatchDoseFire(context.applicationContext, reminderId, reminderLookupFor())
    }

    public companion object {
        /**
         * Test seam for [ReminderLookup]. Production uses [defaultReminderLookup] when null.
         */
        @VisibleForTesting
        @JvmField
        public var lookupOverride: ReminderLookup? = null

        internal fun reminderLookupFor(): ReminderLookup = lookupOverride ?: defaultReminderLookup()

        /**
         * Production lookup until M1.3 wires SQLDelight in the receiver process. Preserves the
         * legacy placeholder row (empty [ScheduledReminder.scheduleId]) so alarms keep firing
         * without crashing while persistence is still fake-only in the foreground app.
         */
        internal fun defaultReminderLookup(): ReminderLookup = PlaceholderReminderLookup()

        /**
         * Shared dispatch entry for Robolectric tests and production [onReceive].
         */
        internal fun dispatchDoseFire(
            context: Context,
            reminderId: String,
            lookup: ReminderLookup,
        ) {
            val reminder = lookup.lookup(reminderId)
            if (reminder == null) {
                notificationActuatorFor(context).cancel(reminderId)
                return
            }
            notificationActuatorFor(context).show(reminder)
        }

        private fun notificationActuatorFor(context: Context): AndroidNotificationActuator {
            val notificationManager = NotificationManagerCompat.from(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            return AndroidNotificationActuator(
                context = context,
                alarmManager = alarmManager,
                notificationManager = notificationManager,
                requestCodeAllocator = RequestCodeAllocator.fromContext(context),
            )
        }
    }
}

/**
 * Pre-M1.3 lookup: returns a synthetic [ScheduledReminder] with empty [ScheduledReminder.scheduleId].
 * Replaced by driver-backed lookup once SQLDelight is reachable from the receiver process.
 */
private class PlaceholderReminderLookup : ReminderLookup {
    override fun lookup(reminderId: String): ScheduledReminder =
        ScheduledReminder(
            id = reminderId,
            scheduleId = "",
            scheduledAt = Clock.System.now(),
        )
}
