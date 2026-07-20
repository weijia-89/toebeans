package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.DoseUnit
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
 * jvmTest SqlDelight regression gate for [ScheduleRepositoryContract]: JDBC SQLite,
 * `PRAGMA foreign_keys=ON`, and real ADR-0010 CASCADE on case 11. The factory constructs
 * a freshly isolated [SqlDelightScheduleRepository] backed by an in-memory [JdbcSqliteDriver].
 *
 * [InMemoryScheduleRepositoryContractTest] (commonTest) runs the same 11 cases against an
 * in-memory fake for harness/Robolectric paths without JDBC; case 11 cascade there is
 * behavioral simulation only (see [ScheduleRepositoryContract] subclass roles).
 *
 * Lives in `:shared:jvmTest` for the same reason as [SqlDelightPetRepositoryContractTest]:
 * the JDBC SQLite driver is not available in `commonTest`.
 *
 * FK enforcement: [configureDb] enables `PRAGMA foreign_keys=ON` per ADR-0010 so case 11
 * (Medication delete CASCADE → Schedule + SchedulePhase) exercises real SQLite behavior.
 * [seedParentMedication] inserts the minimal Pet + Medication chain so schedule upserts satisfy
 * the `medication_id` FK without widening the abstract contract.
 *
 * [AppModule] remains on [app.toebeans.android.data.FakeScheduleRepository]; DI swap is a
 * follow-up queue row after merge.
 *
 * sdk-review F1: Phase 5/6 future tense removed; SqlDelight subclass is the green FK gate today.
 */
class SqlDelightScheduleRepositoryContractTest : ScheduleRepositoryContract() {
    private lateinit var database: ToebeansDatabase
    private lateinit var driver: SqlDriver

    override fun obtainDriver(): SqlDriver {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        database = ToebeansDatabase(driver)
        return driver
    }

    override fun configureDb(driver: SqlDriver) {
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    override fun createRepository(): ScheduleRepository {
        seedParentMedication()
        return SqlDelightScheduleRepository(
            database = database,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    override suspend fun deleteParentMedication(medicationId: String) {
        database.medicationQueries.deleteMedication(medicationId)
    }

    @Test
    fun `upsert update with new end date preserves dose event rows`() =
        runTest {
            val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            ToebeansDatabase.Schema.create(freshDriver)
            freshDriver.execute(null, "PRAGMA foreign_keys=ON", 0)
            val localDb = ToebeansDatabase(freshDriver)
            val createdAtMs = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
            localDb.petQueries.insertPet(
                id = "p-fk-sched",
                name = "FK Pet",
                species = "dog",
                birthdate_iso = null,
                weight_kg = 1.0,
                notes = null,
                created_at = createdAtMs,
                archived_at = null,
            )
            localDb.medicationQueries.insertMedication(
                id = "m-fk-sched",
                pet_id = "p-fk-sched",
                name = "FK Med",
                dose_amount = "1mg",
                dose_unit = "MG",
                notes = null,
                created_at = createdAtMs,
                discontinued_at = null,
            )
            val repo =
                SqlDelightScheduleRepository(
                    database = localDb,
                    dispatcher = Dispatchers.Unconfined,
                )
            val createdAt = Instant.parse("2026-05-19T00:00:00Z")
            val schedule =
                Schedule(
                    id = "sched-fk-update",
                    medicationId = "m-fk-sched",
                    startDate = LocalDate.parse("2026-05-01"),
                    endDate = null,
                    createdAt = createdAt,
                )
            val phase =
                SchedulePhase(
                    id = "phase-fk",
                    scheduleId = schedule.id,
                    phaseOrder = 0,
                    durationDays = 7,
                    dosesPerDay = 1,
                    doseTimesLocal = listOf(LocalTime.parse("08:00")),
                    doseAmount = "1mg",
                    doseUnit = DoseUnit.MG,
                )
            repo.upsert(schedule, listOf(phase))
            localDb.doseEventQueries.insertDoseEvent(
                id = "dose-fk-sched",
                schedule_id = schedule.id,
                medication_id = "m-fk-sched",
                scheduled_at = createdAt.toEpochMilliseconds(),
                fired_at = null,
                resolved_at = null,
                status = "pending",
                note = null,
            )
            repo.upsert(
                schedule.copy(endDate = LocalDate.parse("2026-06-30")),
                listOf(phase),
            )

            assertEquals(
                1,
                localDb.doseEventQueries
                    .selectAllDoseEvents()
                    .executeAsList()
                    .size,
            )
            assertEquals(
                LocalDate.parse("2026-06-30"),
                repo.observeById(schedule.id).first()?.endDate,
            )
        }

    private fun seedParentMedication() {
        val refCreatedAt = Instant.parse("2026-05-19T00:00:00Z")
        database.petQueries.insertPet(
            id = "p-contract",
            name = "Contract Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 1.0,
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            archived_at = null,
        )
        database.medicationQueries.insertMedication(
            id = "m1",
            pet_id = "p-contract",
            name = "Contract Med",
            dose_amount = "1mg",
            dose_unit = "MG",
            notes = null,
            created_at = refCreatedAt.toEpochMilliseconds(),
            discontinued_at = null,
        )
    }
}
