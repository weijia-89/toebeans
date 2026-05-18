package app.toebeans.android.ui.settings

import app.toebeans.core.backup.BackupAggregator
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ExportBackupViewModel]. Pure-JVM (no Robolectric) — the VM is designed
 * to avoid [android.net.Uri] so testing can stay synchronous.
 *
 * The VM accepts a `writeBytes: suspend (ByteArray) -> Unit` lambda at call-site, which
 * the production Composable wires to ContentResolver.openOutputStream(uri).write(bytes).
 * Tests pass a recording lambda to capture the bytes that would land in the SAF file.
 *
 * State-machine contract (ADR-0016): Idle → Writing → Success(summary) | Error(message);
 * `onAcknowledge` transitions back to Idle.
 */
class ExportBackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-05-17T15:30:00Z")
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = now
        }

    @Before
    fun setUp() {
        // viewModelScope dispatches Main; redirect to our test dispatcher so advance works.
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        val vm = newVm()
        assertEquals(ExportBackupUiState.Idle, vm.state.value)
    }

    @Test
    fun `suggestedFilename uses MMDDYYYY-HHMM in the system timezone`() {
        // Filename format per Q4 sign-off + ADR-0016. The clock returns a fixed UTC instant;
        // the conversion respects system timezone, which on the test JVM is whatever is set.
        // We just assert the shape and that the constants appear (extension, prefix).
        val vm = newVm()
        val filename = vm.suggestedFilename()
        assertTrue("filename was: $filename", filename.startsWith("toebeans-backup-"))
        assertTrue("filename was: $filename", filename.endsWith(".json"))
        // Format: toebeans-backup-MMDDYYYY-HHMM.json (8 digits + dash + 4 digits between
        // the prefix and the extension).
        val middle = filename.removePrefix("toebeans-backup-").removeSuffix(".json")
        assertTrue(
            "middle was: '$middle' (expected MMDDYYYY-HHMM)",
            middle.matches(Regex("\\d{8}-\\d{4}")),
        )
    }

    @Test
    fun `exportTo runs the aggregate-serialize-write pipeline and reaches Success`() =
        runTest(dispatcher) {
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()
            petRepo.upsert(samplePet("pet-1", "Mochi"))
            medRepo.upsert(sampleMed("med-1", "pet-1", "Methimazole"))

            var captured: ByteArray? = null
            val vm = newVm(petRepo, medRepo, scheduleRepo, doseEventRepo)
            vm.exportTo { bytes -> captured = bytes }
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue("state was: $state", state is ExportBackupUiState.Success)
            assertEquals(1, (state as ExportBackupUiState.Success).pets)
            assertEquals(1, state.medications)
            assertEquals(0, state.schedules)
            assertEquals(0, state.doseEvents)
            assertTrue(state.bytesWritten > 0)

            // Verify the bytes look like the expected JSON: pet name appears verbatim
            // (plain JSON per ADR-0016 — no encryption envelope).
            val text = captured!!.decodeToString()
            assertTrue("JSON was: $text", text.contains("\"name\":\"Mochi\""))
            assertTrue("JSON missing schemaVersion: $text", text.contains("\"schemaVersion\""))
        }

    @Test
    fun `exportTo transitions through Writing while running`() =
        runTest(dispatcher) {
            val vm = newVm()
            val writeGate = kotlinx.coroutines.CompletableDeferred<Unit>()

            // Start the export. The writeBytes lambda suspends on writeGate so we can
            // inspect the Writing state before the pipeline completes.
            vm.exportTo { writeGate.await() }
            // Let viewModelScope process the launch + the aggregator + serializer, but
            // stop before writeGate completes.
            runCurrent()

            assertEquals(ExportBackupUiState.Writing, vm.state.value)

            // Complete the write, drive the dispatcher, verify terminal state.
            writeGate.complete(Unit)
            advanceUntilIdle()
            assertTrue("final state was: ${vm.state.value}", vm.state.value is ExportBackupUiState.Success)
        }

    @Test
    fun `exportTo surfaces writer failure as Error state and leaves no Success`() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.exportTo { error("disk full") }
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue("state was: $state", state is ExportBackupUiState.Error)
            assertEquals("disk full", (state as ExportBackupUiState.Error).message)
        }

    @Test
    fun `onAcknowledge resets to Idle from Success`() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.exportTo { /* ok */ }
            advanceUntilIdle()
            assertTrue(vm.state.value is ExportBackupUiState.Success)

            vm.onAcknowledge()
            assertEquals(ExportBackupUiState.Idle, vm.state.value)
        }

    @Test
    fun `onAcknowledge resets to Idle from Error`() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.exportTo { error("nope") }
            advanceUntilIdle()
            assertTrue(vm.state.value is ExportBackupUiState.Error)

            vm.onAcknowledge()
            assertEquals(ExportBackupUiState.Idle, vm.state.value)
        }

    // ---- helpers ------------------------------------------------------------------

    private fun newVm(
        petRepo: PetRepository = InMemoryPetRepo(),
        medRepo: MedicationRepository = InMemoryMedRepo(),
        scheduleRepo: ScheduleRepository = InMemoryScheduleRepo(),
        doseEventRepo: DoseEventRepository = InMemoryDoseEventRepo(),
    ): ExportBackupViewModel =
        ExportBackupViewModel(
            aggregator = BackupAggregator(petRepo, medRepo, scheduleRepo, doseEventRepo),
            serializer = BackupSerializer(),
            clock = fixedClock,
            appVersion = "test-0.1.0",
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
            weightKg = 4.2,
            notes = null,
            createdAt = now,
            archivedAt = null,
        )

    private fun sampleMed(
        id: String,
        petId: String,
        name: String,
    ): Medication =
        Medication(
            id = id,
            petId = petId,
            name = name,
            doseAmount = "10mg",
            notes = null,
            createdAt = now,
            discontinuedAt = null,
        )
}

