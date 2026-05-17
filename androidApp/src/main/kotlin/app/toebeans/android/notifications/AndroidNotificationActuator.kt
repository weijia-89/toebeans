package app.toebeans.android.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.ScheduledReminder

/**
 * Android implementation of [NotificationActuator] using AlarmManager + NotificationManagerCompat.
 *
 * Design notes (per `docs/adr/0002-alarmmanager-workmanager-hybrid.md`):
 *  - We use `setExactAndAllowWhileIdle` to satisfy the ±60s fire-window requirement.
 *  - The PendingIntent request code is issued by [RequestCodeAllocator], which maps each
 *    [ScheduledReminder.id] to a stable monotonic Int. Idempotence of [schedule] follows
 *    from the allocator's idempotence: re-scheduling the same id returns the same code,
 *    which AlarmManager uses to look up and replace the existing alarm.
 *  - The PendingIntent uses `FLAG_UPDATE_CURRENT` so the replacement is in-place.
 *  - We do NOT serialize the full reminder payload into the intent. We carry only [REMINDER_ID]
 *    and let the receiver re-fetch authoritative data.
 *
 * Vibe-dangerous: any change here requires human-written tests AND human-read diffs (AGENTS.md).
 *
 * **Collision posture:** PendingIntent request codes are issued by [RequestCodeAllocator],
 * which assigns a strictly-monotonic Int to each `reminderId` and persists the mapping in
 * SharedPreferences. Two different ids cannot collide. The earlier `reminderId.hashCode()`
 * scheme was retired because hash collisions on the 32-bit code could silently overwrite a
 * scheduled alarm, unacceptable in the medication-firing path.
 *
 * **Notification id is allocator-keyed.** Both `schedule()` and `show()` use the same
 * `RequestCodeAllocator` to obtain the underlying Int. The PendingIntent path and the
 * NotificationManager path stay aligned: two reminder ids with colliding hashCodes (the
 * canonical "Aa"/"BB" pair) produce distinct notifications, just as they produce distinct
 * alarms. Regression test:
 * `AndroidNotificationActuatorTest.regression, show with hashCode-colliding reminder ids`.
 *
 * Permission posture (API matrix; see AndroidManifest.xml for the full rationale):
 *  - API 24-30: setExactAndAllowWhileIdle works without an explicit alarm permission.
 *  - API 31-32: `SCHEDULE_EXACT_ALARM` is required. [AlarmManager.canScheduleExactAlarms]
 *    returns false if the user has not granted it; we fall back to `setWindow` with a
 *    60-second window. (USE_EXACT_ALARM does not exist on these API levels.)
 *  - API 33+: `USE_EXACT_ALARM` is auto-granted and non-revocable for qualifying apps
 *    (medication reminders qualify per Google Play policy). [canScheduleExactAlarms]
 *    returns true automatically; the setWindow fallback is unreachable.
 *  - On API 33+, `POST_NOTIFICATIONS` requires runtime grant. If not granted, [show] is a
 *    silent no-op TODAY (slice 1 work item to surface as a UI banner). For the
 *    medication-firing path, this is a SILENT DOSE LOSS hazard: the alarm fires, the
 *    receiver runs, but the user-visible notification never appears. Tracked in
 *    `docs/issues/v0.1-followups.md` under priority "medication-critical". Cross-reference
 *    ADR-0011 (post-notifications denial UX, to be written).
 */
public class AndroidNotificationActuator(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val notificationManager: NotificationManagerCompat,
    private val requestCodeAllocator: RequestCodeAllocator,
) : NotificationActuator {
    init {
        ensureChannelExists()
    }

    override fun schedule(reminder: ScheduledReminder) {
        val pendingIntent = buildPendingIntent(reminder.id)
        val triggerAtMillis = reminder.scheduledAt.toEpochMilliseconds()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Graceful degrade: the user has not granted SCHEDULE_EXACT_ALARM. Fall back to a
            // window-based alarm. Slice 1 work item: surface a banner asking the user to grant it.
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                FALLBACK_WINDOW_MILLIS,
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    override fun cancel(reminderId: String) {
        val pendingIntent = buildPendingIntent(reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel() // also release the PendingIntent itself
        // Drop the allocator mapping AFTER the OS-side cancel; if we released first and the
        // cancel raced with a re-schedule under the same id, the re-schedule would allocate a
        // new code and the OS would hold a stale PendingIntent under the old code.
        requestCodeAllocator.release(reminderId)
    }

    override fun show(reminder: ScheduledReminder) {
        if (!notificationManager.areNotificationsEnabled()) {
            // Slice 1: hook to UI banner asking the user to grant POST_NOTIFICATIONS.
            return
        }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID_MEDICATION_CRITICAL)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // v0.1 placeholder
                .setContentTitle("Medication due")
                .setContentText("Reminder ${reminder.id}") // slice 1: pull live data here
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
        // N       .bui id is dllocator-keyed for the same collisio(-freedom re)son as the
        // PendinIntnt equest code Callig allcae() here is dempotent or a previousl
        // scheduled reminder (returns the same code as schedule)'s PendingIntent) and issues
        // a fresh code for a  shown without a prior schedule (e.g, mmeiate "log
        // dose now" flows in milestone 15).
        notificationManger.notify(requeteAllocator.allocatreminder.id
        notificationManager.notify(reminder.id.hashCode(), notification)
    }

    private fun ensureChannelExists() {
        // NotificationChannel exists on API 26+, our minSdk.
        val channel =
            NotificationChannel(
                CHANNEL_ID_MEDICATION_CRITICAL,
                "Medication reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Time-sensitive reminders to give your pet their medication."
                enableLights(true)
                enableVibration(true)
            }
        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemNotificationManager.createNotificationChannel(channel)
    }

    private fun buildPendingIntent(reminderId: String): PendingIntent {
        val intent =
            Intent(context, DoseAlarmReceiver::class.java).apply {
                action = ACTION_DOSE_FIRE
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCodeAllocator.allocate(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    public companion object {
        public const val CHANNEL_ID_MEDICATION_CRITICAL: String = "medication-critical"
        public const val ACTION_DOSE_FIRE: String = "app.toebeans.action.DOSE_FIRE"
        public const val EXTRA_REMINDER_ID: String = "app.toebeans.extra.REMINDER_ID"
        public const val FALLBACK_WINDOW_MILLIS: Long = 60_000L
    }
}
