package app.toebeans.android.ui.schedule

import kotlinx.datetime.LocalTime

/**
 * Detects whether a set of LocalTime dose times for a single phase "straddles midnight" —
 * i.e. the dosing rhythm wraps around the calendar-day boundary in a way that may confuse
 * the user when reading the schedule. Pure function; no side effects.
 *
 * See `v0.1-followups #9` for the UX context. The companion (and pre-existing) warning
 * `PhaseDraft.nightDoseWarning` fires for any single dose in `[00:00, 06:00)` and is a
 * sleep-disruption nudge; this one is a calendar-day-boundary nudge. They are independent.
 *
 * ## Rule
 *
 * Place each time on the 24-hour clock circle. There are N gaps between N distinct times
 * (counting the wrap-around gap from the latest time back to the earliest time of the
 * next day). The largest gap on the circle is the user's "rest period".
 *
 * A schedule does NOT straddle midnight if that rest period spans midnight (the normal
 * case: doses cluster during waking hours with the longest silence overnight). A schedule
 * DOES straddle midnight if some interior gap (between two consecutive same-day times) is
 * STRICTLY larger than the wrap-around gap — meaning the active dosing interval is the
 * one crossing midnight, and the rest period sits entirely within a single calendar day.
 *
 * Strict inequality on purpose: if an interior gap ties the wrap-around gap (e.g.
 * `[00:00, 12:00]`, two evenly-spaced doses), the schedule is unambiguous and no warning
 * is needed.
 *
 * ## Behavior at edges
 *
 *  - Empty list → false (no doses, nothing to warn about).
 *  - Single dose → false (a lone time defines no interval, so it cannot straddle).
 *  - A single dose at exactly `00:00` → false (same reason; the time-of-day happens to be
 *    midnight, but there is no second dose to define a crossing).
 *  - Duplicate dose times → de-duplicated before the gap calculation (duplicates create
 *    zero-width gaps that don't represent distinct dosing intervals).
 *
 * Worked example: `[23:00, 01:00]`. Sorted: `[01:00, 23:00]`. Interior gap = 22h. Wrap-
 * around gap = 24h - 23:00 + 01:00 = 2h. Interior (22h) > wrap (2h) → straddles.
 *
 * Worked example: `[08:00, 12:00, 18:00]`. Interior gaps: 4h, 6h. Wrap-around gap: 14h.
 * Wrap is the largest → does NOT straddle.
 */
internal object MidnightStraddleDetection {
    private const val SECONDS_PER_DAY = 24 * 60 * 60

    /** Returns true iff [doseTimes] straddles midnight per the rule documented above. */
    fun crossesMidnight(doseTimes: List<LocalTime>): Boolean {
        // De-duplicate before reasoning about gaps. Duplicates produce zero-width gaps
        // that do not represent distinct dosing intervals; SchedulePhase already
        // rejects duplicate doseTimesLocal on save, but the form draft can hold
        // intermediate duplicates while the user is still editing.
        val sorted = doseTimes.distinct().sortedBy { it.secondOfDay() }
        if (sorted.size < 2) return false
        // Wrap-around gap: from the latest same-day time forward across midnight to the
        // earliest time of the next day. Equivalently: the slice of the 24h circle that
        // contains midnight.
        val wrapGap = SECONDS_PER_DAY - sorted.last().secondOfDay() + sorted.first().secondOfDay()
        // Interior gaps: consecutive same-day differences. zipWithNext on a list of
        // size >= 2 always yields at least one element, so max() is safe.
        val maxInteriorGap = sorted.zipWithNext { a, b -> b.secondOfDay() - a.secondOfDay() }.max()
        // Strict ">" so a tie (e.g., [00:00, 12:00], two symmetric 12h gaps) does NOT
        // trigger. A symmetric schedule is unambiguous and needs no nudge.
        return maxInteriorGap > wrapGap
    }

    private fun LocalTime.secondOfDay(): Int = hour * 3600 + minute * 60 + second
}
