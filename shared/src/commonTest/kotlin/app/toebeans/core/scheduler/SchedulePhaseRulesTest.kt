package app.toebeans.core.scheduler

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test-as-spec for the [ScheduleCalculator] contract.
 *
 * **THIS TEST IS REQUIRED TO FAIL** against the current code base. It is the specification of
 * the tapering-schedule semantics; the implementation in [DefaultScheduleCalculator] is a stub.
 *
 * Per AGENTS.md § Test-as-spec rules:
 *   1. A human reviewer must approve this test before any implementation work begins.
 *   2. Implementation may then proceed in a separate PR that ONLY makes this test pass.
 *   3. Mutation testing (pitest) is required after the test passes.
 *
 * Reference inputs are stated in UTC for clarity, then projected through the timezone parameter.
 * Test 5 (DST) is intentionally deferred to a follow-up PR with its own ADR.
 */
class SchedulePhaseRulesTest {
    private val calculator: ScheduleCalculator = DefaultScheduleCalculator()

    // 2026-06-01 is chosen so that no part of the test window crosses a DST boundary in either
    // America/Los_Angeles or America/New_York. DST handling is its own test class.
    private val utc = TimeZone.UTC
    private val baseDate: LocalDate = LocalDate(2026, 6, 1)

    @Test
    fun `single-phase BID for 5 days yields 10 doses at the correct local times`() {
        val schedule =
            Schedule(
                id = "sched-1",
                medicationId = "med-1",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-1",
                scheduleId = "sched-1",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
            )

        val window = baseDate.atTime(0, 0).toInstant(utc)..baseDate.plusDays(5).atTime(0, 0).toInstant(utc)
        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = window.start,
                toExclusive = window.endInclusive,
            )

        assertEquals(10, result.size, "expected 5 days × 2 doses = 10 events")
        assertTrue(result == result.sortedBy { it.scheduledAt }, "results must be ascending")
        assertTrue(result.all { it.phaseOrder == 0 }, "all doses must come from phase 0")

