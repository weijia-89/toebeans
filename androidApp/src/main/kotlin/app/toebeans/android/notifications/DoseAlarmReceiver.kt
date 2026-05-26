package app.toebeans.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import app.toebeans.android.ToebeansApp
import app.toebeans.core.data.SqlDelightDoseEventRepository
import app.toebeans.core.notifications.ReminderLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * BroadcastReceiver fired by AlarmManager when a dose's scheduled time arrives.
 *
 * Vibe-dangerous: the dispatch logic here is the medication-critical fire path. Any change
 * requires the test-as-spec protocol in AGENTS.md.
 *
 * Lifecycle:
 *  1. AlarmManager triggers this receiver's [onReceive] at the wall-clock instant.
 *  2. [ReminderLookup.lookup] re-fetches authoritative reminder data from SQLDelight.
 *  3. We stamp [DoseEvent.fired_at] in SQLDelight (ADR-0011 write-before-show).
 *  4. We delegate to [AndroidNotificationActuator.show], which populates the visible notification.
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

        /**
         * Test seam for ADR-0011 [DoseEvent.fired_at] writes. Production uses SQLDelight on the
         * receiver database when null.
         */
        @VisibleForTesting
        @JvmField
        public var firedAtWriterOverride: ((doseEventId: String, firedAt: Instant) -> Unit)? = null

        /**
         * Test seam invoked immediately after [markDoseEventFired] and before [AndroidNotificationActuator.show].
         */
        @VisibleForTesting
        @JvmField
        public var beforeShowHook: (() -> Unit)? = null

        internal fun reminderLookupFor(context: Context): ReminderLookup =
            lookupOverride ?: defaultReminderLookup(context)

        /**
         * Production lookup: opens SQLDelight directly in the receiver process (outside Koin).
         *
         * F3: [AppModule] writes dose rows via [SqlDelightDoseEventRepository] on the shared
         * `toebeans.db` file; this lookup reads the same file. Callers must INSERT before
         * [NotificationActuator.schedule] so the row exists at fire time.
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
            markDoseEventFired(context, reminderId)
            beforeShowHook?.invoke()
            notificationActuatorFor(context).show(reminder)
        }

        internal fun markDoseEventFired(
            context: Context,
            doseEventId: String,
        ) {
            val firedAt = Clock.System.now()
            val writer = firedAtWriterOverride
            if (writer != null) {
                writer(doseEventId, firedAt)
            } else {
                SqlDelightDoseEventRepository(
                    database = ToebeansApp.openReceiverDatabase(context),
                    dispatcher = Dispatchers.Unconfined,
                ).markFired(doseEventId, firedAt)
            }
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
