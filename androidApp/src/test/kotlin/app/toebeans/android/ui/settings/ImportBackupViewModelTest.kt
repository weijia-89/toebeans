package app.toebeans.android.ui.settings

import app.toebeans.core.backup.BackupExport
import app.toebeans.core.backup.BackupImporter
import app.toebeans.core.backup.BackupSerializer
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ImportBackupViewModel]. Pure-JVM; the VM is intentionally Android-free.
 *
 * State-machine contract (ADR-0016):
 *   Idle → AwaitingConfirm(summary) → (cancel → Idle) | (confirm → Importing → Success | Error)
 *   Error and Success both clear via onAcknowledge → Idle.
 */
class ImportBackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-05-17T15:30:00Z")

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        val vm = newVm()
        assertEquals(ImportBackupUiState.Idle, vm.state.value)
    }

    @Test
    fun `stageFile with valid JSON transitions to AwaitingConfirm with counts`() =
        runTest(dispatcher) {
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val schedRepo = InMemoryScheduleRepo()
            val doseRepo = InMemoryDoseEventRepo()
            val vm = newVm(petRepo, medRepo, schedRepo, doseRepo)

            val payload =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = listOf(samplePet(id = "p1", name = "Luna")),
                    medications = emptyList(),
                    schedules = emptyList(),
                    schedulePhases = emptyList(),
                    doseEvents = emptyList(),
                )
            val bytes = BackupSerializer().encodeToString(payload).encodeToByteArray()

            vm.stageFile(bytes)
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(
                "expected AwaitingConfirm; got $state",
                state is ImportBackupUiState.AwaitingConfirm,
            )
            state as ImportBackupUiState.AwaitingConfirm
            assertEquals(1, state.pets)
            assertEquals(0, state.medications)
        }

    @Test
    fun `confirmImport applies merge-by-id and transitions to Success`() =
        runTest(dispatcher) {
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val schedRepo = InMemoryScheduleRepo()
            val doseRepo = InMemoryDoseEventRepo()
            val vm = newVm(petRepo, medRepo, schedRepo, doseRepo)

            // Pre-existing pet on the target device with the same id as one in the file.
            val existing = samplePet(id = "p-existing", name = "Existing")
            petRepo.upsert(existing)

            val fileVersionOfSamePet = samplePet(id = "p-existing", name = "Replaced-from-file")
            val newPet = samplePet(id = "p-new", name = "Luna")

            val payload =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = listOf(fileVersionOfSamePet, newPet),
                    medications = emptyList(),
                    schedules = emptyList(),
                    schedulePhases = emptyList(),
                    doseEvents = emptyList(),
                )
            val bytes = BackupSerializer().encodeToString(payload).encodeToByteArray()

            vm.stageFile(bytes)
            advanceUntilIdle()
            vm.confirmImport()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(
                "expected Success; got $state",
                state is ImportBackupUiState.Success,
            )
            state as ImportBackupUiState.Success
            assertEquals(1, state.summary.petsAdded)
            assertEquals(1, state.summary.petsSkipped)
            // Existing pet's name must NOT have been overwritten by the file row.
            assertEquals("Existing", petRepo.getById("p-existing")?.name)
            assertEquals("Luna", petRepo.getById("p-new")?.name)
        }

    @Test
    fun `stageFile with malformed JSON transitions to Error`() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.stageFile("this is not json".encodeToByteArray())
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(
                "expected Error; got $state",
                state is ImportBackupUiState.Error,
            )
        }

    @Test
    fun `onCancelConfirm returns to Idle without writing`() =
        runTest(dispatcher) {
            val petRepo = InMemoryPetRepo()
            val vm = newVm(petRepo)

            val payload =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = listOf(samplePet(id = "p-should-not-import", name = "Skipped")),
                    medications = emptyList(),
                    schedules = emptyList(),
                    schedulePhases = emptyList(),
                    doseEvents = emptyList(),
                )
            vm.stageFile(BackupSerializer().encodeToString(payload).encodeToByteArray())
            advanceUntilIdle()
            vm.onCancelConfirm()
            advanceUntilIdle()

            assertEquals(ImportBackupUiState.Idle, vm.state.value)
            assertEquals(null, petRepo.getById("p-should-not-import"))
        }

    @Test
    fun `onAcknowledge clears Success`() =
        runTest(dispatcher) {
            val vm = newVm()
            val payload =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = emptyList(),
                    medications = emptyList(),
                    schedules = emptyList(),
                    schedulePhases = emptyList(),
                    doseEvents = emptyList(),
                )
            vm.stageFile(BackupSerializer().encodeToString(payload).encodeToByteArray())
            advanceUntilIdle()
            vm.confirmImport()
            advanceUntilIdle()
            assertTrue(vm.state.value is ImportBackupUiState.Success)

            vm.onAcknowledge()
            assertEquals(ImportBackupUiState.Idle, vm.state.value)
        }

    // ---- fixtures ---------------------------------------------------------

    private fun newVm(
        petRepo: PetRepository = InMemoryPetRepo(),
        medRepo: MedicationRepository = InMemoryMedRepo(),
        schedRepo: ScheduleRepository = InMemoryScheduleRepo(),
        doseRepo: DoseEventRepository = InMemoryDoseEventRepo(),
    ): ImportBackupViewModel =
        ImportBackupViewModel(
            serializer = BackupSerializer(),
            importer = BackupImporter(petRepo, medRepo, schedRepo, doseRepo),
        )

    private fun samplePet(
        id: String,
        name: String,
    ): Pet =
        Pet(
            id = id,
            name = name,
            species = Species.CAT,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 4.0,
            notes = null,
            createdAt = now,
            archivedAt = null,
        )
}

