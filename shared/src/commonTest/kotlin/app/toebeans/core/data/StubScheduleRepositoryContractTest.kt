package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * commonTest harness fake: all 11 inherited contract cases pass without JDBC.
 * [SqlDelightScheduleRepositoryContractTest] (jvmTest) is the SQLDelight + FK cascade gate.
 * Kept (unlike deleted StubPetRepositoryContractTest) because harness verify and Robolectric
 * paths lack a JDBC SQLite driver in commonTest.
 *
 * Case 11 (Medication delete cascade) is behavioral simulation via [deleteAllForMedication];
 * ADR-0010 SQLite FK CASCADE is proven only on the SqlDelight subclass path.
 */
class StubScheduleRepositoryContractTest : ScheduleRepositoryContract() {
    private lateinit var inMemory: InMemoryContractScheduleRepository

    override fun obtainDriver(): SqlDriver? = null

    override fun createRepository(): ScheduleRepository {
        inMemory = InMemoryContractScheduleRepository()
        return inMemory
    }

    override suspend fun deleteParentMedication(medicationId: String) {
        inMemory.deleteAllForMedication(medicationId)
    }
}

/** In-memory [ScheduleRepository] satisfying [ScheduleRepositoryContract] for harness runs. */
private class InMemoryContractScheduleRepository : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())
    private val phasesByScheduleId = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phasesByScheduleId.map { snap ->
            (snap[scheduleId] ?: emptyList()).sortedBy(SchedulePhase::phaseOrder)
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        // sdk-review F5: combine both flows like SqlDelightScheduleRepository so phase-only updates re-emit.
        combine(schedules, phasesByScheduleId) { scheduleSnap, phaseSnap ->
            scheduleSnap.values
                .filter { it.endDate == null || it.endDate!! >= onOrAfter }
                .map { sched ->
                    ScheduleWithPhases(sched, phaseSnap[sched.id] ?: emptyList())
                }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = phasesByScheduleId.map { it.values.flatten() }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        validatePhasesForContract(schedule, phases)
        schedules.update { it + (schedule.id to schedule) }
        phasesByScheduleId.update { it + (schedule.id to phases) }
    }

    override suspend fun delete(id: String) {
        schedules.update { it - id }
        phasesByScheduleId.update { it - id }
    }

    // sdk-review F4: app-level cascade simulation; SqlDelight path proves ADR-0010 FK CASCADE.
    suspend fun deleteAllForMedication(medicationId: String) {
        val ids =
            schedules.value.values
                .filter { it.medicationId == medicationId }
                .map { it.id }
        ids.forEach { delete(it) }
    }
}

private fun validatePhasesForContract(
    schedule: Schedule,
    phases: List<SchedulePhase>,
) {
    val orders = phases.map { it.phaseOrder }.sorted()
    if (orders != orders.indices.toList()) {
        throw IllegalArgumentException(
            "phases must have dense phaseOrder 0..N-1; got $orders",
        )
    }
    if (phases.any { it.scheduleId != schedule.id }) {
        throw IllegalArgumentException("every phase.scheduleId must match schedule.id")
    }
}