        // First and last doses anchor the window correctness.
        assertEquals(
            LocalDateTime(2026, 6, 1, 8, 0).toInstant(utc),
            result.first().scheduledAt,
        )
        assertEquals(
            LocalDateTime(2026, 6, 5, 20, 0).toInstant(utc),
            result.last().scheduledAt,
        )
    }

    @Test
    fun `two-phase taper concatenates phases without overlap or gap`() {
        // Phase 0: 10mg BID for 5 days.  Phase 1: 5mg BID for 5 days.
        val schedule =
            Schedule(
                id = "sched-2",
                medicationId = "med-2",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase0 =
            SchedulePhase(
                id = "phase-2-0",
                scheduleId = "sched-2",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = "10mg",
            )
        val phase1 =
            SchedulePhase(
                id = "phase-2-1",
                scheduleId = "sched-2",
                phaseOrder = 1,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = "5mg",
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase1, phase0), // intentionally unordered input
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(10).atTime(0, 0).toInstant(utc),
            )

        assertEquals(20, result.size, "expected 10 days × 2 doses = 20 events across two phases")
        // F5: global ordering across phases, not just within a phase.
        assertTrue(
            result == result.sortedBy { it.scheduledAt },
            "results must be globally ascending across phase boundaries",
        )

        val phase0Doses = result.filter { it.phaseOrder == 0 }
        val phase1Doses = result.filter { it.phaseOrder == 1 }
        assertEquals(10, phase0Doses.size, "phase 0 contributes 10 doses")
        assertEquals(10, phase1Doses.size, "phase 1 contributes 10 doses")

        assertTrue(
            phase0Doses.all { it.doseAmount == "10mg" } && phase1Doses.all { it.doseAmount == "5mg" },
            "dose amounts must reflect each phase's override",
        )

        // The boundary check: phase 0 ends 2026-06-05 20:00, phase 1 starts 2026-06-06 08:00.
        // There must be a clean handoff with no duplicate at midnight.
        assertEquals(
            LocalDateTime(2026, 6, 5, 20, 0).toInstant(utc),
            phase0Doses.last().scheduledAt,
        )
        assertEquals(
            LocalDateTime(2026, 6, 6, 8, 0).toInstant(utc),
            phase1Doses.first().scheduledAt,
        )
    }

    @Test
    fun `window narrower than the schedule returns only events inside the window`() {
        val schedule =
            Schedule(
                id = "sched-3",
                medicationId = "med-3",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-3",
                scheduleId = "sched-3",
                phaseOrder = 0,
                durationDays = 30,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
            )

        // 72-hour window starting on day 2 morning.
        val from = LocalDateTime(2026, 6, 2, 0, 0).toInstant(utc)
        val to = LocalDateTime(2026, 6, 5, 0, 0).toInstant(utc)
        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = from,
                toExclusive = to,
            )

        assertEquals(6, result.size, "72 hours x 2 doses/day == 6 doses")
        assertTrue(result.all { it.scheduledAt in from..<to }, "all events must lie inside the window")
    }

    @Test
    fun `endDate caps the schedule before phase exhaustion`() {
        val schedule =
            Schedule(
                id = "sched-4",
                medicationId = "med-4",
                startDate = baseDate,
                endDate = baseDate.plusDays(2), // inclusive: 2026-06-01, 02, 03
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-4",
                scheduleId = "sched-4",
                phaseOrder = 0,
                durationDays = 10, // would otherwise extend past endDate
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(30).atTime(0, 0).toInstant(utc),
            )

        assertEquals(6, result.size, "3 days × 2 doses == 6 events (endDate is inclusive)")
        assertEquals(
            LocalDateTime(2026, 6, 3, 20, 0).toInstant(utc),
            result.last().scheduledAt,
            "last event must be on the inclusive endDate",
        )
    }

    // D2: confusing-time anchor. Per the human-reviewed decision (2026-05-15), a phase
    // beginning at 00:00 local on startDate produces a dose at exactly that instant.
    // A separate UX layer (slice 1) will warn the user when any doseTimesLocal falls in
    // [00:00, 06:00) — see docs/issues/v0.1-followups.md item #1.
    @Test
    fun `phase with midnight dose time anchors first dose at startDate 0000 local`() {
        val schedule =
            Schedule(
                id = "sched-d2",
                medicationId = "med-d2",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-d2",
                scheduleId = "sched-d2",
                phaseOrder = 0,
                durationDays = 2,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(0, 0), LocalTime(12, 0)),
                doseAmount = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(2).atTime(0, 0).toInstant(utc),
            )

        assertEquals(4, result.size, "2 days × 2 doses == 4 events")
        assertEquals(
            LocalDateTime(2026, 6, 1, 0, 0).toInstant(utc),
            result.first().scheduledAt,
            "first dose anchors at startDate 00:00 local, NOT next day",
        )
        assertEquals(
            LocalDateTime(2026, 6, 2, 12, 0).toInstant(utc),
            result.last().scheduledAt,
        )
    }

    // D3a: duplicate phaseOrder is a medication-critical bug class. Throw, don't guess.
    @Test
    fun `duplicate phaseOrder throws IllegalArgumentException`() {
        val schedule =
            Schedule(
                id = "sched-d3a",
                medicationId = "med-d3a",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phaseA =
            SchedulePhase(
                id = "phase-d3a-1",
                scheduleId = "sched-d3a",
                phaseOrder = 0,
                durationDays = 3,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
            )
        val phaseB = phaseA.copy(id = "phase-d3a-2") // same phaseOrder = 0

        assertFailsWith<IllegalArgumentException> {
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phaseA, phaseB),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(5).atTime(0, 0).toInstant(utc),
            )
        }
    }

    // D3b / F4: gap in phaseOrder is malformed. [0, 2] without a phase 1 is rejected.
    @Test
    fun `phaseOrder gap throws IllegalArgumentException`() {
        val schedule =
            Schedule(
                id = "sched-d3b",
                medicationId = "med-d3b",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase0 =
            SchedulePhase(
                id = "phase-d3b-0",
                scheduleId = "sched-d3b",
                phaseOrder = 0,
                durationDays = 3,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
            )
        val phase2 = phase0.copy(id = "phase-d3b-2", phaseOrder = 2) // skips 1

        assertFailsWith<IllegalArgumentException> {
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase0, phase2),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(10).atTime(0, 0).toInstant(utc),
            )
        }
    }

    // D4: an empty phase list is not an error — it is a no-op. Returns empty.
    @Test
    fun `empty phases returns empty result`() {
        val schedule =
            Schedule(
                id = "sched-d4",
                medicationId = "med-d4",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = emptyList(),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(5).atTime(0, 0).toInstant(utc),
            )

        assertTrue(result.isEmpty(), "empty phases means no scheduled doses")
    }

    // D5: schedule has not started yet within the query window. Empty result.
    @Test
    fun `schedule starting after toExclusive returns empty result`() {
        val schedule =
            Schedule(
                id = "sched-d5",
                medicationId = "med-d5",
                startDate = baseDate.plusDays(10), // 2026-06-11
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-d5",
                scheduleId = "sched-d5",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(5).atTime(0, 0).toInstant(utc), // 2026-06-06
            )

        assertTrue(
            result.isEmpty(),
            "schedule starting 2026-06-11 must yield no doses in window ending 2026-06-06",
        )
    }
}

// Small extensions to keep test setup readable.
// We use kotlinx-datetime's idiomatic plus(n, DateTimeUnit.DAY) rather than hand-rolling.
private fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)

private fun LocalDate.atTime(
    hour: Int,
    minute: Int,
): LocalDateTime = LocalDateTime(year, month, dayOfMonth, hour, minute)