// ---- In-test repository fakes (commonTest-equivalent for androidApp tests) -------

private class InMemoryPetRepo : PetRepository {
    private val state = MutableStateFlow<Map<String, Pet>>(emptyMap())

    override fun observeAll(): Flow<List<Pet>> = state.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: String): Pet? = state.value[id]

    override fun observeById(id: String): Flow<Pet?> = state.asStateFlow().map { it[id] }

    override suspend fun upsert(pet: Pet) {
        state.update { it + (pet.id to pet) }
    }

    override suspend fun delete(id: String) {
        state.update { it - id }
    }
}

private class InMemoryMedRepo : MedicationRepository {
    private val state = MutableStateFlow<Map<String, Medication>>(emptyMap())

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        state.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override fun observeAll(): Flow<List<Medication>> = state.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: String): Medication? = state.value[id]

    override suspend fun upsert(medication: Medication) {
        state.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        state.update { it - id }
    }
}

private class InMemoryScheduleRepo : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())
    private val phasesById = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phasesById.asStateFlow().map { snap -> snap[scheduleId] ?: emptyList() }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.asStateFlow().map { snap ->
            snap.values.map { sched -> ScheduleWithPhases(sched, phasesById.value[sched.id] ?: emptyList()) }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.asStateFlow().map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> =
        phasesById.asStateFlow().map { it.values.flatten() }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        schedules.update { it + (schedule.id to schedule) }
        phasesById.update { it + (schedule.id to phases) }
    }

    override suspend fun delete(id: String) {
        schedules.update { it - id }
        phasesById.update { it - id }
    }
}

private class InMemoryDoseEventRepo : DoseEventRepository {
    private val state = MutableStateFlow<Map<String, DoseEvent>>(emptyMap())

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> = state.asStateFlow().map { it.values.toList() }

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
        state.asStateFlow().map { snap ->
            snap.values.firstOrNull { it.medicationId == medicationId && it.status == DoseStatus.GIVEN }
        }

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
        state.asStateFlow().map { it.values.toList() }

    override fun observeAll(): Flow<List<DoseEvent>> = state.asStateFlow().map { it.values.toList() }

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent = error("unused in ImportBackupViewModelTest")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent = error("unused in ImportBackupViewModelTest")

    override suspend fun delete(doseEventId: String) {
        state.update { it - doseEventId }
    }

    override suspend fun upsert(event: DoseEvent) {
        state.update { it + (event.id to event) }
    }
}
