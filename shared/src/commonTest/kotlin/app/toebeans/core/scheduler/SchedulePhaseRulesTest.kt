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
 * **All 15 cases pass green** against the current [DefaultScheduleCalculator] implementation.
 * This file is now the regression contract, any change to the calculator must keep these
 * green, and any extension (e.g. ADR-0007 anchor modes, DST handling) must add cases here
 * rather than introduce a sibling test class.
 *
 * Per AGENTS.md § Test-as-spec rules:
 *   1. A human reviewer must approve any new case in this file before implementation work
 *      that depends on it begins.
 *   2. Implementation that lights up a new case lives in its own PR.
 *   3. Mutation testing (pitest) is deferred per ADR-0006 (Kover coverage gates this surface
 *      at 85% in the meantime).
 *
 * Reference inputs are stated in UTC for clarity, then projected through the timezone parameter.
 * DST-specific behavior is deferred to a future test class with its own ADR-0007 acceptance.
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
    // [00:00, 06:00), see docs/issues/v0.1-followups.md item #1.
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

        assertEquals(4, result.size, "2 days × 2 doses == 4 events; got ${result.size}")
        // Pin every expected dose by index so the failure message identifies which one drifted.
        val expected =
            listOf(
                // day 1, 00:00, the key anchor assertion
                LocalDateTime(2026, 6, 1, 0, 0),
                // day 1, 12:00
                LocalDateTime(2026, 6, 1, 12, 0),
                // day 2, 00:00, not skipped to next noon
                LocalDateTime(2026, 6, 2, 0, 0),
                // day 2, 12:00
                LocalDateTime(2026, 6, 2, 12, 0),
            ).map { it.toInstant(utc) }
        expected.forEachIndexed { i, exp ->
            assertEquals(
                exp,
                result[i].scheduledAt,
                "dose[$i] mismatched: a midnight (00:00) dose on startDate must fire at that instant, " +
                    "not be deferred to the next valid hour or the next day. " +
                    "This is the anchor decision D2 from ADR-0004's 2026-05-15 review.",
            )
        }
    }

    // D3a: duplicate phaseOrder is a medication-critical bug class. Throw the discriminated
    // structured exception so an AI agent (or UI mapper) can act on it without parsing prose.
    @Test
    fun `duplicate phaseOrder throws DuplicatePhaseOrder with the offending value and ids`() {
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

        val thrown =
            assertFailsWith<MalformedScheduleException.DuplicatePhaseOrder> {
                calculator.computeScheduledDoses(
                    schedule = schedule,
                    phases = listOf(phaseA, phaseB),
                    timeZone = utc,
                    fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                    toExclusive = baseDate.plusDays(5).atTime(0, 0).toInstant(utc),
                )
            }
        assertEquals(0, thrown.phaseOrder, "exception must report the offending phaseOrder")
        assertEquals(
            setOf("phase-d3a-1", "phase-d3a-2"),
            thrown.phaseIds.toSet(),
            "exception must report all colliding phase ids so the UI can highlight them",
        )
        assertEquals("DuplicatePhaseOrder", thrown.code, "stable code for log keys")
    }

    // D3b / F4: gap in phaseOrder is malformed. [0, 2] without a phase 1 is rejected.
    // Also tested: [1, 2] missing-zero (the gap is at the start).
    @Test
    fun `phaseOrder gap throws PhaseOrderGap with the actual phaseOrder list`() {
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

        val thrown =
            assertFailsWith<MalformedScheduleException.PhaseOrderGap> {
                calculator.computeScheduledDoses(
                    schedule = schedule,
                    phases = listOf(phase0, phase2),
                    timeZone = utc,
                    fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                    toExclusive = baseDate.plusDays(10).atTime(0, 0).toInstant(utc),
                )
            }
        assertEquals(
            listOf(0, 2),
            thrown.phaseOrders.sorted(),
            "exception must report the malformed phaseOrder list so a UI can show " +
                "'phases must be numbered 0, 1, 2, ... without gaps; got [0, 2]'",
        )

        // Variant: missing-zero (phases start at 1).
        val phase1 = phase0.copy(id = "phase-d3b-1-alt", phaseOrder = 1)
        val phase2alt = phase0.copy(id = "phase-d3b-2-alt", phaseOrder = 2)
        assertFailsWith<MalformedScheduleException.PhaseOrderGap> {
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase1, phase2alt),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(10).atTime(0, 0).toInstant(utc),
            )
        }
    }

    // D4: an empty phase list is not an error, it is a no-op. Returns empty.
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

    // B2: schedule already over before the window starts. Symmetric to D5.
    @Test
    fun `schedule whose endDate precedes fromInclusive returns empty result`() {
        val schedule =
            Schedule(
                id = "sched-b2",
                medicationId = "med-b2",
                startDate = baseDate,
                endDate = baseDate.plusDays(2), // 2026-06-03 inclusive, schedule ends here
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-b2",
                scheduleId = "sched-b2",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
            )

        // Query 7 days AFTER the schedule ended.
        val from = LocalDateTime(2026, 6, 10, 0, 0).toInstant(utc)
        val to = LocalDateTime(2026, 6, 17, 0, 0).toInstant(utc)
        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = from,
                toExclusive = to,
            )

        assertTrue(
            result.isEmpty(),
            "schedule with endDate=2026-06-03 must yield no doses in window 2026-06-10..2026-06-17",
        )
    }

    // B3: degenerate or inverted window is a programmer error. Throw the structured exception
    // so a caller bug surfaces immediately rather than silently returning empty.
    @Test
    fun `non-positive window throws WindowNotPositive with the offending instants`() {
        val schedule =
            Schedule(
                id = "sched-b3",
                medicationId = "med-b3",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-b3",
                scheduleId = "sched-b3",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
            )
        val from = baseDate.atTime(12, 0).toInstant(utc)
        val to = baseDate.atTime(8, 0).toInstant(utc) // to < from on purpose

        val thrown =
            assertFailsWith<MalformedScheduleException.WindowNotPositive> {
                calculator.computeScheduledDoses(
                    schedule = schedule,
                    phases = listOf(phase),
                    timeZone = utc,
                    fromInclusive = from,
                    toExclusive = to,
                )
            }
        assertEquals(from, thrown.fromInclusive)
        assertEquals(to, thrown.toExclusive)
        assertEquals("WindowNotPositive", thrown.code)
    }

    // Skip-day dosing via SchedulePhase.dayInterval. New in commit 2; ADR-0004 D2 mentions this
    // implicitly via "phase day N is a dosing day iff N % dayInterval == 0".
    @Test
    fun `dayInterval=2 fires on alternate calendar days starting from startDate`() {
        val schedule =
            Schedule(
                id = "sched-skip",
                medicationId = "med-skip",
                startDate = baseDate, // 2026-06-01 (Mon)
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        // Phase: 7 calendar days, every other day, single morning dose.
        val phase =
            SchedulePhase(
                id = "phase-skip",
                scheduleId = "sched-skip",
                phaseOrder = 0,
                durationDays = 7,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
                dayInterval = 2,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(7).atTime(0, 0).toInstant(utc),
            )

        // Phase day 0=Jun1, day 2=Jun3, day 4=Jun5, day 6=Jun7. Days 1, 3, 5 are off.
        val expected =
            listOf(
                LocalDateTime(2026, 6, 1, 8, 0),
                LocalDateTime(2026, 6, 3, 8, 0),
                LocalDateTime(2026, 6, 5, 8, 0),
                LocalDateTime(2026, 6, 7, 8, 0),
            ).map { it.toInstant(utc) }
        assertEquals(4, result.size, "7 days, dayInterval=2 → days 0,2,4,6 fire = 4 doses")
        expected.forEachIndexed { i, exp ->
            assertEquals(exp, result[i].scheduledAt, "skip-day dose[$i] must fall on the alternate day")
        }
    }

    // ADR-0008 mechanical bound: window > 30 days throws WindowTooLarge with the
    // requested-vs-max days in the typed payload.
    @Test
    fun `window exceeding 30 days throws WindowTooLarge with the offending days count`() {
        val schedule =
            Schedule(
                id = "sched-wtl",
                medicationId = "med-wtl",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-wtl",
                scheduleId = "sched-wtl",
                phaseOrder = 0,
                durationDays = 60,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
            )

        val thrown =
            assertFailsWith<MalformedScheduleException.WindowTooLarge> {
                calculator.computeScheduledDoses(
                    schedule = schedule,
                    phases = listOf(phase),
                    timeZone = utc,
                    fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                    toExclusive = baseDate.plusDays(31).atTime(0, 0).toInstant(utc),
                )
            }
        assertEquals(31L, thrown.requestedDays, "exception must report the actual requested days")
        assertEquals(30, thrown.maxDays, "exception must report the ADR-0008 cap")
        assertEquals("WindowTooLarge", thrown.code)
    }

    // EventCountExceeded is defense-in-depth, per-field caps make it unreachable from
    // legitimate input (max realistic = 30d × 6 doses × 1 phase = 180). We still cover its
    // construction path so the typed payload is exercised against future regressions.
    @Test
    fun `EventCountExceeded carries typed payload and stable code`() {
        val ex = MalformedScheduleException.EventCountExceeded(attemptedCount = 200_000L, maxCount = 100_000)
        assertEquals(200_000L, ex.attemptedCount)
        assertEquals(100_000, ex.maxCount)
        assertEquals("EventCountExceeded", ex.code)
        assertTrue(
            ex.message!!.contains("100000"),
            "message must mention the cap so an AI/UI mapper can present it",
        )
    }

    // Phase exhaustion: phases sum to fewer days than the schedule's effective range.
    // Per the KDoc "Phase exhaustion" clause: schedule ends at the last phase's last day. No
    // looping, no extension.
    @Test
    fun `phases shorter than schedule effective range terminates at last phase last day`() {
        val schedule =
            Schedule(
                id = "sched-exh",
                medicationId = "med-exh",
                startDate = baseDate, // 2026-06-01
                endDate = baseDate.plusDays(19), // 2026-06-20 inclusive; effective range = 20d
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
            )
        // ONLY 5 days of phase; the remaining 15 days have NO doses despite endDate=Jun20.
        val phase =
            SchedulePhase(
                id = "phase-exh",
                scheduleId = "sched-exh",
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
                toExclusive = baseDate.plusDays(25).atTime(0, 0).toInstant(utc),
            )

        assertEquals(
            10,
            result.size,
            "phase exhausts at 5 days × 2 doses = 10; phases must NOT loop or extend to endDate",
        )
        assertEquals(
            LocalDateTime(2026, 6, 5, 20, 0).toInstant(utc),
            result.last().scheduledAt,
            "last dose must be at the last phase's last day (Jun-5), not at the schedule's endDate (Jun-20)",
        )
    }

    // ADR-0007 v0.1 FOLLOW_PHONE TZ semantics. The wall-clock time stays at 8 AM local across
    // DST transitions; the UTC instant shifts by the DST offset. 2026 spring-forward in
    // America/Los_Angeles lands at 2 AM PST on Sun Mar 8 (the second Sunday of March), pushing
    // clocks to 3 AM PDT and the offset from UTC-8 to UTC-7. An 8 AM dose, well outside the
    // 2-3 AM gap, fires at 16:00 UTC on Mar 7 (PST) and at 15:00 UTC on Mar 8 onwards (PDT).
    //
    // Pinning this is medication-critical: an AI agent or future refactor that "helpfully"
    // normalizes everything to UTC would silently shift the Mar 8 dose to 7 AM PDT, an hour
    // earlier than the user expects. The wall-clock interpretation is the right one for
    // pet medications administered at human-routine times.
    //
    // STAY_HOME_TZ and ELAPSED_INTERVAL anchor modes (per ADR-0007) are explicitly out of scope
    // for v0.1 and will get their own test class in milestone 1.5.
    @Test
    fun `DST spring-forward, 8 AM dose stays at 8 AM local across the transition`() {
        val pt = TimeZone.of("America/Los_Angeles")
        val startDate = LocalDate(2026, 3, 7) // Saturday, the day before DST starts
        val schedule =
            Schedule(
                id = "sched-dst",
                medicationId = "med-dst",
                startDate = startDate,
                endDate = null,
                createdAt = Instant.parse("2026-03-06T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst",
                scheduleId = "sched-dst",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = startDate.atTime(0, 0).toInstant(pt),
                toExclusive = startDate.plusDays(5).atTime(0, 0).toInstant(pt),
            )

        assertEquals(5, result.size, "5 days x 1 dose/day = 5 events across the DST boundary")

        // Mar 7 is PST (UTC-8): 8 AM local = 16:00 UTC.
        assertEquals(
            Instant.parse("2026-03-07T16:00:00Z"),
            result[0].scheduledAt,
            "Mar 7 (PST) 8 AM local must fire at 16:00 UTC",
        )

        // Mar 8 onward is PDT (UTC-7): 8 AM local = 15:00 UTC.
        // The UTC instant shifted by an hour BUT the user-perceived wall-clock time did not.
        assertEquals(
            Instant.parse("2026-03-08T15:00:00Z"),
            result[1].scheduledAt,
            "Mar 8 (PDT, DST transition day) 8 AM local must fire at 15:00 UTC, " +
                "not at the pre-DST UTC instant. FOLLOW_PHONE semantics.",
        )
        assertEquals(Instant.parse("2026-03-09T15:00:00Z"), result[2].scheduledAt)
        assertEquals(Instant.parse("2026-03-10T15:00:00Z"), result[3].scheduledAt)
        assertEquals(Instant.parse("2026-03-11T15:00:00Z"), result[4].scheduledAt)
    }

    // DST gap case: a dose scheduled inside the non-existent 2-3 AM local hour on the
    // spring-forward day. Per kotlinx-datetime's documented resolver semantics (lesser /
    // earlier offset, "as if the time gap didn't occur yet"), the input "2:30 AM" on
    // 2026-03-08 in PT maps to instant 10:30 UTC (PST = UTC-8). At that UTC instant the
    // user's wall clock reads 3:30 AM PDT (because DST started at 10:00 UTC = 3:00 AM PDT).
    //
    // The user-visible consequence: a "2:30 AM" dose on the DST day fires at "3:30 AM" by
    // the user's clock. This is the documented behavior; pin it so a future refactor that
    // switches to "later offset" resolution (3:30 AM PDT = 10:30 UTC, firing at 2:30 AM
    // local on the dial, except 2:30 doesn't exist) cannot silently change behavior.
    //
    // Realistic toebeans use rarely schedules medications inside the 2-6 AM window, so the
    // practical hazard is low; the test exists to pin the documented contract.
    @Test
    fun `DST gap, 2_30 AM local on spring-forward day fires at the lesser-offset instant`() {
        val pt = TimeZone.of("America/Los_Angeles")
        val dstDay = LocalDate(2026, 3, 8) // spring-forward Sunday
        val schedule =
            Schedule(
                id = "sched-dst-gap",
                medicationId = "med-dst-gap",
                startDate = dstDay,
                endDate = null,
                createdAt = Instant.parse("2026-03-07T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-gap",
                scheduleId = "sched-dst-gap",
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(2, 30)), // inside the non-existent 2-3 AM PT hour
                doseAmount = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = dstDay.atTime(0, 0).toInstant(pt),
                toExclusive = dstDay.plusDays(1).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size, "single dose on the DST day even inside the gap")
        // 2:30 AM "PST" resolves via the earlier offset (UTC-8) to 10:30 UTC. At that UTC
        // instant the local clock has already advanced to 3:30 AM PDT.
        assertEquals(
            Instant.parse("2026-03-08T10:30:00Z"),
            result[0].scheduledAt,
            "2:30 AM input inside the DST gap must resolve via the lesser (PST) offset " +
                "per kotlinx-datetime's documented semantics. User's clock at 10:30 UTC " +
                "reads 3:30 AM PDT.",
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
