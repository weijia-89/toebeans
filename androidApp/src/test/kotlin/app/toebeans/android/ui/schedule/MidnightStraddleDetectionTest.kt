package app.toebeans.android.ui.schedule

import kotlinx.datetime.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MidnightStraddleDetection.crossesMidnight] (v0.1-followups #9).
 *
 * Spec: see the KDoc on [MidnightStraddleDetection] for the rule and rationale. These
 * tests pin the boundary behavior so future refactors keep the same UX semantics.
 *
 * The rule, restated: a list of dose times "straddles midnight" iff, after sorting by
 * second-of-day, some interior gap (between two consecutive same-day times) is strictly
 * larger than the wrap-around gap (from the latest time back to the earliest time of the
 * next day). Equivalent geometric reading: the largest gap on the 24-hour clock circle
 * does NOT contain midnight.
 */
class MidnightStraddleDetectionTest {
    @Test
    fun `empty list does not straddle`() {
        // No doses, no rhythm to wrap. The detection function has nothing to flag.
        assertFalse(MidnightStraddleDetection.crossesMidnight(emptyList()))
    }

    @Test
    fun `single dose at noon does not straddle`() {
        // A lone time defines no interval and therefore cannot cross anything.
        assertFalse(MidnightStraddleDetection.crossesMidnight(listOf(LocalTime(12, 0))))
    }

    @Test
    fun `single dose at exactly midnight does not straddle`() {
        // Edge case from the prompt: 00:00 as the only time. Same reasoning as the noon
        // single-dose case — one time, no interval. The time-of-day happens to be
        // midnight, but there is no second dose to define a crossing.
        assertFalse(MidnightStraddleDetection.crossesMidnight(listOf(LocalTime(0, 0))))
    }

    @Test
    fun `typical daytime schedule does not straddle`() {
        // 08:00, 12:00, 18:00 — three meals. Interior gaps: 4h, 6h. Wrap-around gap:
        // 14h (18:00 → next-day 08:00). The wrap is the largest, so the user's rest
        // period spans midnight as expected. No straddle.
        assertFalse(
            MidnightStraddleDetection.crossesMidnight(
                listOf(LocalTime(8, 0), LocalTime(12, 0), LocalTime(18, 0)),
            ),
        )
    }

    @Test
    fun `dose at 23 followed by dose at 01 straddles midnight`() {
        // The motivating case from the prompt. Sorted: [01:00, 23:00]. Interior gap:
        // 22h. Wrap-around gap: 2h. The 22h rest period sits inside a single calendar
        // day (01:00 → 23:00), so the dosing interval is the one crossing midnight.
        assertTrue(
            MidnightStraddleDetection.crossesMidnight(
                listOf(LocalTime(23, 0), LocalTime(1, 0)),
            ),
        )
    }

    @Test
    fun `three doses spanning midnight straddle`() {
        // 23:00, 01:00, 08:00. Sorted: [01:00, 08:00, 23:00]. Interior gaps: 7h, 15h.
        // Wrap: 2h. The largest interior gap (15h, 08:00 → 23:00) exceeds the wrap
        // (23:00 → next-day 01:00), so the schedule straddles.
        assertTrue(
            MidnightStraddleDetection.crossesMidnight(
                listOf(LocalTime(23, 0), LocalTime(1, 0), LocalTime(8, 0)),
            ),
        )
    }

    @Test
    fun `symmetric doses at 00 and 12 do not straddle (tie does not trigger)`() {
        // [00:00, 12:00] — two evenly-spaced doses. Both gaps are 12h. The rule uses
        // strict inequality, so a tie is NOT a straddle. The user's schedule is
        // unambiguous: doses 12h apart. Nothing to nudge about.
        assertFalse(
            MidnightStraddleDetection.crossesMidnight(
                listOf(LocalTime(0, 0), LocalTime(12, 0)),
            ),
        )
    }
}
