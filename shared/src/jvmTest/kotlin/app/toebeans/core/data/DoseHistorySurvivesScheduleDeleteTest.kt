package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RED TEST (test-as-spec; do NOT make green in this PR): dose history must survive
 * [ScheduleRepository.delete].
 *
 * Desired behavior pinned here: after `scheduleRepository.delete(scheduleId)`, the dose
 * events recorded for that schedule (GIVEN and PENDING alike) still exist in
 * [DoseEventRepository.observeAll]. The History tab is the record of what actually
 * happened; deleting a wrong schedule must not erase it.
 *
 * Current behavior (pinned green elsewhere, intentionally contradicted here):
 * `DoseEvent.sq` declares `FOREIGN KEY (schedule_id) REFERENCES Schedule(id) ON DELETE
 * CASCADE`, and with ADR-0010's `PRAGMA foreign_keys=ON` that cascade physically removes
 * every dose row for the schedule. That cascade is what makes this test fail today:
 * [SqliteForeignKeysCallbackTest] (androidApp) pins deleteSchedule -> dose rows gone, and
 * `ScheduleRepositoryContract` case 11 pins the medication-delete cascade.
 *
 * The contradiction the human test reviewer should see in one read: `DoseEvent.sq`'s own
 * header says `medication_id` is denormalized so retrospective surfaces survive "even if
 * the parent Schedule is deleted" and names SET NULL as the intended ON DELETE policy
 * ("M1 work"), yet the declared schema does the opposite on `schedule_id` (CASCADE, NOT
 * NULL). Whichever way the product decision lands, it belongs to the operator; this test
 * is decision-ready evidence, not the fix. If the decision is "history survives", the fix
 * is a vibe-dangerous schema migration (SET NULL + nullable `schedule_id`) under the
 * test-as-spec protocol in AGENTS.md.
 *
 * Lives in `:shared:jvmTest` like [SqlDelightScheduleRepositoryContractTest]: the JDBC
 * SQLite driver is not available in `commonTest`. FK enforcement follows ADR-0010:
 * `PRAGMA foreign_keys=ON` per connection so the real SQLite CASCADE fires.
 */
class DoseHistorySurvivesScheduleDeleteTest {
    private lateinit var database: ToebeansDatabase
    private lateinit var driver: SqlDriver

    @Test
    fun `dose history survives schedule delete`() =
        runTest {
            val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            ToebeansDatabase.Schema.create(freshDriver)
            freshDriver.execute(null, "PRAGMA foreign_keys=ON", 0)
            val localDb = ToebeansDatabase(freshDriver)
            val createdAt = Instant.parse("2026-05-19T00:00:00Z")
            localDb.petQueries.insertPet(
                id = "p-hist",
                name = "History Pet",
                species = "dog",
                birthdate_iso = null,
                weight_kg = 1.0,
                notes = null,
                created_at = createdAt.toEpochMilliseconds(),
                archived_at = null,
            )
            localDb.medicationQueries.insertMedication(
                id = "m-hist",
                pet_id = "p-hist",
                name = "History Med",
                dose_amount = "1mg",
                dose_unit = "MG",
                notes = null,
                created_at = createdAt.toEpochMilliseconds(),
                discontinued_at = null,
            )
            val scheduleRepository =
                SqlDelightScheduleRepository(
                    database = localDb,
                    dispatcher = Dispatchers.Unconfined,
                )
            val doseEventRepository =
                SqlDelightDoseEventRepository(
                    database = localDb,
                    dispatcher = Dispatchers.Unconfined,
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
            val givenEvent =
                DoseEvent(
                    id = "dose-hist-given",
                    scheduleId = schedule.id,
                    medicationId = "m-hist",
                    scheduledAt = Instant.parse("2026-05-24T08:00:00Z"),
                    firedAt = Instant.parse("2026-05-24T08:00:01Z"),
                    resolvedAt = Instant.parse("2026-05-24T08:12:00Z"),
                    status = DoseStatus.GIVEN,
                    note = "breakfast dose",
                )
            val pendingEvent =
                DoseEvent(
                    id = "dose-hist-pending",
                    scheduleId = schedule.id,
                    medicationId = "m-hist",
                    scheduledAt = Instant.parse("2026-05-24T20:00:00Z"),
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                )
            doseEventRepository.upsert(givenEvent)
            doseEventRepository.upsert(pendingEvent)

            scheduleRepository.delete(schedule.id)

            val surviving = doseEventRepository.observeAll().first()
            assertEquals(
                setOf(givenEvent.id, pendingEvent.id),
                surviving.map { it.id }.toSet(),
                "dose history for the deleted schedule must survive, not be cascade-wiped",
            )
        }
}
