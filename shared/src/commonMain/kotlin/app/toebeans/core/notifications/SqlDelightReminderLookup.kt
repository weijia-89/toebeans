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
 * **Discontinued / archived.** [lookup] uses `selectEnrichedReminderById` which filters out
 * soft-discontinued medications and archived pets so they don't surface at fire time.
 *
 * **Enriched lookup.** Returns medication and pet names for notification display, avoiding
 * a separate lookup at notification-build time.
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
                .selectEnrichedReminderById(reminderId)
                .executeAsOneOrNull()
            ?: return null

        return ScheduledReminder(
            id = row.id,
            scheduleId = row.schedule_id,
            scheduledAt = Instant.fromEpochMilliseconds(row.scheduled_at),
            medicationName = row.medication_name,
            petName = row.pet_name,
        )
    }
}
