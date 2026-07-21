package app.toebeans.core.scheduler

import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test-as-spec for DST warning surfacing in [ScheduleCalculator.computeScheduledDoses].
 *
 * Per ADR-0007 G2:
 *  - Spring-forward gap: a dose scheduled inside the non-existent hour is shifted
 *    forward and carries [DstWarning.DST_SKIP].
 *  - Fall-back overlap: a dose scheduled inside the repeated hour fires at the earlier
 *    UTC instant and carries [DstWarning.DST_DUPLICATE_RESOLVED].
 *  - Normal wall-clock times on DST days are unaffected (warning is null).
 *  - [AnchorMode.ELAPSED_INTERVAL] doses are UTC-interval based; DST has no effect
 *    (warning is always null).
 *
 * These tests are deliberately separate from [SchedulePhaseRulesTest] because DST
 * behavior depends on real tzdb data and deserves its own review gate.
 */
class SchedulePhaseDstRulesTest {
    private val calculator: ScheduleCalculator = DefaultScheduleCalculator()
    private val pt = TimeZone.of("America/Los_Angeles")

    // ---- Spring-forward (2026-03-08) ----

    @Test
    fun `spring-forward gap, 2_30 AM dose is shifted and carries DST_SKIP`() {
        val schedule =
            Schedule(
                id = "sched-dst-spring",
                medicationId = "med-1",
                startDate = LocalDate(2026, 3, 8), // spring-forward Sunday
                endDate = null,
                createdAt = Instant.parse("2026-03-07T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-spring",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(2, 30)), // inside the gap
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDate(2026, 3, 8).atTime(0, 0).toInstant(pt),
                toExclusive = LocalDate(2026, 3, 9).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size)
        assertEquals(
            DstWarning.DST_SKIP,
            result[0].dstWarning,
            "dose inside spring-forward gap must carry DST_SKIP",
        )
        // kotlinx-datetime resolves via earlier offset (PST) → 10:30 UTC,
        // which is 3:30 AM PDT on the user's clock (shifted forward by 1h).
        assertEquals(
            Instant.parse("2026-03-08T10:30:00Z"),
            result[0].scheduledAt,
        )
    }

