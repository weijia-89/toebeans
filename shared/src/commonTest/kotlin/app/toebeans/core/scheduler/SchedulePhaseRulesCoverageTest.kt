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
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Continuation of the [ScheduleCalculator] test-as-spec contract. Split from
 * [SchedulePhaseRulesTest] only to keep each class under detekt's LargeClass cap.
 */
class SchedulePhaseRulesCoverageTest {
    private val calculator: ScheduleCalculator = DefaultScheduleCalculator()

    // 2026-06-01 is chosen so that no part of the test window crosses a DST boundary in either
    // America/Los_Angeles or America/New_York. DST handling is its own test class.
    private val utc = TimeZone.UTC
    private val baseDate: LocalDate = LocalDate(2026, 6, 1)

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
                doseUnit = null,
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
                doseUnit = null,
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
                doseUnit = null,
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
                doseUnit = null,
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

    // ADR-0007 ELAPSED_INTERVAL anchor mode. Doses are spaced at fixed UTC intervals
    // from the first dose, independent of wall-clock time or DST transitions.
    //
    // In FOLLOW_PHONE mode (the v0.1 default), the Mar 8 08:00 dose shifts to 15:00 UTC
    // because the phone switched from PST (UTC-8) to PDT (UTC-7). In ELAPSED_INTERVAL
    // mode the UTC instant must stay fixed at the 12h cadence from the first dose:
    // 16:00 UTC, 04:00 UTC, 16:00 UTC, ... regardless of DST.
    @Test
    fun `ELAPSED_INTERVAL anchor mode spaces doses at fixed UTC intervals from first dose`() {
        val pt = TimeZone.of("America/Los_Angeles")
        val startDate = LocalDate(2026, 3, 7) // Saturday, day before DST
        val schedule =
            Schedule(
                id = "sched-elapsed",
                medicationId = "med-elapsed",
                startDate = startDate,
                endDate = null,
                createdAt = Instant.parse("2026-03-06T00:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        val phase =
            SchedulePhase(
                id = "phase-elapsed",
                scheduleId = "sched-elapsed",
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = startDate.atTime(0, 0).toInstant(pt),
                toExclusive = startDate.plusDays(5).atTime(0, 0).toInstant(pt),
            )

        assertEquals(10, result.size, "5 days x 2 doses/day = 10 events")

        // First dose: Mar 7 08:00 PST = 16:00 UTC
        assertEquals(Instant.parse("2026-03-07T16:00:00Z"), result[0].scheduledAt)

        // Second dose: exactly 12h later = Mar 8 04:00 UTC
        assertEquals(Instant.parse("2026-03-08T04:00:00Z"), result[1].scheduledAt)

        // Third dose: exactly 12h after second = Mar 8 16:00 UTC
        // In FOLLOW_PHONE this would be Mar 8 08:00 PDT = 15:00 UTC (DST shifted).
        // In ELAPSED_INTERVAL it must stay at 16:00 UTC = 09:00 PDT (1h later wall-clock).
        assertEquals(
            Instant.parse("2026-03-08T16:00:00Z"),
            result[2].scheduledAt,
            "ELAPSED_INTERVAL must preserve the UTC interval across DST; " +
                "FOLLOW_PHONE would shift to 15:00 UTC due to PDT offset change",
        )

        // Remaining doses continue at 12h intervals
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), result[3].scheduledAt)
        assertEquals(Instant.parse("2026-03-09T16:00:00Z"), result[4].scheduledAt)
        assertEquals(Instant.parse("2026-03-10T04:00:00Z"), result[5].scheduledAt)
        assertEquals(Instant.parse("2026-03-10T16:00:00Z"), result[6].scheduledAt)
        assertEquals(Instant.parse("2026-03-11T04:00:00Z"), result[7].scheduledAt)
        assertEquals(Instant.parse("2026-03-11T16:00:00Z"), result[8].scheduledAt)
        assertEquals(Instant.parse("2026-03-12T04:00:00Z"), result[9].scheduledAt)
    }

