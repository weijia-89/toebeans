package app.toebeans.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
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
 *  2. We re-fetch the authoritative reminder data (slice 1: actually do that — at v0.1 we
 *     surface a placeholder).
 *  3. We delegate to [AndroidNotificationActuator.show], which populates the visible notification.
 *  4. We mark the DoseEvent.fired_at in the database (slice 1 work item; requires DB injection here).
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

        // Slice 1: replace this hand-built ScheduledReminder with a DB lookup that returns
        // the current state. The actuator's `show` contract permits (and recommends) re-fetch.
        val placeholder =
            ScheduledReminder(
                id = reminderId,
                scheduleId = "",
                scheduledAt = Clock.System.now(),
            )

        val notificationManager = NotificationManagerCompat.from(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val actuator =
            AndroidNotificationActuator(
                context = context,
                alarmManager = alarmManager,
                notificationManager = notificationManager,
            )
        actuator.show(placeholder)
    }
}