// ---- minimal in-test fakes (could be shared with BackupAggregatorTest later) -----

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

    override fun observeForPet(petId: String): Flow<List<Medication>> = state.asStateFlow().map { it.values.filter { m -> m.petId == petId } }

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
    private val phases = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> = schedules.asStateFlow().map { it.values.filter { s -> s.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> = phases.asStateFlow().map { it[scheduleId] ?: emptyList() }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.asStateFlow().map { snap ->
            snap.values.map { ScheduleWithPhases(it, phases.value[it.id] ?: emptyList()) }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.asStateFlow().map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = phases.asStateFlow().map { it.values.flatten() }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        schedules.update { it + (schedule.id to schedule) }
        this.phases.update { it + (schedule.id to phases) }
    }

    override suspend fun delete(id: String) {
        schedules.update { it - id }
        phases.update { it - id }
    }
}

private class InMemoryDoseEventRepo : DoseEventRepository {
    private val state = MutableStateFlow<Map<String, DoseEvent>>(emptyMap())

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> = state.asStateFlow().map { it.values.toList() }

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> = state.asStateFlow().map { it.values.firstOrNull { e -> e.medicationId == medicationId } }

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> = state.asStateFlow().map { it.values.toList() }

    override fun observeAll(): Flow<List<DoseEvent>> = state.asStateFlow().map { it.values.toList() }

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent {
        val event =
            DoseEvent(
                id = doseEventId,
                scheduleId = scheduleId,
                medicationId = medicationId,
                scheduledAt = at,
                firedAt = at,
                resolvedAt = at,
                status = DoseStatus.GIVEN,
                note = note,
            )
        state.update { it + (doseEventId to event) }
        return event
    }

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent {
        val event =
            DoseEvent(
                id = doseEventId,
                scheduleId = scheduleId,
                medicationId = medicationId,
                scheduledAt = scheduledAt,
                firedAt = resolvedAt,
                resolvedAt = resolvedAt,
                status = DoseStatus.GIVEN,
                note = note,
            )
        state.update { it + (doseEventId to event) }
        return event
    }

    override suspend fun delete(doseEventId: String) {
        state.update { it - doseEventId }
    }

    override suspend fun upsert(event: DoseEvent) {
        state.update { it + (event.id to event) }
    }
}
