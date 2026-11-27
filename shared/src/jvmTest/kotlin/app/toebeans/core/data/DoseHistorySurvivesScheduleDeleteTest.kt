package app.toebeans.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.unconfinedTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test-as-spec for the operator decision of 2026-11-22: dose history must survive
 * [ScheduleRepository.delete].
 *
 * After `scheduleRepository.delete(scheduleId)`, the dose events recorded for that schedule
 * (GIVEN and PENDING alike) still exist in [DoseEventRepository.observeAll]. The History
 * tab is the record of what actually happened; deleting a wrong schedule must not erase it.
 *
 * The original schema used ON DELETE CASCADE on `schedule_id`, so this test failed on
 * purpose as decision-ready evidence. Migration 4 changed the column to nullable with
 * ON DELETE SET NULL and made this test green. Orphaned rows are invisible to the receiver
 * because the enriched lookup INNER JOINs Schedule, so keeping a pending row does not keep
 * an alarm for a deleted plan. Medication deletion still cascades, which preserves the
 * Remove demo data behavior.
 *
 * Lives in `:shared:jvmTest` like [SqlDelightScheduleRepositoryContractTest]: the JDBC
 * SQLite driver is not available in `commonTest`. FK enforcement follows ADR-0010:
 * `PRAGMA foreign_keys=ON` per connection so the real SQLite CASCADE fires.
 */
class DoseHistorySurvivesScheduleDeleteTest {
    @Test
    fun `dose history survives schedule delete`() =
        runTest {
            val fixture = seedFixture()

            fixture.scheduleRepository.delete(fixture.schedule.id)

            val surviving = fixture.doseEventRepository.observeAll().first()
            assertEquals(
                setOf(fixture.given.id, fixture.pending.id),
                surviving.map { it.id }.toSet(),
                "dose history for the deleted schedule must survive, not be cascade-wiped",
            )
        }

    @Test
    fun `deleted schedule nulls the dose row's schedule id and hides it from the receiver fire path`() =
        runTest {
            val fixture = seedFixture()
            val givenBefore =
                fixture.db.doseEventQueries
                    .selectDoseEventById(fixture.given.id)
                    .executeAsOne()
            assertEquals(
                fixture.schedule.id,
                givenBefore.schedule_id,
                "precondition: the given row must still point at the schedule",
            )

            fixture.scheduleRepository.delete(fixture.schedule.id)

            val givenAfter =
                fixture.db.doseEventQueries
                    .selectDoseEventById(fixture.given.id)
                    .executeAsOne()
            assertEquals(fixture.given.id, givenAfter.id, "history must keep the row, not just the count")
            assertNull(
                givenAfter.schedule_id,
                "ON DELETE SET NULL must detach the row instead of cascading it away",
            )
            assertEquals(fixture.given.medicationId, givenAfter.medication_id, "denormalized med id must survive")
            assertEquals(DoseStatus.GIVEN.wireName, givenAfter.status, "the recorded outcome must survive")
            val survivingIds =
                fixture.doseEventRepository
                    .observeAll()
                    .first()
                    .map { it.id }
                    .toSet()
            assertEquals(
                setOf(fixture.given.id, fixture.pending.id),
                survivingIds,
                "both history rows must still be readable after the schedule delete",
            )
            assertNull(
                fixture.db.doseEventQueries
                    .selectEnrichedReminderById(fixture.given.id)
                    .executeAsOneOrNull(),
                "an orphaned given row must not be delivered by the receiver fire path",
            )
            assertNull(
                fixture.db.doseEventQueries
                    .selectEnrichedReminderById(fixture.pending.id)
                    .executeAsOneOrNull(),
                "an orphaned pending row must not fire an alarm for a deleted schedule",
            )
        }

    private class Fixture(
        val db: ToebeansDatabase,
        val scheduleRepository: SqlDelightScheduleRepository,
        val doseEventRepository: SqlDelightDoseEventRepository,
        val schedule: Schedule,
        val given: DoseEvent,
        val pending: DoseEvent,
    )

    private suspend fun seedFixture(): Fixture {
        val db = freshDatabase()
        val createdAt = Instant.parse("2026-05-19T00:00:00Z")
        seedPetAndMedication(db, createdAt)
        val scheduleRepository =
            SqlDelightScheduleRepository(
                database = db,
                dispatcher = unconfinedTestDispatcher(),
            )
        val doseEventRepository =
            SqlDelightDoseEventRepository(
                database = db,
                dispatcher = unconfinedTestDispatcher(),
            )
        val schedule =
            Schedule(
                id = "sched-hist",
                medicationId = "m-hist",
                startDate = LocalDate.parse("2026-05-01"),
                endDate = null,
                createdAt = createdAt,
            )
        scheduleRepository.upsert(
            schedule,
            listOf(
                SchedulePhase(
                    id = "phase-hist",
                    scheduleId = schedule.id,
                    phaseOrder = 0,
                    durationDays = 7,
                    dosesPerDay = 2,
                    doseTimesLocal = listOf(LocalTime.parse("08:00"), LocalTime.parse("20:00")),
                    doseAmount = "1",
                    doseUnit = DoseUnit.MG,
                ),
            ),
        )
        val (given, pending) = sampleEvents(schedule.id)
        doseEventRepository.upsert(given)
        doseEventRepository.upsert(pending)
        return Fixture(db, scheduleRepository, doseEventRepository, schedule, given, pending)
    }

    private fun freshDatabase(): ToebeansDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        return ToebeansDatabase(driver)
    }

    private fun seedPetAndMedication(
        db: ToebeansDatabase,
        createdAt: Instant,
    ) {
        db.petQueries.insertPet(
            id = "p-hist",
            name = "History Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 1.0,
            notes = null,
            created_at = createdAt.toEpochMilliseconds(),
            archived_at = null,
        )
        db.medicationQueries.insertMedication(
            id = "m-hist",
            pet_id = "p-hist",
            name = "History Med",
            dose_amount = "1mg",
            dose_unit = "MG",
            notes = null,
            created_at = createdAt.toEpochMilliseconds(),
            discontinued_at = null,
        )
    }

    private fun sampleEvents(scheduleId: String): Pair<DoseEvent, DoseEvent> {
        val given =
            DoseEvent(
                id = "dose-hist-given",
                scheduleId = scheduleId,
                medicationId = "m-hist",
                scheduledAt = Instant.parse("2026-05-24T08:00:00Z"),
                firedAt = Instant.parse("2026-05-24T08:00:01Z"),
                resolvedAt = Instant.parse("2026-05-24T08:12:00Z"),
                status = DoseStatus.GIVEN,
                note = "breakfast dose",
            )
        val pending =
            DoseEvent(
                id = "dose-hist-pending",
                scheduleId = scheduleId,
                medicationId = "m-hist",
                scheduledAt = Instant.parse("2026-05-24T20:00:00Z"),
                firedAt = null,
                resolvedAt = null,
                status = DoseStatus.PENDING,
                note = null,
            )
        return given to pending
    }
}
