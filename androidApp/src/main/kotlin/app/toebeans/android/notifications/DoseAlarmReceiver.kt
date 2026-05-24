package app.toebeans.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import app.toebeans.android.ToebeansApp
import app.toebeans.core.notifications.ReminderLookup

/**
 * BroadcastReceiver fired by AlarmManager when a dose's scheduled time arrives.
 *
 * Vibe-dangerous: the dispatch logic here is the medication-critical fire path. Any change
 * requires the test-as-spec protocol in AGENTS.md.
 *
 * Lifecycle:
 *  1. AlarmManager triggers this receiver's [onReceive] at the wall-clock instant.
 *  2. [ReminderLookup.lookup] re-fetches authoritative reminder data from SQLDelight.
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

        val appContext = context.applicationContext
        dispatchDoseFire(appContext, reminderId, reminderLookupFor(appContext))
    }

    public companion object {
        /**
         * Test seam for [ReminderLookup]. Production uses [defaultReminderLookup] when null.
         */
        @VisibleForTesting
        @JvmField
        public var lookupOverride: ReminderLookup? = null

        internal fun reminderLookupFor(context: Context): ReminderLookup =
            lookupOverride ?: defaultReminderLookup(context)

        /**
         * Production lookup: opens SQLDelight directly in the receiver process (outside Koin).
         *
         * sdk-review F1: requires a persisted SQLDelight dose row; [AppModule] still binds
         * FakeDoseEventRepository — alarms must be scheduled only after DB materialization.
         */
        internal fun defaultReminderLookup(context: Context): ReminderLookup =
            ToebeansApp.reminderLookupForReceiver(context)

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
