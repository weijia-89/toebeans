package app.toebeans.core.scheduler

import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.HomeTimezone
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test-as-spec for the pure timezone policy: callers resolve a zone from the persisted
 * pet plus the device zone, then hand it to the unchanged pure [ScheduleCalculator].
 */
class TimeZonePolicyTest {
    private val calculator: ScheduleCalculator = DefaultScheduleCalculator()
    private val tokyo = TimeZone.of("Asia/Tokyo")

    private fun pet(zoneId: String?): Pet =
        Pet(
            id = "p-tz",
            name = "TZ Pet",
            species = Species.DOG,
            birthdate = null,
            weightKg = null,
            notes = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            archivedAt = null,
            homeTimezone = zoneId,
        )

    private fun schedule(mode: AnchorMode): Schedule =
        Schedule(
            id = "s-tz",
            medicationId = "m-tz",
            startDate = LocalDate(2026, 7, 6),
            endDate = null,
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
            anchorMode = mode,
        )

    private fun phase(time: LocalTime = LocalTime(8, 0)): SchedulePhase =
        SchedulePhase(
            id = "ph-tz",
            scheduleId = "s-tz",
            phaseOrder = 0,
            durationDays = 3,
            dosesPerDay = 1,
            doseTimesLocal = listOf(time),
            doseAmount = null,
            doseUnit = null,
        )

    private val windowStart = Instant.parse("2026-07-05T00:00:00Z")
    private val windowEnd = Instant.parse("2026-07-10T00:00:00Z")

    @Test
    fun `STAY_HOME_TZ resolves the pet home zone regardless of device zone`() {
        val resolution = TimeZonePolicy.resolve(schedule(AnchorMode.STAY_HOME_TZ), pet("America/New_York"), tokyo)

        assertTrue(resolution is TimeZonePolicy.Resolution.Use)
        assertEquals(TimeZone.of("America/New_York"), resolution.timeZone)
        val doses =
            calculator.computeScheduledDoses(
                schedule = schedule(AnchorMode.STAY_HOME_TZ),
                phases = listOf(phase()),
                timeZone = resolution.timeZone,
                fromInclusive = windowStart,
                toExclusive = windowEnd,
            )
        assertEquals(
            listOf(
                Instant.parse("2026-07-06T12:00:00Z"),
                Instant.parse("2026-07-07T12:00:00Z"),
                Instant.parse("2026-07-08T12:00:00Z"),
            ),
            doses.map { it.scheduledAt },
        )
    }

    @Test
    fun `FOLLOW_PHONE resolves the device zone and ELAPSED_INTERVAL is deterministic`() {
        val follow = TimeZonePolicy.resolve(schedule(AnchorMode.FOLLOW_PHONE), pet("America/New_York"), tokyo)
        val elapsed = TimeZonePolicy.resolve(schedule(AnchorMode.ELAPSED_INTERVAL), pet(null), tokyo)

        assertTrue(follow is TimeZonePolicy.Resolution.Use)
        assertEquals(tokyo, follow.timeZone)
        assertTrue(elapsed is TimeZonePolicy.Resolution.Use)
        assertEquals(TimeZone.UTC, elapsed.timeZone)
    }

    @Test
    fun `null home timezone falls back to the device zone for STAY_HOME_TZ`() {
        val resolution = TimeZonePolicy.resolve(schedule(AnchorMode.STAY_HOME_TZ), pet(null), tokyo)

        assertTrue(resolution is TimeZonePolicy.Resolution.Use)
        assertEquals(tokyo, resolution.timeZone)
    }

    @Test
    fun `invalid stored zone id quarantines instead of falling back to the device zone`() {
        val resolution = TimeZonePolicy.resolve(schedule(AnchorMode.STAY_HOME_TZ), pet("Mars/Olympus_Mons"), tokyo)

        assertEquals(TimeZonePolicy.Resolution.Quarantined, resolution)
        assertTrue(HomeTimezone.isQuarantined(pet("Mars/Olympus_Mons")))
        assertFalse(HomeTimezone.isQuarantined(pet(null)))
        assertFalse(HomeTimezone.isQuarantined(pet("America/New_York")))
        assertFalse(HomeTimezone.isValid("Mars/Olympus_Mons"))
        assertTrue(HomeTimezone.isValid("America/New_York"))
    }

