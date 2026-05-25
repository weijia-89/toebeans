package app.toebeans.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import app.toebeans.core.db.DoseEvent as DoseEventRow

/**
 * SQLDelight-backed [DoseEventRepository]. M1 step 3 remainder (DoseEvent slice).
 *
 * Backs every query against the generated `doseEventQueries` API from `DoseEvent.sq`.
 * [SqlDelightDoseEventRepositoryContractTest] in `:shared:jvmTest` proves insert →
 * [selectDoseEventById][app.toebeans.core.db.DoseEventQueries.selectDoseEventById] visibility
 * for the receiver fire path.
 *
 * **Receiver path ordering (v0.1-followups §3).** [DoseAlarmReceiver][app.toebeans.android.notifications.DoseAlarmReceiver]
 * resolves firing alarms via [app.toebeans.core.notifications.SqlDelightReminderLookup] over the
 * same `toebeans.db` file as this repository. Callers MUST INSERT dose rows here before
 * [app.toebeans.core.notifications.NotificationActuator.schedule] so the receiver can load the
 * row by id at fire time.
 *
 * **AppModule wiring:** bound in [app.toebeans.android.di.AppModule] together with
 * [SqlDelightPetRepository], [SqlDelightMedicationRepository], and [SqlDelightScheduleRepository]
 * so FK targets exist at write time (ADR-0010).
 *
 * Threading matches sibling SQLDelight repos: suspending writes on [dispatcher]; Flow reads via
 * SQLDelight coroutine extensions with the same dispatcher.
 */
public class SqlDelightDoseEventRepository(
    private val database: ToebeansDatabase,
    private val dispatcher: CoroutineDispatcher,
) : DoseEventRepository {
    private val queries get() = database.doseEventQueries

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> =
        queries
            .selectDoseEventsForPetSince(petId, sinceInclusive.toEpochMilliseconds())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(DoseEventRow::toDomain) }

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
        queries
            .selectLastGivenForMedication(medicationId)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
        queries
            .selectAllGivenSince(sinceInclusive.toEpochMilliseconds())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows
                    .map(DoseEventRow::toDomain)
                    .take(OBSERVE_ALL_RECENT_LIMIT)
            }

    override fun observeAll(): Flow<List<DoseEvent>> =
        queries
            .selectAllDoseEvents()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(DoseEventRow::toDomain) }

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent =
        withContext(dispatcher) {
            val event =
                DoseEvent(
                    id = doseEventId,
                    scheduleId = scheduleId,
                    medicationId = medicationId,
                    scheduledAt = at,
                    firedAt = null,
                    resolvedAt = at,
                    status = DoseStatus.GIVEN,
                    note = note,
                )
            queries.insertDoseEvent(
                id = event.id,
                schedule_id = event.scheduleId,
                medication_id = event.medicationId,
                scheduled_at = event.scheduledAt.toEpochMilliseconds(),
                fired_at = event.firedAt?.toEpochMilliseconds(),
                resolved_at = event.resolvedAt?.toEpochMilliseconds(),
                status = event.status.wireName,
                note = event.note,
            )
            event
        }

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent =
        withContext(dispatcher) {
            val existing =
                queries
                    .selectGivenForScheduleSlot(
                        schedule_id = scheduleId,
                        scheduled_at = scheduledAt.toEpochMilliseconds(),
                    ).executeAsOneOrNull()
            val event =
                DoseEvent(
                    id = existing?.id ?: doseEventId,
                    scheduleId = scheduleId,
                    medicationId = medicationId,
                    scheduledAt = scheduledAt,
                    firedAt = existing?.fired_at?.let(Instant::fromEpochMilliseconds),
                    resolvedAt = resolvedAt,
                    status = DoseStatus.GIVEN,
                    note = note ?: existing?.note,
                )
            queries.upsertDoseEvent(
                id = event.id,
                schedule_id = event.scheduleId,
                medication_id = event.medicationId,
                scheduled_at = event.scheduledAt.toEpochMilliseconds(),
                fired_at = event.firedAt?.toEpochMilliseconds(),
                resolved_at = event.resolvedAt?.toEpochMilliseconds(),
                status = event.status.wireName,
                note = event.note,
            )
            event
        }

    override suspend fun delete(doseEventId: String): Unit =
        withContext(dispatcher) {
            queries.deleteDoseEvent(doseEventId)
        }

    override suspend fun upsert(event: DoseEvent): Unit =
        withContext(dispatcher) {
            queries.upsertDoseEvent(
                id = event.id,
                schedule_id = event.scheduleId,
                medication_id = event.medicationId,
                scheduled_at = event.scheduledAt.toEpochMilliseconds(),
                fired_at = event.firedAt?.toEpochMilliseconds(),
                resolved_at = event.resolvedAt?.toEpochMilliseconds(),
                status = event.status.wireName,
                note = event.note,
            )
        }
}

private const val OBSERVE_ALL_RECENT_LIMIT = 50

internal fun DoseEventRow.toDomain(): DoseEvent =
    DoseEvent(
        id = id,
        scheduleId = schedule_id,
        medicationId = medication_id,
        scheduledAt = Instant.fromEpochMilliseconds(scheduled_at),
        firedAt = fired_at?.let(Instant::fromEpochMilliseconds),
        resolvedAt = resolved_at?.let(Instant::fromEpochMilliseconds),
        status = DoseStatus.fromWireName(status),
        note = note,
    )
