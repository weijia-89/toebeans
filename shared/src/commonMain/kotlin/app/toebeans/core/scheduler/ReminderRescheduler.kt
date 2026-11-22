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

/** Inputs to [ReminderRescheduler.materializeHorizonForSchedule], grouped to keep the call signature small. */
public data class MaterializeHorizonRequest(
    val schedule: Schedule,
    val phases: List<SchedulePhase>,
    val medicationId: String,
    val doseEventRepository: DoseEventRepository,
    val scheduleCalculator: ScheduleCalculator,
    val timeZone: TimeZone,
    val now: Instant,
    val skipExistingIds: Set<String> = emptySet(),
)

/**
 * Projects pending [DoseEvent] rows for the next [HORIZON_HOURS] and returns
 * [ScheduledReminder] handles for [app.toebeans.core.notifications.NotificationActuator].
 *
 * Matches [docs/ARCHITECTURE.md] lazy 72h materialization. Call after schedule create
 * (or boot/app-open sweep in a follow-up slice).
 */
public object ReminderRescheduler {
    public const val HORIZON_HOURS: Int = 72
    public const val MISSED_DOSE_TIMEOUT_HOURS: Int = 4

    /**
     * Materialize pending dose rows in `[now, now + [HORIZON_HOURS])` and return reminders
     * to schedule with AlarmManager.
     *
     * Intended for **newly created** schedules (no prior dose rows). [DoseEventRepository.upsert]
     * will overwrite an existing row with the same id; callers editing an existing schedule
     * must rematerialize in a dedicated slice that preserves GIVEN/SKIPPED/MISSED rows.
     */
    public suspend fun materializeHorizonForSchedule(request: MaterializeHorizonRequest): List<ScheduledReminder> {
        val schedule = request.schedule
        val phases = request.phases
        val medicationId = request.medicationId
        val doseEventRepository = request.doseEventRepository
        val scheduleCalculator = request.scheduleCalculator
        val timeZone = request.timeZone
        val now = request.now
        val skipExistingIds = request.skipExistingIds
        val horizonEnd = now + HORIZON_HOURS.hours
        val doses =
            scheduleCalculator.computeScheduledDoses(
                schedule = schedule,
                phases = phases,
                timeZone = timeZone,
                fromInclusive = now,
                toExclusive = horizonEnd,
            )
        return doses.filterNot { doseEventIdForSlot(schedule.id, it.scheduledAt) in skipExistingIds }.map { dose ->
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
            // For new schedules, we don't have medication/pet names yet (they'll be fetched
            // at receiver fire time via SqlDelightReminderLookup JOIN). Use empty for now.
            ScheduledReminder(
                id = eventId,
                scheduleId = schedule.id,
                scheduledAt = dose.scheduledAt,
                medicationName = "",
                petName = "",
            )
        }
    }

    /** Stable id per (schedule, slot) so boot rehydration and receiver lookup stay aligned. */
    public fun doseEventIdForSlot(
        scheduleId: String,
        scheduledAt: Instant,
    ): String = "dose-$scheduleId-${scheduledAt.toEpochMilliseconds()}"
}
