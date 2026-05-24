package app.toebeans.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.Medication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import app.toebeans.core.db.Medication as MedicationRow

/**
 * SQLDelight-backed [MedicationRepository] implementation. M1 step 3 (Medication slice).
 *
 * Threading and ordering match [SqlDelightPetRepository]. [observeForPet] and [observeAll]
 * use `ORDER BY name COLLATE NOCASE` per `Medication.sq`. Returns active and discontinued
 * medications alike; UI filters on [Medication.discontinuedAt] when needed.
 */
public class SqlDelightMedicationRepository(
    private val database: ToebeansDatabase,
    private val dispatcher: CoroutineDispatcher,
) : MedicationRepository {
    private val queries get() = database.medicationQueries

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        queries
            .selectMedicationsForPet(petId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(MedicationRow::toDomain) }

    override fun observeAll(): Flow<List<Medication>> =
        queries
            .selectAllMedications()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(MedicationRow::toDomain) }

    override suspend fun getById(id: String): Medication? =
        withContext(dispatcher) {
            queries.selectMedicationById(id).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun upsert(medication: Medication): Unit =
        withContext(dispatcher) {
            queries.upsertMedication(
                id = medication.id,
                pet_id = medication.petId,
                name = medication.name,
                dose_amount = medication.doseAmount,
                notes = medication.notes,
                created_at = medication.createdAt.toEpochMilliseconds(),
                discontinued_at = medication.discontinuedAt?.toEpochMilliseconds(),
            )
        }

    override suspend fun delete(id: String): Unit =
        withContext(dispatcher) {
            queries.deleteMedication(id)
        }
}

internal fun MedicationRow.toDomain(): Medication =
    Medication(
        id = id,
        petId = pet_id,
        name = name,
        doseAmount = dose_amount,
        notes = notes,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        discontinuedAt = discontinued_at?.let(Instant::fromEpochMilliseconds),
    )
