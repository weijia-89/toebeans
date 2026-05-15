package app.toebeans.core.scheduler

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Computes the scheduled-dose series for a [Schedule] within a half-open window
 * `[fromInclusive, toExclusive)`.
 *
 * Contract:
 *  - **Pure.** No I/O. No platform clock access. No randomness. Same inputs -> same outputs.
 *  - **Order.** Returned list is ascending by [ScheduledDose.scheduledAt] (globally, across phases).
 *  - **Window honoring.** Every returned event satisfies
 *    `fromInclusive <= scheduledAt < toExclusive` regardless of phase boundaries.
 *  - **Phase mapping.** Each event carries the [SchedulePhase.phaseOrder] of the phase that
 *    produced it. Phase 0 occupies calendar days `[startDate, startDate + phase0.durationDays)`,
 *    phase 1 occupies the next contiguous range, and so on. There is no half-day handoff:
 *    phase N's last dose is on its last calendar day; phase N+1's first dose is on the next
 *    calendar day. The "two phases on the same calendar date" case from earlier drafts is
 *    impossible by construction.
 *  - **DST.** Times are computed in local wall-clock per [timeZone]; a 23h or 25h day may
 *    therefore have the usual number of doses, just shifted in UTC instants. DST edge-case
 *    test coverage is deferred — tracked by ADR-0007.
 *  - **End-date semantics — INCLUSIVE.** If [Schedule.endDate] is non-null, doses ARE produced
 *    on `endDate` itself. No dose with a calendar date strictly after `endDate` is produced.
 *    This convention is intentionally asymmetric with the [toExclusive] window param:
 *    `endDate` is a clinical bound ("give through Friday") and humans expect it inclusive;
 *    `toExclusive` is a materialization bound ("compute the next 72h") and is mechanical.
 *  - **Phase exhaustion.** When the sum of phase durations is less than the schedule's
 *    effective length, the schedule simply ends at the last phase's last day. Phases are
 *    NOT looped or extended.
 *  - **Empty-result cases.** Returns an empty list when:
 *      * [phases] is empty.
 *      * [Schedule.startDate] is on or after [toExclusive] (the schedule has not started yet
 *        within this window).
 *      * The schedule's effective range (capped by `endDate`) lies entirely outside
 *        `[fromInclusive, toExclusive)`.
 *  - **Throws [IllegalArgumentException]** for malformed input:
 *      * Two phases share the same [SchedulePhase.phaseOrder] (duplicate).
 *      * The phases' `phaseOrder` values, sorted, are not the dense sequence `0, 1, 2, ...`
 *        (gaps such as `[0, 2]` are rejected; missing-zero is rejected).
 *      * [fromInclusive] is not strictly less than [toExclusive].
 *    Silently producing nonsense from malformed input is a medication-critical bug class;
 *    we crash loudly instead.
 *
 * @param phases the schedule's phases in any order; the implementation sorts by [SchedulePhase.phaseOrder].
 * @param timeZone the local timezone in which dose times are interpreted. v0.1 callers pass
 *                 `TimeZone.currentSystemDefault()` captured at materialization time, which
 *                 gives "travel mode" for free (phone switches TZ on landing → next
 *                 materialization uses the new TZ). Per-schedule pinned timezones are a
 *                 follow-up tracked by ADR-0007.
 * @param fromInclusive earliest instant to include (UTC).
 * @param toExclusive earliest instant to exclude (UTC). Must be strictly greater than [fromInclusive].
 */
public fun interface ScheduleCalculator {
    public fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose>
}

/**
 * One dose occurrence projected from a schedule. Pure data; not yet persisted.
 *
 * @property scheduledAt the instant (UTC) the dose is due.
 * @property phaseOrder the [SchedulePhase.phaseOrder] this dose came from.
 * @property doseAmount the dose amount to administer; falls back to the parent
 *           [app.toebeans.core.model.Medication]'s default if the phase did not override.
 */
public data class ScheduledDose(
    val scheduledAt: Instant,
    val phaseOrder: Int,
    val doseAmount: String?,
)
