package app.toebeans.core.scheduler

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.notifications.ScheduledReminder
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours

/**
 * Projects pending [DoseEvent] rows for the next [HORIZON_HOURS] and returns
 * [ScheduledReminder] handles for [app.toebeans.core.notifications.NotificationActuator].
 *
 * Matches [docs/ARCHITECTURE.md] lazy 72h materialization. Call after schedule create
 * (or boot/app-open sweep in a follow-up slice).
 */
public object ReminderRescheduler {
    public const val HORIZON_HOURS: Int = 72

    /**
     * Materialize pending dose rows in `[now, now + [HORIZON_HOURS])` and return reminders
     * to schedule with AlarmManager.
     *
     * Intended for **newly created** schedules (no prior dose rows). [DoseEventRepository.upsert]
     * will overwrite an existing row with the same id; callers editing an existing schedule
     * must rematerialize in a dedicated slice that preserves GIVEN/SKIPPED/MISSED rows.
     */
    public suspend fun materializeHorizonForSchedule(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        medicationId: String,
        doseEventRepository: DoseEventRepository,
        scheduleCalculator: ScheduleCalculator,
        timeZone: TimeZone,
        now: Instant,
    ): List<ScheduledReminder> {
        val horizonEnd = now + HORIZON_HOURS.hours
        val doses =
            scheduleCalculator.computeScheduledDoses(
                schedule = schedule,
                phases = phases,
                timeZone = timeZone,
                fromInclusive = now,
                toExclusive = horizonEnd,
            )
        return doses.map { dose ->
            val eventId = doseEventIdForSlot(schedule.id, dose.scheduledAt)
            doseEventRepository.upsert(
                DoseEvent(
                    id = eventId,
                    scheduleId = schedule.id,
                    medicationId = medicationId,
                    scheduledAt = dose.scheduledAt,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )
            ScheduledReminder(
                id = eventId,
                scheduleId = schedule.id,
                scheduledAt = dose.scheduledAt,
            )
        }
    }

    /** Stable id per (schedule, slot) so boot rehydration and receiver lookup stay aligned. */
    public fun doseEventIdForSlot(
        scheduleId: String,
        scheduledAt: Instant,
    ): String = "dose-$scheduleId-${scheduledAt.toEpochMilliseconds()}"
}
