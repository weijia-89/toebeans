package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Phase 5 concrete subclass of [ScheduleRepositoryContract] backed by a stub-throws
 * [ScheduleRepository]. Every inherited contract test fails RED on first run, which is the
 * deliverable of this PR per AGENTS.md § Test-as-spec rules: the human reviewer approves
 * the failing test runner output (assertions + signatures + cascade hook) before any impl
 * lands in Phase 6.
 *
 * [obtainDriver] returns null because the stub has no real driver. The inherited
 * `setupMedicalDb` therefore skips `configureDb`, matching the documented null-driver path
 * on [MedicalRepositoryContract].
 *
 * Phase 6 ships a sibling `SqlDelightScheduleRepositoryContractTest` whose factory returns
 * a real impl and whose [deleteParentMedication] override deletes the parent medication
 * row via raw driver execute (or a co-injected `MedicationRepository`) so the cascade test
 * actually exercises ADR-0010's FK behavior. Phase 6 may delete this stub subclass at that
 * point, matching the Phase-1 to Phase-2 pattern for [PetRepositoryContract].
 */
class StubScheduleRepositoryContractTest : ScheduleRepositoryContract() {
    override fun obtainDriver(): SqlDriver? = null

    override fun createRepository(): ScheduleRepository = StubScheduleRepository()

    override suspend fun deleteParentMedication(medicationId: String): Unit =
        throw UnsupportedOperationException(
            "StubScheduleRepositoryContractTest: deleteParentMedication is not supported " +
                "(Phase 6 SqlDelight subclass implements it via raw driver execute)",
        )
}

/**
 * Throws on every call. Phase 6 replaces this with the real SQLDelight-backed
 * implementation. The exception message names the phase that ships the green-path impl so
 * a reviewer reading the RED test output is not confused about why the failure exists.
 */
private class StubScheduleRepository : ScheduleRepository {
    private fun notImplemented(method: String): Nothing =
        throw NotImplementedError(
            "StubScheduleRepository.$method: not implemented (Phase 6 ships the real one)",
        )

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        notImplemented("observeForMedication")

    override fun observeById(id: String): Flow<Schedule?> = notImplemented("observeById")

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        notImplemented("observePhases")

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        notImplemented("observeActiveWithPhases")

    override fun observeAll(): Flow<List<Schedule>> = notImplemented("observeAll")

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = notImplemented("observeAllPhases")

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        notImplemented("upsert")
    }

    override suspend fun delete(id: String) {
        notImplemented("delete")
    }
}