    @Test
    fun `spring-forward 02_30 in the home zone with device in Tokyo carries DST_SKIP`() {
        val home = TimeZone.of("America/New_York")
        val result =
            calculator.computeScheduledDoses(
                schedule =
                    schedule(AnchorMode.STAY_HOME_TZ).copy(
                        id = "s-spring",
                        startDate = LocalDate(2026, 3, 8),
                    ),
                phases =
                    listOf(
                        phase(LocalTime(2, 30)).copy(
                            id = "ph-spring",
                            scheduleId = "s-spring",
                            durationDays = 1,
                        ),
                    ),
                timeZone = home,
                fromInclusive = Instant.parse("2026-03-07T00:00:00Z"),
                toExclusive = Instant.parse("2026-03-10T00:00:00Z"),
            )

        assertEquals(1, result.size, "02:30 on spring-forward day resolves once, not twice")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), result.single().scheduledAt)
        assertEquals(DstWarning.DST_SKIP, result.single().dstWarning)
    }

    @Test
    fun `fall-back 01_30 in the home zone with device in Tokyo fires once at the earlier instant`() {
        val home = TimeZone.of("America/New_York")
        val result =
            calculator.computeScheduledDoses(
                schedule =
                    schedule(AnchorMode.STAY_HOME_TZ).copy(
                        id = "s-fall",
                        startDate = LocalDate(2026, 11, 1),
                    ),
                phases =
                    listOf(
                        phase(LocalTime(1, 30)).copy(
                            id = "ph-fall",
                            scheduleId = "s-fall",
                            durationDays = 1,
                        ),
                    ),
                timeZone = home,
                fromInclusive = Instant.parse("2026-10-31T00:00:00Z"),
                toExclusive = Instant.parse("2026-11-03T00:00:00Z"),
            )

        assertEquals(1, result.size, "01:30 on fall-back day must not double-dose")
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), result.single().scheduledAt)
        assertEquals(DstWarning.DST_DUPLICATE_RESOLVED, result.single().dstWarning)
    }

    @Test
    fun `72 hour horizon boundary is half-open and stable across device zone changes`() {
        val home = TimeZone.of("America/New_York")
        val now = Instant.parse("2026-07-06T11:59:59Z")
        val horizonEnd = now + 72.hours
        val edgeSchedule = schedule(AnchorMode.STAY_HOME_TZ).copy(id = "s-edge")
        val edgePhase =
            phase(LocalTime(12, 0)).copy(
                id = "ph-edge",
                scheduleId = "s-edge",
                durationDays = 4,
            )

        val atStart =
            calculator.computeScheduledDoses(
                schedule = edgeSchedule,
                phases = listOf(edgePhase),
                timeZone = home,
                fromInclusive = now,
                toExclusive = horizonEnd,
            )
        assertTrue(atStart.isNotEmpty(), "a dose at the window start must be included")

        val oneMsShort =
            calculator.computeScheduledDoses(
                schedule = edgeSchedule,
                phases = listOf(edgePhase),
                timeZone = home,
                fromInclusive = now,
                toExclusive = horizonEnd - 1.milliseconds,
            )
        assertEquals(atStart.map { it.scheduledAt }, oneMsShort.map { it.scheduledAt })

        val excludedBoundary =
            calculator.computeScheduledDoses(
                schedule = edgeSchedule,
                phases = listOf(edgePhase),
                timeZone = tokyo,
                fromInclusive = Instant.parse("2026-07-09T12:00:00Z"),
                toExclusive = Instant.parse("2026-07-09T12:00:01Z"),
            )
        assertTrue(
            excludedBoundary.none { it.scheduledAt == Instant.parse("2026-07-09T12:00:00Z") },
            "a dose at the exclusive upper bound must not be materialized",
        )
    }
}
