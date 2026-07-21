package app.toebeans.core.scheduler

import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

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
 *   - ADR-0007 ELAPSED_INTERVAL anchor mode: doses spaced by gaps derived from
 *     [SchedulePhase.doseTimesLocal] with wrap-to-24h semantics.
 *   - ADR-0008 mechanical bounds: window ≤ 30 days, event count ≤ 100,000.
 *
 * Not yet honored (deferred):
 *   - ADR-0007 anchor mode `STAY_HOME_TZ` — caller-side policy, no calculator change.
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
        val totalPhaseDays = sorted.sumOf { it.durationDays }
        val phaseEndInclusive =
            schedule.startDate.plus(totalPhaseDays.toLong() - 1, DateTimeUnit.DAY)
        val effectiveEnd = schedule.endDate?.let { minOf(it, phaseEndInclusive) } ?: phaseEndInclusive

        // --- 5. Schedule-not-yet-started fast path -------------------------------------------
        val scheduleStartInstant = schedule.startDate.atStartOfDayIn(timeZone)
        if (scheduleStartInstant >= toExclusive) return emptyList()

        // --- 6. Emit doses -------------------------------------------------------------------
        return if (schedule.anchorMode == AnchorMode.ELAPSED_INTERVAL) {
            computeElapsedIntervalDoses(schedule, sorted, timeZone, effectiveEnd, fromInclusive, toExclusive)
        } else {
            computeWallClockDoses(schedule, sorted, timeZone, effectiveEnd, fromInclusive, toExclusive)
        }
    }

    /**
     * FOLLOW_PHONE (and STAY_HOME_TZ) path: wall-clock calendar-day walking.
     *
     * Each dose is anchored to a calendar date + local wall-clock time, converted to UTC via
     * [timeZone]. DST shifts the UTC instant but preserves the local wall-clock time.
     */
    private fun computeWallClockDoses(
        schedule: Schedule,
        sorted: List<SchedulePhase>,
        timeZone: TimeZone,
        effectiveEnd: LocalDate,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> {
        // Already-over fast path (calendar-day semantics).
        val effectiveEndExclusiveInstant =
            effectiveEnd.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        if (effectiveEndExclusiveInstant <= fromInclusive) return emptyList()

        val results = ArrayList<ScheduledDose>(64)
        var currentDate = schedule.startDate
        var phaseIndex = 0
        var dayInPhase = 0

        while (phaseIndex < sorted.size && currentDate <= effectiveEnd) {
            val phase = sorted[phaseIndex]

            if (dayInPhase % phase.dayInterval == 0) {
                for (localTime in phase.doseTimesLocal) {
                    val ldt = LocalDateTime(currentDate, localTime)
                    val instant = ldt.toInstant(timeZone)
                    // ADR-0004 contract: endDate is inclusive; no dose strictly after effectiveEnd.
                    val doseDate = instant.toLocalDateTime(timeZone).date
                    if (doseDate > effectiveEnd) return results

                    if (instant >= fromInclusive && instant < toExclusive) {
                        val dstWarning = computeDstWarning(ldt, instant, timeZone)
                        results.add(
                            ScheduledDose(
                                scheduledAt = instant,
                                phaseOrder = phase.phaseOrder,
                                doseAmount = phase.doseAmount,
                                doseUnit = phase.doseUnit,
                                dstWarning = dstWarning,
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

        // Global ordering is asserted-by-construction (ascending dates + times).
        return results
    }

    /**
     * ELAPSED_INTERVAL path: fixed UTC intervals from the first dose.
     *
     * 1. Compute gaps between consecutive [SchedulePhase.doseTimesLocal] values.
     * 2. Force a wrap-around gap so the sum of all gaps equals 24h (one full cycle).
     * 3. First dose = [schedule.startDate] @ first local time, projected through [timeZone].
     * 4. Every subsequent dose = previous dose + next gap in the cycle.
     * 5. Phases are concatenated: phase N+1 starts immediately after phase N's last dose,
     *    using phase N+1's own gap pattern.
     *
     * DST has no effect on the interval; it only affects the first-dose projection and any
     * UI rendering that converts UTC back to local time.
     *
     * Unsupported combinations:
     *  - `dayInterval != 1` is rejected because "skip days" and "fixed interval" are
     *    contradictory concepts.
     */
    private fun computeElapsedIntervalDoses(
        schedule: Schedule,
        sorted: List<SchedulePhase>,
        timeZone: TimeZone,
        effectiveEnd: LocalDate,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> {
        // Already-over fast path.
        val effectiveEndExclusiveInstant =
            effectiveEnd.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        if (effectiveEndExclusiveInstant <= fromInclusive) return emptyList()

        val results = ArrayList<ScheduledDose>(64)
        var lastInstant: Instant? = null

        for (phase in sorted) {
            if (phase.dayInterval != 1) {
                throw MalformedScheduleException.ElapsedIntervalDayIntervalUnsupported(
                    phaseId = phase.id,
                    dayInterval = phase.dayInterval,
                )
            }

            val gaps = computeGaps(phase.doseTimesLocal)
            val totalDoses = phase.durationDays * phase.dosesPerDay

            for (doseInPhase in 0 until totalDoses) {
                val instant =
                    when {
                        lastInstant == null -> {
                            // First dose of the entire schedule.
                            LocalDateTime(schedule.startDate, phase.doseTimesLocal.first())
                                .toInstant(timeZone)
                        }
                        doseInPhase == 0 -> {
                            // First dose of a subsequent phase: continue from previous phase's
                            // last dose using this phase's first gap.
                            lastInstant + gaps[0]
                        }
                        else -> {
                            // Subsequent dose in this phase: cycle through gaps.
                            val gapIndex = (doseInPhase - 1) % gaps.size
                            lastInstant + gaps[gapIndex]
                        }
                    }

                val doseDate = instant.toLocalDateTime(timeZone).date
                if (doseDate > effectiveEnd) return results

                if (instant >= fromInclusive && instant < toExclusive) {
                    results.add(
                        ScheduledDose(
                            scheduledAt = instant,
                            phaseOrder = phase.phaseOrder,
                            doseAmount = phase.doseAmount,
                            doseUnit = phase.doseUnit,
                        ),
                    )
                    if (results.size > MAX_EVENT_COUNT) {
                        throw MalformedScheduleException.EventCountExceeded(
                            attemptedCount = results.size.toLong(),
                            maxCount = MAX_EVENT_COUNT,
                        )
                    }
                }

                lastInstant = instant
            }
        }

        // Results are generated in chronological order by construction.
        return results
    }

    /**
     * Derive the elapsed-time gaps from a list of local wall-clock times.
     *
     * For `[t0, t1, t2]` the gaps are `[t1-t0, t2-t1, (24h-t2)+t0]`.
     * The last gap wraps around midnight so that `sum(gaps) == 24h`.
     */
    private fun computeGaps(doseTimesLocal: List<LocalTime>): List<Duration> {
        require(doseTimesLocal.isNotEmpty()) {
            "computeGaps requires at least one dose time; empty list is a programmer error"
        }
        val gaps = mutableListOf<Duration>()
        for (i in 0 until doseTimesLocal.size - 1) {
            val seconds = doseTimesLocal[i + 1].toSecondOfDay() - doseTimesLocal[i].toSecondOfDay()
            gaps += seconds.seconds
        }
        val firstSeconds = doseTimesLocal.first().toSecondOfDay()
        val lastSeconds = doseTimesLocal.last().toSecondOfDay()
        val wrapSeconds = (24 * 3600 - lastSeconds) + firstSeconds
        gaps += wrapSeconds.seconds
        return gaps
    }

    /**
     * Detect DST transition warnings for a single dose in wall-clock mode.
     *
     * Algorithm (linear-time per dose, negligible overhead):
     *   1. Round-trip the instant back to local time.
     *      If the local time changed, the original was in a spring-forward gap
     *      → [DstWarning.DST_SKIP].
     *   2. Otherwise, check if the same local time exists 1 hour later in UTC.
     *      If yes, the original was in a fall-back overlap
     *      → [DstWarning.DST_DUPLICATE_RESOLVED].
     *   3. Otherwise → no warning.
     *
     * The "1 hour later" heuristic covers all standard 1-hour DST shifts.
     * Timezones with non-1-hour shifts (e.g. Lord Howe Island, 30 min) may not
     * be detected; this is documented and acceptable for v1.
     */
    private fun computeDstWarning(
        ldt: LocalDateTime,
        instant: Instant,
        timeZone: TimeZone,
    ): DstWarning? {
        val roundTripped = instant.toLocalDateTime(timeZone)
        if (roundTripped.time != ldt.time) {
            // Spring-forward gap: the local wall-clock time does not exist.
            return DstWarning.DST_SKIP
        }
        // Fall-back overlap check: does the same local time exist 1h later in UTC?
        if ((instant + 1.hours).toLocalDateTime(timeZone).time == ldt.time) {
            return DstWarning.DST_DUPLICATE_RESOLVED
        }
        return null
    }
}
