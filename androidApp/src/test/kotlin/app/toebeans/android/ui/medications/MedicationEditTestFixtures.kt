package app.toebeans.android.ui.medications

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

// Shared fixtures for the `MedicationEditViewModel*` test family.

internal fun seedMed(
    id: String,
    petId: String,
    name: String,
): Medication =
    Medication(
        id = id,
        petId = petId,
        name = name,
        doseAmount = "2.5 mg",
        notes = null,
        createdAt = Clock.System.now(),
        discontinuedAt = null,
    )

internal fun seedPet(
    id: String,
    name: String,
): Pet =
    Pet(
        id = id,
        name = name,
        species = Species.CAT,
        birthdate = null,
        weightKg = null,
        notes = null,
        createdAt = Clock.System.now(),
        archivedAt = null,
    )

internal class InMemoryMedRepo(
    initial: Medication? = null,
) : MedicationRepository {
    private val store = MutableStateFlow(if (initial != null) mapOf(initial.id to initial) else emptyMap())

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override fun observeAll(): Flow<List<Medication>> = store.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: String): Medication? = store.value[id]

    override suspend fun upsert(medication: Medication) {
        store.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }
}

internal class InMemoryPetRepo(
    private val pet: Pet,
) : PetRepository {
    override fun observeAll(): Flow<List<Pet>> = flowOf(listOf(pet))

    override fun observeById(id: String): Flow<Pet?> = flowOf(if (id == pet.id) pet else null)

    override suspend fun getById(id: String): Pet? = if (id == pet.id) pet else null

    override suspend fun upsert(pet: Pet) = Unit

    override suspend fun delete(id: String) = Unit
}

internal class InMemoryScheduleRepo(
    initialSchedules: List<Schedule> = emptyList(),
    initialPhases: List<SchedulePhase> = emptyList(),
) : ScheduleRepository {
    private val schedules = initialSchedules.toMutableList()
    private val phases = initialPhases.toMutableList()

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        flowOf(schedules.filter { it.medicationId == medicationId })

    override fun observeById(id: String): Flow<Schedule?> = flowOf(schedules.find { it.id == id })

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        flowOf(phases.filter { it.scheduleId == scheduleId })

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<Schedule>> = flowOf(schedules.toList())

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = flowOf(phases.toList())

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        schedules.removeAll { it.id == schedule.id }
        schedules.add(schedule)
        phases.forEach { p -> this.phases.removeAll { it.id == p.id } }
        this.phases.addAll(phases)
    }

    override suspend fun delete(id: String) {
        schedules.removeAll { it.id == id }
        phases.removeAll { it.scheduleId == id }
    }
}

internal fun medicationEditViewModel(
    medRepo: MedicationRepository,
    pet: Pet = seedPet("pet-1", "Luna"),
    scheduleRepo: ScheduleRepository = InMemoryScheduleRepo(),
): MedicationEditViewModel =
    MedicationEditViewModel(
        medicationRepository = medRepo,
        petRepository = InMemoryPetRepo(pet),
        scheduleRepository = scheduleRepo,
    )
