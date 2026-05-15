package app.toebeans.core.scheduler

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Computes the scheduled-time series for a [Schedule] within a half-open window
 * `[fromInclusive, toExclusive)`.
 *
 * Contract:
 *  - **Pure.** No I/O. No platform clock access. No randomness. Same inputs -> same outputs.
 *  - **Order.** Returned list is ascending by [ScheduledDose.scheduledAt].
 *  - **Window honoring.** Every returned event satisfies
 *    `fromInclusive <= scheduledAt < toExclusive` regardless of phase boundaries.
 *  - **Phase mapping.** Each event carries the [SchedulePhase.phaseOrder] of the phase that
 *    produced it. If a phase ends midday and the next phase begins, two phases may both
 *    contribute events on the same calendar date.
 *  - **DST.** Times are computed in local wall-clock per [timeZone]; a 23h or 25h day may
 *    therefore have the usual number of doses, just shifted in UTC instants.
 *  - **End-date semantics.** If [Schedule.endDate] is non-null, no event with a calendar date
 *    after `endDate` is produced.
 *  - **Phase exhaustion.** When the sum of phase durations is less than the schedule's
 *    effective length, the schedule simply ends at the last phase's last day. Phases are
 *    NOT looped or extended.
 *
 * @param phases the schedule's phases in any order; the implementation sorts by [SchedulePhase.phaseOrder].
 * @param timeZone the local timezone in which dose times are interpreted.
 * @param fromInclusive earliest instant to include (UTC).
 * @param toExclusive earliest instant to exclude (UTC). Must be strictly greater than [fromInclusive].
 */
public fun interface ScheduleCalculator {
    public fun computeScheduledTimes(
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
