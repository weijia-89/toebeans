package app.toebeans.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import app.toebeans.core.db.Schedule as ScheduleRow
import app.toebeans.core.db.SchedulePhase as SchedulePhaseRow

/**
 * SQLDelight-backed [ScheduleRepository] implementation. M1 Decision 4a Phase 6.
 *
 * Backs every query against the generated `scheduleQueries` API from `Schedule.sq`.
 * The sibling [SqlDelightScheduleRepositoryContractTest] in `:shared:jvmTest` proves
 * it satisfies [ScheduleRepositoryContract].
 *
 * Threading matches [SqlDelightPetRepository]: suspending writes and one-shot reads run on
 * the injected [dispatcher]; Flow reads use SQLDelight's `asFlow()` extensions with the
 * same dispatcher.
 *
 * Phase list replacement on [upsert]: the fake repo overwrites the in-memory phase list;
 * this impl deletes all existing phases for the schedule id inside the same transaction,
 * then upserts the caller's dense 0..N-1 set. Orphan phase rows from a taper edit therefore
 * cannot survive a save.
 *
 * [dayInterval] is not persisted in v1 schema (`SchedulePhase` table has no column yet).
 * Reads default to `1` (daily dosing). A future migration will add the column per ADR-0008.
 *
 * FK cascade: [delete] is hard delete via `deleteSchedule`; ADR-0010 mandates
 * `PRAGMA foreign_keys=ON` on the platform driver so dependents cascade. Parent Medication
 * cascade is asserted in [ScheduleRepositoryContract] case 11, not here.
 */
public class SqlDelightScheduleRepository(
    private val database: ToebeansDatabase,
    private val dispatcher: CoroutineDispatcher,
) : ScheduleRepository {
    private val queries get() = database.scheduleQueries

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        queries
            .selectSchedulesForMedication(medicationId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(ScheduleRow::toDomain) }

    override fun observeById(id: String): Flow<Schedule?> =
        queries
            .selectScheduleById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        queries
            .selectPhasesForSchedule(scheduleId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(SchedulePhaseRow::toDomain) }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> {
        val onOrAfterIso = onOrAfter.toString()
        val schedulesFlow =
            queries
                .selectSchedulesActiveOnOrAfter(onOrAfterIso)
                .asFlow()
                .mapToList(dispatcher)
        val phasesFlow =
            queries
                .selectAllSchedulePhases()
                .asFlow()
                .mapToList(dispatcher)
        return combine(schedulesFlow, phasesFlow) { scheduleRows, phaseRows ->
            val phasesByScheduleId =
                phaseRows
                    .map(SchedulePhaseRow::toDomain)
                    .groupBy(SchedulePhase::scheduleId)
            scheduleRows.map { row ->
                ScheduleWithPhases(
                    schedule = row.toDomain(),
                    phases =
                        (phasesByScheduleId[row.id] ?: emptyList())
                            .sortedBy(SchedulePhase::phaseOrder),
                )
            }
        }
    }

    override fun observeAll(): Flow<List<Schedule>> =
        queries
            .selectAllSchedules()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(ScheduleRow::toDomain) }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> =
        queries
            .selectAllSchedulePhases()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(SchedulePhaseRow::toDomain) }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ): Unit =
        withContext(dispatcher) {
            validatePhases(schedule.id, phases)
            database.transaction {
                queries.upsertSchedule(
                    id = schedule.id,
                    medication_id = schedule.medicationId,
                    start_date_iso = schedule.startDate.toString(),
                    end_date_iso = schedule.endDate?.toString(),
                    created_at = schedule.createdAt.toEpochMilliseconds(),
                )
                queries.deletePhasesForSchedule(schedule.id)
                for (phase in phases) {
                    queries.upsertSchedulePhase(
                        id = phase.id,
                        schedule_id = phase.scheduleId,
                        phase_order = phase.phaseOrder.toLong(),
                        duration_days = phase.durationDays.toLong(),
                        doses_per_day = phase.dosesPerDay.toLong(),
                        dose_times_local = encodeDoseTimesLocal(phase.doseTimesLocal),
                        dose_amount = phase.doseAmount,
                    )
                }
            }
        }

    override suspend fun delete(id: String): Unit =
        withContext(dispatcher) {
            queries.deleteSchedule(id)
        }
}

private fun validatePhases(
    scheduleId: String,
    phases: List<SchedulePhase>,
) {
    val orders = phases.map { it.phaseOrder }.sorted()
    require(orders == orders.indices.toList()) {
        "phases must have dense phaseOrder 0..N-1; got $orders"
    }
    require(phases.all { it.scheduleId == scheduleId }) {
        "every phase.scheduleId must match schedule.id"
    }
}

private val doseTimesJson = Json

internal fun encodeDoseTimesLocal(times: List<LocalTime>): String {
    val wire =
        times.map { time ->
            "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
        }
    return doseTimesJson.encodeToString(ListSerializer(String.serializer()), wire)
}

internal fun decodeDoseTimesLocal(json: String): List<LocalTime> {
    val wire = doseTimesJson.decodeFromString(ListSerializer(String.serializer()), json)
    return wire.map(LocalTime::parse)
}

internal fun ScheduleRow.toDomain(): Schedule =
    Schedule(
        id = id,
        medicationId = medication_id,
        startDate = LocalDate.parse(start_date_iso),
        endDate = end_date_iso?.let(LocalDate::parse),
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

internal fun SchedulePhaseRow.toDomain(): SchedulePhase =
    SchedulePhase(
        id = id,
        scheduleId = schedule_id,
        phaseOrder = phase_order.toInt(),
        durationDays = duration_days.toInt(),
        dosesPerDay = doses_per_day.toInt(),
        doseTimesLocal = decodeDoseTimesLocal(dose_times_local),
        doseAmount = dose_amount,
        dayInterval = 1,
    )
