package app.toebeans.core.scheduler

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RED TEST (test-as-spec; do NOT make green in this PR): ADR-0007 travel mode.
 * Pins the DESIRED end state of the launch-checklist timezone row ("Pet.homeTimezone
 * plus the STAY_HOME_TZ caller policy", docs/launch-checklist.md section 3) and
 * `docs/issues/v0.1-followups.md` item 2 at the schema + domain-contract level.
 *
 * Desired end state pinned here, per ADR-0007 (Accepted, gates G1/G3 out of scope):
 *
 * 1. `Pet` carries an optional `home_timezone` column (ADR-0007 section Per-pet home
 *    timezone; null = use the system zone). `Pet.sq` has no such column today, which is
 *    exactly why this test is red. The `Pet.homeTimezone` model field and the
 *    repository mapping ride along with the implementation slice; the column is the
 *    contract the reviewer signs off on (vibe-dangerous migration per AGENTS.md).
 * 2. Caller policy: a schedule in [AnchorMode.STAY_HOME_TZ] computes its doses in the
 *    pet's home timezone, read from the persisted pet row, regardless of the device
 *    timezone. The device change is simulated through the domain layer only: the 72-hour
 *    style horizon is recomputed as if the device had moved from America/Los_Angeles to
 *    Asia/Tokyo. No OS broadcast is involved; the `ACTION_TIMEZONE_CHANGED` receiver
 *    integration test is a separate slice and stays out of scope here.
 * 3. Divergence: across the same device change a [AnchorMode.FOLLOW_PHONE] schedule
 *    DOES shift (08:00 becomes 08:00 in the new device zone), so the two modes cannot
 *    silently collapse into one behavior.
 *
 * Red style, chosen deliberately: a COMPILING test that fails on missing schema,
 * matching PR #155 (`DoseHistorySurvivesScheduleDeleteTest`) and the ADR-0020 precedent
 * (`VetVisitSchemaContractTest`). A domain-level `computeScheduledDoses(pet, ...)`
 * entry point that resolves the timezone internally does not exist yet, and inventing
 * one would add production code to a red-only PR. Until the implementation slice adds
 * it, the caller policy is expressed by reading the pet's persisted home timezone and
 * passing it as the calculator's `timeZone` argument (what the materialization layer
 * must do), pinned against the device-zone computation for the FOLLOW_PHONE case. After
 * implementation lands, the schema gate passes and the policy pins hold green.
 *
 * Lives in `:shared:jvmTest` like the SqlDelight contract tests: the JDBC SQLite driver
 * is not available in commonTest. Package `app.toebeans.core.scheduler` keeps it beside
 * `SchedulePhaseDstRulesTest` (commonTest), the other ADR-0007 test-as-spec class.
 * FK enforcement follows ADR-0010: `PRAGMA foreign_keys=ON` per connection.
 */
class TravelModeHomeTimezoneContractTest {
    private val calculator: ScheduleCalculator = DefaultScheduleCalculator()

    @Test
    fun `stay home tz doses stay on the pet's home timezone when the device timezone changes`() {
        val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = ToebeansDatabase(freshDriver)
        val createdAtMs = Instant.parse("2026-07-01T00:00:00Z").toEpochMilliseconds()
        val homeZone = seedSchema(freshDriver, db, createdAtMs)

        val staySchedule =
            Schedule(
                id = "sched-stay",
                medicationId = "m-tz",
                startDate = LocalDate(2026, 7, 6),
                endDate = null,
                createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                anchorMode = AnchorMode.STAY_HOME_TZ,
            )
        val phase =
            SchedulePhase(
                id = "phase-stay",
                scheduleId = "sched-stay",
                phaseOrder = 0,
                durationDays = 3,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
                doseUnit = null,
            )
        // One UTC-wide window covers every case below so dose counts stay comparable.
        val windowStart = Instant.parse("2026-07-05T00:00:00Z")
        val windowEnd = Instant.parse("2026-07-10T00:00:00Z")

        assertStayPins(staySchedule, phase, homeZone, windowStart, windowEnd)
        assertFollowPins(staySchedule, phase, windowStart, windowEnd)
    }

    private fun computeDoses(
        schedule: Schedule,
        phase: SchedulePhase,
        zone: TimeZone,
        start: Instant,
        end: Instant,
    ) = calculator.computeScheduledDoses(
        schedule = schedule,
        phases = listOf(phase),
        timeZone = zone,
        fromInclusive = start,
        toExclusive = end,
    )

    private fun assertStayPins(
        staySchedule: Schedule,
        phase: SchedulePhase,
        homeZone: TimeZone,
        windowStart: Instant,
        windowEnd: Instant,
    ) {
        // Policy pin: the materialization layer passes the pet's home timezone for a
        // STAY_HOME_TZ schedule. 08:00 in America/New_York (EDT) is 12:00 UTC.
        val stayDoses = computeDoses(staySchedule, phase, homeZone, windowStart, windowEnd)
        assertEquals(
            listOf(
                Instant.parse("2026-07-06T12:00:00Z"),
                Instant.parse("2026-07-07T12:00:00Z"),
                Instant.parse("2026-07-08T12:00:00Z"),
            ),
            stayDoses.map { it.scheduledAt },
            "STAY_HOME_TZ doses must be anchored to the pet's home timezone (08:00 New York)",
        )

        // Device-timezone change, simulated through the domain layer: the device moves
        // from America/Los_Angeles to Asia/Tokyo and the horizon is recomputed. The
        // STAY_HOME_TZ caller policy still passes the pet's home timezone, so the dose
        // instants are identical after the change.
        val stayDosesAfterChange = computeDoses(staySchedule, phase, homeZone, windowStart, windowEnd)
        assertEquals(
            stayDoses.map { it.scheduledAt },
            stayDosesAfterChange.map { it.scheduledAt },
            "a device timezone change must not move STAY_HOME_TZ dose instants",
        )
    }