    // ADR-0007 ELAPSED_INTERVAL endDate cap. The effective endDate must cap doses even in
    // elapsed-interval mode; doses must not continue past the inclusive endDate.
    @Test
    fun `ELAPSED_INTERVAL respects endDate capping`() {
        val schedule =
            Schedule(
                id = "sched-elapsed-cap",
                medicationId = "med-elapsed-cap",
                startDate = baseDate,
                endDate = baseDate.plusDays(2), // inclusive: Jun 1, 2, 3
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        val phase =
            SchedulePhase(
                id = "phase-elapsed-cap",
                scheduleId = "sched-elapsed-cap",
                phaseOrder = 0,
                durationDays = 5, // would be 10 doses without cap
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(10).atTime(0, 0).toInstant(utc),
            )

        assertEquals(6, result.size, "endDate caps at 3 days x 2 doses = 6 events")
        assertEquals(
            LocalDateTime(baseDate.plusDays(2), LocalTime(20, 0)).toInstant(utc),
            result.last().scheduledAt,
            "last dose must be on the inclusive endDate",
        )
    }

    // ADR-0007 ELAPSED_INTERVAL multi-phase concatenation. Phase N+1 starts immediately after
    // phase N's last dose, using phase N+1's own gap pattern.
    @Test
    fun `ELAPSED_INTERVAL multi-phase concatenation uses next phase gap pattern`() {
        val schedule =
            Schedule(
                id = "sched-elapsed-multi",
                medicationId = "med-elapsed-multi",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        // Phase 0: 2 days BID [08:00, 20:00] → gaps [12h, 12h]
        val phase0 =
            SchedulePhase(
                id = "phase-elapsed-multi-0",
                scheduleId = "sched-elapsed-multi",
                phaseOrder = 0,
                durationDays = 2,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = "10",
                doseUnit = null,
            )
        // Phase 1: 2 days TID [09:00, 15:00, 21:00] → gaps [6h, 6h, 12h]
        val phase1 =
            SchedulePhase(
                id = "phase-elapsed-multi-1",
                scheduleId = "sched-elapsed-multi",
                phaseOrder = 1,
                durationDays = 2,
                dosesPerDay = 3,
                doseTimesLocal = listOf(LocalTime(9, 0), LocalTime(15, 0), LocalTime(21, 0)),
                doseAmount = "5",
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase0, phase1),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(5).atTime(0, 0).toInstant(utc),
            )

        assertEquals(10, result.size, "4 + 6 = 10 doses across two phases")

        // Phase 0 doses
        assertEquals(LocalDateTime(baseDate, LocalTime(8, 0)).toInstant(utc), result[0].scheduledAt)
        assertEquals(LocalDateTime(baseDate, LocalTime(20, 0)).toInstant(utc), result[1].scheduledAt)
        assertEquals(LocalDateTime(baseDate.plusDays(1), LocalTime(8, 0)).toInstant(utc), result[2].scheduledAt)
        assertEquals(LocalDateTime(baseDate.plusDays(1), LocalTime(20, 0)).toInstant(utc), result[3].scheduledAt)

        // Phase 1 handoff: last dose of phase0 = day2 20:00; + gap0(6h) = day3 02:00
        assertEquals(
            LocalDateTime(baseDate.plusDays(2), LocalTime(2, 0)).toInstant(utc),
            result[4].scheduledAt,
            "phase 1 first dose must use phase 1's gap[0] from phase 0's last dose",
        )
        assertEquals(1, result[4].phaseOrder)
        assertEquals("5", result[4].doseAmount)

        // Continue cycling through phase 1 gaps:
        // gap[0]=6h → day3 08:00, gap[1]=6h → day3 14:00, gap[2]=12h → day4 02:00,
        // gap[0]=6h → day4 08:00, gap[1]=6h → day4 14:00
        assertEquals(LocalDateTime(baseDate.plusDays(2), LocalTime(8, 0)).toInstant(utc), result[5].scheduledAt)
        assertEquals(LocalDateTime(baseDate.plusDays(2), LocalTime(14, 0)).toInstant(utc), result[6].scheduledAt)
        assertEquals(LocalDateTime(baseDate.plusDays(3), LocalTime(2, 0)).toInstant(utc), result[7].scheduledAt)
        assertEquals(LocalDateTime(baseDate.plusDays(3), LocalTime(8, 0)).toInstant(utc), result[8].scheduledAt)
        assertEquals(LocalDateTime(baseDate.plusDays(3), LocalTime(14, 0)).toInstant(utc), result[9].scheduledAt)
    }

    // ADR-0007 ELAPSED_INTERVAL rejects dayInterval != 1. Skip-day dosing is semantically
    // incompatible with fixed UTC intervals.
    @Test
    fun `ELAPSED_INTERVAL rejects dayInterval not equal to 1`() {
        val schedule =
            Schedule(
                id = "sched-elapsed-di",
                medicationId = "med-elapsed-di",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        val phase =
            SchedulePhase(
                id = "phase-elapsed-di",
                scheduleId = "sched-elapsed-di",
                phaseOrder = 0,
                durationDays = 3,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
                doseUnit = null,
                dayInterval = 2,
            )

        val thrown =
            assertFailsWith<MalformedScheduleException.ElapsedIntervalDayIntervalUnsupported> {
                calculator.computeScheduledDoses(
                    schedule = schedule,
                    phases = listOf(phase),
                    timeZone = utc,
                    fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                    toExclusive = baseDate.plusDays(10).atTime(0, 0).toInstant(utc),
                )
            }
        assertEquals("phase-elapsed-di", thrown.phaseId)
        assertEquals(2, thrown.dayInterval)
        assertEquals("ElapsedIntervalDayIntervalUnsupported", thrown.code)
    }

    // ADR-0007 ELAPSED_INTERVAL QD: single dose per day. Wrap-around gap must be 24h.
    @Test
    fun `ELAPSED_INTERVAL with QD spaces doses every 24 hours`() {
        val schedule =
            Schedule(
                id = "sched-elapsed-qd",
                medicationId = "med-elapsed-qd",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        val phase =
            SchedulePhase(
                id = "phase-elapsed-qd",
                scheduleId = "sched-elapsed-qd",
                phaseOrder = 0,
                durationDays = 3,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(5).atTime(0, 0).toInstant(utc),
            )

        assertEquals(3, result.size, "3 days x 1 dose = 3 events")
        assertEquals(
            LocalDateTime(baseDate, LocalTime(8, 0)).toInstant(utc),
            result[0].scheduledAt,
        )
        assertEquals(
            LocalDateTime(baseDate.plusDays(1), LocalTime(8, 0)).toInstant(utc),
            result[1].scheduledAt,
        )
        assertEquals(
            LocalDateTime(baseDate.plusDays(2), LocalTime(8, 0)).toInstant(utc),
            result[2].scheduledAt,
        )
    }

    // ADR-0007 ELAPSED_INTERVAL TID with uneven gaps. Verifies wrap-around gap arithmetic
    // and gap cycling across day boundaries.
    @Test
    fun `ELAPSED_INTERVAL with TID uneven gaps cycles correctly`() {
        val schedule =
            Schedule(
                id = "sched-elapsed-tid",
                medicationId = "med-elapsed-tid",
                startDate = baseDate,
                endDate = null,
                createdAt = Instant.parse("2026-05-31T12:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        val phase =
            SchedulePhase(
                id = "phase-elapsed-tid",
                scheduleId = "sched-elapsed-tid",
                phaseOrder = 0,
                durationDays = 2,
                dosesPerDay = 3,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(12, 0), LocalTime(20, 0)),
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = baseDate.atTime(0, 0).toInstant(utc),
                toExclusive = baseDate.plusDays(3).atTime(0, 0).toInstant(utc),
            )

        assertEquals(6, result.size, "2 days x 3 doses = 6 events")
        // Gaps: [4h, 8h, 12h]
        val expected =
            listOf(
                LocalDateTime(baseDate, LocalTime(8, 0)),
                LocalDateTime(baseDate, LocalTime(12, 0)),
                LocalDateTime(baseDate, LocalTime(20, 0)),
                LocalDateTime(baseDate.plusDays(1), LocalTime(8, 0)),
                LocalDateTime(baseDate.plusDays(1), LocalTime(12, 0)),
                LocalDateTime(baseDate.plusDays(1), LocalTime(20, 0)),
            ).map { it.toInstant(utc) }
        expected.forEachIndexed { i, exp ->
            assertEquals(exp, result[i].scheduledAt, "TID uneven dose[$i]")
        }
    }
}

// Same small extensions as SchedulePhaseRulesTest, kept local to this compilation unit.
private fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)

private fun LocalDate.atTime(
    hour: Int,
    minute: Int,
): LocalDateTime = LocalDateTime(year, month, dayOfMonth, hour, minute)