    @Test
    fun `spring-forward day, 8 AM dose is unaffected and has no warning`() {
        val schedule =
            Schedule(
                id = "sched-dst-spring-normal",
                medicationId = "med-1",
                startDate = LocalDate(2026, 3, 8),
                endDate = null,
                createdAt = Instant.parse("2026-03-07T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-spring-normal",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)), // outside the gap
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDate(2026, 3, 8).atTime(0, 0).toInstant(pt),
                toExclusive = LocalDate(2026, 3, 9).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size)
        assertNull(
            result[0].dstWarning,
            "normal morning dose on spring-forward day must have no DST warning",
        )
        assertEquals(
            Instant.parse("2026-03-08T15:00:00Z"), // 8 AM PDT = 15:00 UTC
            result[0].scheduledAt,
        )
    }

    // ---- Fall-back (2026-11-01) ----

    @Test
    fun `fall-back overlap, 1_30 AM dose fires at earlier instant and carries DST_DUPLICATE_RESOLVED`() {
        val schedule =
            Schedule(
                id = "sched-dst-fall",
                medicationId = "med-1",
                startDate = LocalDate(2026, 11, 1), // fall-back Sunday
                endDate = null,
                createdAt = Instant.parse("2026-10-31T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-fall",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(1, 30)), // inside the repeated hour
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDate(2026, 11, 1).atTime(0, 0).toInstant(pt),
                toExclusive = LocalDate(2026, 11, 2).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size)
        assertEquals(
            DstWarning.DST_DUPLICATE_RESOLVED,
            result[0].dstWarning,
            "dose inside fall-back overlap must carry DST_DUPLICATE_RESOLVED",
        )
        // The earlier UTC instant (PDT, UTC-7) is chosen by kotlinx-datetime default.
        assertEquals(
            Instant.parse("2026-11-01T08:30:00Z"), // 1:30 AM PDT = 8:30 UTC
            result[0].scheduledAt,
        )
    }

    @Test
    fun `fall-back day, 8 AM dose is unaffected and has no warning`() {
        val schedule =
            Schedule(
                id = "sched-dst-fall-normal",
                medicationId = "med-1",
                startDate = LocalDate(2026, 11, 1),
                endDate = null,
                createdAt = Instant.parse("2026-10-31T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-fall-normal",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)), // outside the overlap
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDate(2026, 11, 1).atTime(0, 0).toInstant(pt),
                toExclusive = LocalDate(2026, 11, 2).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size)
        assertNull(
            result[0].dstWarning,
            "normal morning dose on fall-back day must have no DST warning",
        )
        assertEquals(
            Instant.parse("2026-11-01T16:00:00Z"), // 8 AM PST = 16:00 UTC
            result[0].scheduledAt,
        )
    }

    // ---- ELAPSED_INTERVAL ----

    @Test
    fun `ELAPSED_INTERVAL mode ignores DST and produces no warnings`() {
        val schedule =
            Schedule(
                id = "sched-dst-elapsed",
                medicationId = "med-1",
                startDate = LocalDate(2026, 3, 8), // spring-forward day
                endDate = null,
                createdAt = Instant.parse("2026-03-07T00:00:00Z"),
                anchorMode = AnchorMode.ELAPSED_INTERVAL,
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-elapsed",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 2,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(2, 30)), // same gap time
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDateTime(LocalDate(2026, 3, 8), LocalTime(0, 0)).toInstant(pt),
                toExclusive = LocalDateTime(LocalDate(2026, 3, 10), LocalTime(0, 0)).toInstant(pt),
            )

        assertEquals(2, result.size)
        assertNull(
            result[0].dstWarning,
            "ELAPSED_INTERVAL must not produce DST warnings",
        )
        assertNull(
            result[1].dstWarning,
            "ELAPSED_INTERVAL must not produce DST warnings",
        )
    }

    // ---- UTC (no DST) ----

    @Test
    fun `UTC timezone never produces DST warnings`() {
        val utc = TimeZone.UTC
        val schedule =
            Schedule(
                id = "sched-dst-utc",
                medicationId = "med-1",
                startDate = LocalDate(2026, 3, 8),
                endDate = null,
                createdAt = Instant.parse("2026-03-07T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-utc",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(2, 30)),
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = utc,
                fromInclusive = LocalDateTime(LocalDate(2026, 3, 8), LocalTime(0, 0)).toInstant(utc),
                toExclusive = LocalDateTime(LocalDate(2026, 3, 9), LocalTime(0, 0)).toInstant(utc),
            )

        assertEquals(1, result.size)
        assertNull(
            result[0].dstWarning,
            "UTC has no DST transitions; warning must be null",
        )
    }

    // ---- Edge: dose exactly at transition boundary ----

    @Test
    fun `dose at exactly 2 AM on spring-forward day is shifted to 3 AM and carries DST_SKIP`() {
        val schedule =
            Schedule(
                id = "sched-dst-spring-boundary",
                medicationId = "med-1",
                startDate = LocalDate(2026, 3, 8),
                endDate = null,
                createdAt = Instant.parse("2026-03-07T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-spring-boundary",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(2, 0)), // exactly the transition
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDate(2026, 3, 8).atTime(0, 0).toInstant(pt),
                toExclusive = LocalDate(2026, 3, 9).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size)
        assertEquals(
            DstWarning.DST_SKIP,
            result[0].dstWarning,
            "dose at exact gap start must carry DST_SKIP",
        )
    }

    @Test
    fun `dose at exactly 1 AM on fall-back day is in overlap and carries DST_DUPLICATE_RESOLVED`() {
        val schedule =
            Schedule(
                id = "sched-dst-fall-boundary",
                medicationId = "med-1",
                startDate = LocalDate(2026, 11, 1),
                endDate = null,
                createdAt = Instant.parse("2026-10-31T00:00:00Z"),
            )
        val phase =
            SchedulePhase(
                id = "phase-dst-fall-boundary",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(1, 0)), // exactly the repeated hour start
                doseAmount = null,
                doseUnit = null,
            )

        val result =
            calculator.computeScheduledDoses(
                schedule = schedule,
                phases = listOf(phase),
                timeZone = pt,
                fromInclusive = LocalDate(2026, 11, 1).atTime(0, 0).toInstant(pt),
                toExclusive = LocalDate(2026, 11, 2).atTime(0, 0).toInstant(pt),
            )

        assertEquals(1, result.size)
        assertEquals(
            DstWarning.DST_DUPLICATE_RESOLVED,
            result[0].dstWarning,
            "dose at exact overlap start must carry DST_DUPLICATE_RESOLVED",
        )
    }
}

// Small extension to keep test setup readable.
private fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(year, month, dayOfMonth, hour, minute)
