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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.toebeans.core.db.Schedule as ScheduleRow
import app.toebeans.core.db.SchedulePhase as SchedulePhaseRow

/**
 * SQLDelight-backed [ScheduleRepository] implementation. M1 step 3 (ScheduleRepository only).
 *
 * Backs every query against the generated `scheduleQueries` API from `Schedule.sq`.
 * `SqlDelightScheduleRepositoryContractTest` in `:shared:jvmTest` proves contract satisfaction.
 *
 * **AppModule wiring:** still on [app.toebeans.android.data.FakeScheduleRepository] as of this
 * PR — the Koin DI swap is a follow-up queue row after merge. Do not bind this class in
 * [app.toebeans.android.di.AppModule] until that follow-up lands.
 *
 * Threading matches [SqlDelightPetRepository]: suspending writes on [dispatcher]; Flow reads
 * via SQLDelight coroutine extensions with the same dispatcher.
 *
 * FK cascade: [delete] is hard delete on the schedule row; ADR-0010 CASCADE removes phases and
 * dose events. Parent Medication delete cascades to schedules — asserted in
 * [ScheduleRepositoryContract] case 11 via [MedicalRepositoryContract]'s `PRAGMA foreign_keys=ON`
 * setup, not in this repository's surface.
 *
 * Phase replace semantics: [upsert] replaces the schedule row and the **entire** phase list
 * atomically (delete-all-phases-for-schedule then re-insert), matching
 * [app.toebeans.android.data.FakeScheduleRepository].
 *
 * `dayInterval` is not persisted in `SchedulePhase` schema v0.1; [SchedulePhaseRow.toDomain]
 * defaults it to 1. Backup/restore and the calculator both use domain defaults until a migration
 * adds the column.
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
            val phasesByScheduleId = phaseRows.groupBy { it.schedule_id }
            scheduleRows.map { scheduleRow ->
                ScheduleWithPhases(
                    schedule = scheduleRow.toDomain(),
                    phases =
                        (phasesByScheduleId[scheduleRow.id] ?: emptyList())
                            .sortedBy { it.phase_order }
                            .map(SchedulePhaseRow::toDomain),
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
            validatePhases(schedule, phases)
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

private val doseTimesWireJson = Json

internal fun encodeDoseTimesLocal(times: List<LocalTime>): String =
    doseTimesWireJson.encodeToString(times.map { it.toString() })

internal fun decodeDoseTimesLocal(wire: String): List<LocalTime> =
    doseTimesWireJson.decodeFromString<List<String>>(wire).map(LocalTime::parse)

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
