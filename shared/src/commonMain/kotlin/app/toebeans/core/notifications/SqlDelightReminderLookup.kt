package app.toebeans.core.notifications

import app.toebeans.core.db.ToebeansDatabase
import kotlinx.datetime.Instant

/**
 * SQLDelight-backed [ReminderLookup] for the receiver fire path (M1.3).
 *
 * Opens the authoritative [ToebeansDatabase] directly (outside Koin) and maps a persisted
 * dose-event id to the [ScheduledReminder] snapshot [NotificationActuator.show] needs.
 *
 * **Row-gone race.** Schedule delete cascades to dose events (ADR-0010); [lookup] returns null
 * so [app.toebeans.android.notifications.DoseAlarmReceiver] can silently cancel the stale alarm.
 *
 * **ADR-0011 write path (deferred).** Stamping `DoseEvent.fired_at` before [NotificationActuator.show]
 * is a separate wire-up slice; this class covers the read side only.
 */
public class SqlDelightReminderLookup(
    private val database: ToebeansDatabase,
) : ReminderLookup {
    override fun lookup(reminderId: String): ScheduledReminder? {
        val row =
            database.doseEventQueries
                .selectDoseEventById(reminderId)
                .executeAsOneOrNull()
                ?: return null

        // Defense in depth: confirm the parent schedule row still exists so scheduleId is
        // non-empty and authoritative (CASCADE should have removed the dose event already).
        val schedule =
            database.scheduleQueries
                .selectScheduleById(row.schedule_id)
                .executeAsOneOrNull()
                ?: return null

        return ScheduledReminder(
            id = row.id,
            scheduleId = schedule.id,
            scheduledAt = Instant.fromEpochMilliseconds(row.scheduled_at),
        )
    }
}