    private fun assertFollowPins(
        staySchedule: Schedule,
        phase: SchedulePhase,
        windowStart: Instant,
        windowEnd: Instant,
    ) {
        // Divergence pin: the same pet's FOLLOW_PHONE schedule DOES follow the device.
        // Before the change 08:00 Los Angeles (PDT) is 15:00 UTC; after it 08:00 Tokyo
        // (JST) is 23:00 UTC the previous calendar day.
        val followSchedule = staySchedule.copy(id = "sched-follow", anchorMode = AnchorMode.FOLLOW_PHONE)
        val followPhase = phase.copy(id = "phase-follow", scheduleId = "sched-follow")
        val la = TimeZone.of("America/Los_Angeles")
        val tokyo = TimeZone.of("Asia/Tokyo")
        val followBefore = computeDoses(followSchedule, followPhase, la, windowStart, windowEnd)
        assertEquals(
            listOf(
                Instant.parse("2026-07-06T15:00:00Z"),
                Instant.parse("2026-07-07T15:00:00Z"),
                Instant.parse("2026-07-08T15:00:00Z"),
            ),
            followBefore.map { it.scheduledAt },
            "FOLLOW_PHONE doses follow the device timezone before the change",
        )
        val followAfter = computeDoses(followSchedule, followPhase, tokyo, windowStart, windowEnd)
        assertEquals(
            listOf(
                Instant.parse("2026-07-05T23:00:00Z"),
                Instant.parse("2026-07-06T23:00:00Z"),
                Instant.parse("2026-07-07T23:00:00Z"),
            ),
            followAfter.map { it.scheduledAt },
            "FOLLOW_PHONE doses shift with the device timezone after the change",
        )
    }

    private fun seedSchema(
        driver: SqlDriver,
        db: ToebeansDatabase,
        createdAtMs: Long,
    ): TimeZone {
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)

        // RED gate: ADR-0007 travel mode must add the optional home_timezone column to Pet.
        // Today Pet.sq declares no such column, so this assertion fails first and the
        // policy pins below execute only after the schema lands.
        assertTrue(
            "home_timezone" in columnNames(driver, "Pet"),
            "ADR-0007 travel-mode schema missing: Pet.home_timezone column does not exist yet",
        )

        // Persisted pet row with a home timezone. Raw SQL stands in for the generated
        // insertPet, which cannot know the column until the implementation slice adds it.
        driver.execute(
            null,
            """
            INSERT INTO Pet (id, name, species, birthdate_iso, weight_kg, notes, created_at, archived_at, home_timezone)
            VALUES ('p-tz', 'Travel Pet', 'dog', NULL, NULL, NULL, $createdAtMs, NULL, 'America/New_York')
            """.trimIndent(),
            0,
        )
        val homeZoneId = queryScalar(driver, "SELECT home_timezone FROM Pet WHERE id = 'p-tz'")
        assertEquals(
            "America/New_York",
            homeZoneId,
            "the pet's home timezone must persist and read back as the IANA zone id",
        )

        db.medicationQueries.insertMedication(
            id = "m-tz",
            pet_id = "p-tz",
            name = "Travel Med",
            dose_amount = "1mg",
            dose_unit = "MG",
            notes = null,
            created_at = createdAtMs,
            discontinued_at = null,
        )
        db.scheduleQueries.insertSchedule(
            id = "sched-stay",
            medication_id = "m-tz",
            start_date_iso = "2026-07-06",
            end_date_iso = null,
            created_at = createdAtMs,
            anchor_mode = AnchorMode.STAY_HOME_TZ.name,
        )
        db.scheduleQueries.upsertSchedulePhase(
            id = "phase-stay",
            schedule_id = "sched-stay",
            phase_order = 0,
            duration_days = 3,
            doses_per_day = 1,
            dose_times_local = """["08:00"]""",
            dose_amount = null,
            dose_unit = null,
        )
        return TimeZone.of(homeZoneId)
    }

    private fun columnNames(
        driver: SqlDriver,
        table: String,
    ): Set<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA table_info($table)",
                mapper = { cursor ->
                    QueryResult.Value(
                        buildSet {
                            while (cursor.next().value) add(cursor.getString(1)!!)
                        },
                    )
                },
                parameters = 0,
            ).value

    private fun queryScalar(
        driver: SqlDriver,
        sql: String,
    ): String =
        driver
            .executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    QueryResult.Value(
                        if (cursor.next().value) {
                            cursor.getString(0)!!
                        } else {
                            error("no row for: $sql")
                        },
                    )
                },
                parameters = 0,
            ).value
}
