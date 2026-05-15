package app.toebeans.core.scheduler

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days

/**
 * Default implementation of [ScheduleCalculator]. Pure-functional; no I/O, no platform clock,
 * no randomness. Same inputs → same outputs.
 *
 * Algorithm (linear in output size):
 *   1. Validate the window and the phase ordering. Throw the appropriate
 *      [MalformedScheduleException] subclass on any violation — fail loud, never silent.
 *   2. Compute the schedule's effective inclusive end date as
 *      `min(schedule.endDate, startDate + sum(durationDays) - 1)`.
 *      Empty-result fast paths (schedule has not started, schedule already over) return early.
 *   3. Walk calendar days from `schedule.startDate` to `effectiveEnd`, advancing through phases
 *      in `phaseOrder` order. On each calendar day, if the day-within-phase index is divisible
 *      by [SchedulePhase.dayInterval], emit one [ScheduledDose] per local time in
 *      [SchedulePhase.doseTimesLocal].
 *   4. Convert each `(date, localTime)` to an [Instant] via the supplied [TimeZone] and keep
 *      only those falling inside `[fromInclusive, toExclusive)`.
 *   5. Defense-in-depth size check (should be unreachable given the per-field caps).
 *
 * Honors:
 *   - ADR-0004 § Test-as-spec review (D1 endDate-inclusive, D2 midnight anchor, D3 throws,
 *     D4 empty-phases empty-result, D5 not-yet-started empty-result, D6 caller-supplied TZ,
 *     D7 name, F5 global sort).
 *   - ADR-0007 v0.1 TZ behavior (FOLLOW_PHONE; DST handling deferred to a follow-up).
 *   - ADR-0008 mechanical bounds: window ≤ 30 days, event count ≤ 100,000.
 *
 * Not yet honored (deferred):
 *   - ADR-0007 anchor modes (`STAY_HOME_TZ`, `ELAPSED_INTERVAL`) — milestone 1.5.
 *   - DST detection (`DST_SKIP` / `DST_DUPLICATE_RESOLVED`) — milestone 1.5.
 *   - Pre-call event-count estimation (currently checked post-allocation) — milestone 2.
 */
public class DefaultScheduleCalculator : ScheduleCalculator {
    public companion object {
        /** Per ADR-0008. Window cap of 30 days covers the milestone-2 "next month" view. */
        public const val MAX_WINDOW_DAYS: Int = 30

        /** Per ADR-0008. Defense-in-depth ceiling; per-field caps make this unreachable. */
        public const val MAX_EVENT_COUNT: Int = 100_000
    }

    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> {
        // --- 1. Validate the window ----------------------------------------------------------
        if (fromInclusive >= toExclusive) {
            throw MalformedScheduleException.WindowNotPositive(fromInclusive, toExclusive)
        }
        val windowDuration = toExclusive - fromInclusive
        if (windowDuration > MAX_WINDOW_DAYS.days) {
            throw MalformedScheduleException.WindowTooLarge(
                requestedDays = windowDuration.inWholeDays,
                maxDays = MAX_WINDOW_DAYS,
            )
        }

        // --- 2. Empty-phases fast path -------------------------------------------------------
        if (phases.isEmpty()) return emptyList()

        // --- 3. Validate phase ordering ------------------------------------------------------
        // Detect duplicate phaseOrder values before sorting.
        val byOrder = phases.groupBy { it.phaseOrder }
        byOrder.entries.firstOrNull { it.value.size > 1 }?.let { dup ->
            throw MalformedScheduleException.DuplicatePhaseOrder(
                phaseOrder = dup.key,
                phaseIds = dup.value.map { it.id },
            )
        }
        val sorted = phases.sortedBy { it.phaseOrder }
        val orders = sorted.map { it.phaseOrder }
        if (orders != orders.indices.toList()) {
            throw MalformedScheduleException.PhaseOrderGap(orders)
        }

        // --- 4. Compute effective end date ---------------------------------------------------
        // Phase 0 occupies calendar days [startDate, startDate + duration0).
        // Phase 1 occupies the next contiguous range, etc.
        // The schedule ENDS (inclusive) at startDate + sum(durations) - 1.
        val totalPhaseDays = sorted.sumOf { it.durationDays }
        val phaseEndInclusive =
            schedule.startDate.plus(totalPhaseDays.toLong() - 1, DateTimeUnit.DAY)
        val effectiveEnd = schedule.endDate?.let { minOf(it, phaseEndInclusive) } ?: phaseEndInclusive

        // --- 5. Schedule-not-yet-started and schedule-already-over fast paths ----------------
        // "Not yet started" = the schedule's first dose (startDate at earliest local time) is
        // on or after toExclusive. Compare in the supplied timeZone.
        val scheduleStartInstant = schedule.startDate.atStartOfDayIn(timeZone)
        if (scheduleStartInstant >= toExclusive) return emptyList()

        // "Already over" = the last possible dose (effectiveEnd at end of day local) is before
        // fromInclusive. Use start-of-next-day as the strict upper bound for the effectiveEnd
        // calendar day.
        val effectiveEndExclusiveInstant =
            effectiveEnd.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        if (effectiveEndExclusiveInstant <= fromInclusive) return emptyList()

        // --- 6. Walk calendar days, emitting doses --------------------------------------------
        val results = ArrayList<ScheduledDose>(64)
        var currentDate = schedule.startDate
        var phaseIndex = 0
        var dayInPhase = 0

        while (phaseIndex < sorted.size && currentDate <= effectiveEnd) {
            val phase = sorted[phaseIndex]

            // dayInPhase 0 is always a dosing day. After that, dose every `dayInterval` days.
            if (dayInPhase % phase.dayInterval == 0) {
                for (localTime in phase.doseTimesLocal) {
                    val ldt = LocalDateTime(currentDate, localTime)
                    val instant = ldt.toInstant(timeZone)
                    if (instant >= fromInclusive && instant < toExclusive) {
                        results.add(
                            ScheduledDose(
                                scheduledAt = instant,
                                phaseOrder = phase.phaseOrder,
                                doseAmount = phase.doseAmount,
                            ),
                        )
                        if (results.size > MAX_EVENT_COUNT) {
                            throw MalformedScheduleException.EventCountExceeded(
                                attemptedCount = results.size.toLong(),
                                maxCount = MAX_EVENT_COUNT,
                            )
                        }
                    }
                }
            }

            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
            dayInPhase++
            if (dayInPhase >= phase.durationDays) {
                phaseIndex++
                dayInPhase = 0
            }
        }

        // --- 7. Global ordering guarantee (F5) -----------------------------------------------
        // doseTimesLocal is enforced strictly-ascending within a day by SchedulePhase.init.
        // Days advance monotonically. Phases are processed in phaseOrder. Therefore the
        // results list is already globally ascending by `scheduledAt`. No re-sort needed.
        // This is asserted-by-construction, not by a sort call — the test catches a regression
        // if this invariant is ever broken.
        return results
    }
}
