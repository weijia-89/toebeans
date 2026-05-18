package app.toebeans.core.backup

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Test-as-spec for [BackupImporter]: the merge-by-id semantic.
 *
 * Contract:
 *  - For each entity in the [BackupExport], if no entity with that id exists in the
 *    target repository, insert it.
 *  - If an entity with the same id already exists, skip it (the existing row wins).
 *  - The returned [BackupImportSummary] reports per-entity-type added/skipped counts
 *    so the UI can show an honest "imported N pets, skipped M because they already
 *    exist" sentence per ADR-0016.
 *  - Schedules are imported atomically with their phases. Schedule phases for a
 *    skipped schedule are also skipped (we never overwrite phases of an existing
 *    schedule via import).
 *  - Pure-KMP. Tests run on JVM target with no Android dependency.
 */
class BackupImporterTest {
    private val now = Instant.parse("2026-05-17T12:00:00Z")

    @Test
    fun `import into empty repositories inserts everything`() =
        runTest {
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val pet = samplePet(id = "pet-rufus", name = "Rufus")
            val med = sampleMed(id = "med-1", petId = pet.id)
            val schedule = sampleSchedule(id = "sched-1", medicationId = med.id)
            val phase =
                SchedulePhase(
                    id = "phase-1",
                    scheduleId = schedule.id,
                    phaseOrder = 0,
                    durationDays = 30,
                    dosesPerDay = 1,
                    doseTimesLocal = listOf(LocalTime(8, 0)),
                    doseAmount = null,
                )
            val event = sampleEvent(id = "dose-1", scheduleId = schedule.id, medicationId = med.id)

            val backup =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = listOf(pet),
                    medications = listOf(med),
                    schedules = listOf(schedule),
                    schedulePhases = listOf(phase),
                    doseEvents = listOf(event),
                )

            val importer = BackupImporter(petRepo, medRepo, scheduleRepo, doseEventRepo)
            val summary = importer.import(backup)

            assertEquals(1, summary.petsAdded)
            assertEquals(0, summary.petsSkipped)
            assertEquals(1, summary.medicationsAdded)
            assertEquals(0, summary.medicationsSkipped)
            assertEquals(1, summary.schedulesAdded)
            assertEquals(0, summary.schedulesSkipped)
            assertEquals(1, summary.doseEventsAdded)
            assertEquals(0, summary.doseEventsSkipped)

            assertNotNull(petRepo.getById("pet-rufus"))
            assertNotNull(medRepo.getById("med-1"))
            assertEquals(1, scheduleRepo.observeAll().first().size)
            assertEquals(1, doseEventRepo.observeAll().first().size)
        }

    @Test
    fun `import skips entities whose ids already exist in target`() =
        runTest {
            // Coached merge-by-id semantic per ADR-0016: existing rows win, file rows
            // with matching ids are silently dropped. The user is informed via the
            // summary counts so they know how many "duplicates" the import declined.
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val existingPet = samplePet(id = "pet-rufus", name = "Rufus-existing")
            petRepo.upsert(existingPet)
            val fileVersionOfSamePet = samplePet(id = "pet-rufus", name = "Rufus-from-backup")

            val newPet = samplePet(id = "pet-luna", name = "Luna")

            val backup =
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

            val summary =
                BackupImporter(petRepo, medRepo, scheduleRepo, doseEventRepo)
                    .import(backup)

            assertEquals(1, summary.petsAdded, "Luna should be added")
            assertEquals(1, summary.petsSkipped, "Rufus already exists; file version is skipped")
            // Existing row MUST win: the stored Rufus's name is the pre-existing one,
            // not the version from the file.
            assertEquals("Rufus-existing", petRepo.getById("pet-rufus")?.name)
            assertEquals("Luna", petRepo.getById("pet-luna")?.name)
        }

    @Test
    fun `import skips phases of a schedule whose id already exists`() =
        runTest {
            // A schedule and its phases are imported atomically. If the schedule id is
            // already present, NEITHER the schedule NOR any of its file phases are
            // imported. This avoids leaving a stale schedule with new phases (which
            // would silently change the user's existing dosing) or new phases attached
            // to no schedule.
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val existingSchedule = sampleSchedule(id = "sched-existing", medicationId = "med-x")
            val existingPhase =
                SchedulePhase(
                    id = "phase-existing",
                    scheduleId = existingSchedule.id,
                    phaseOrder = 0,
                    durationDays = 30,
                    dosesPerDay = 1,
                    doseTimesLocal = listOf(LocalTime(9, 0)),
                    doseAmount = null,
                )
            scheduleRepo.upsert(existingSchedule, listOf(existingPhase))

            val filePhaseForExistingSchedule =
                SchedulePhase(
                    id = "phase-from-file",
                    scheduleId = existingSchedule.id,
                    phaseOrder = 0,
                    durationDays = 60,
                    dosesPerDay = 2,
                    doseTimesLocal = listOf(LocalTime(7, 0), LocalTime(19, 0)),
                    doseAmount = "2mg",
                )

            val backup =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = emptyList(),
                    medications = emptyList(),
                    schedules = listOf(existingSchedule),
                    schedulePhases = listOf(filePhaseForExistingSchedule),
                    doseEvents = emptyList(),
                )

            val summary =
                BackupImporter(petRepo, medRepo, scheduleRepo, doseEventRepo)
                    .import(backup)

            assertEquals(0, summary.schedulesAdded)
            assertEquals(1, summary.schedulesSkipped)

            val phasesAfter = scheduleRepo.observePhases(existingSchedule.id).first()
            assertEquals(1, phasesAfter.size, "phases of skipped schedule must remain unchanged")
            assertEquals("phase-existing", phasesAfter.single().id)
        }

    @Test
    fun `import inserts new schedule together with its file phases`() =
        runTest {
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val newSchedule = sampleSchedule(id = "sched-new", medicationId = "med-y")
            val phase1 =
                SchedulePhase(
                    id = "phase-new-0",
                    scheduleId = newSchedule.id,
                    phaseOrder = 0,
                    durationDays = 14,
                    dosesPerDay = 1,
                    doseTimesLocal = listOf(LocalTime(8, 0)),
                    doseAmount = "5mg",
                )
            val phase2 =
                SchedulePhase(
                    id = "phase-new-1",
                    scheduleId = newSchedule.id,
                    phaseOrder = 1,
                    durationDays = 14,
                    dosesPerDay = 1,
                    doseTimesLocal = listOf(LocalTime(8, 0)),
                    doseAmount = "2.5mg",
                )

            val backup =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                    exportedAt = now,
                    appVersion = "test",
                    pets = emptyList(),
                    medications = emptyList(),
                    schedules = listOf(newSchedule),
                    // Intentionally out-of-order to verify phaseOrder is preserved by data,
                    // not by list position.
                    schedulePhases = listOf(phase2, phase1),
                    doseEvents = emptyList(),
                )

            val summary =
                BackupImporter(petRepo, medRepo, scheduleRepo, doseEventRepo)
                    .import(backup)

            assertEquals(1, summary.schedulesAdded)
            val phasesStored = scheduleRepo.observePhases(newSchedule.id).first()
            assertEquals(2, phasesStored.size)
            // observePhases is contracted to sort by phaseOrder.
            assertEquals(0, phasesStored[0].phaseOrder)
            assertEquals(1, phasesStored[1].phaseOrder)
        }

    @Test
    fun `import rejects schemaVersion newer than current`() =
        runTest {
            val petRepo = InMemoryPetRepo()
            val backup =
                BackupExport(
                    schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION + 1,
                    exportedAt = now,
                    appVersion = "future",
                    pets = listOf(samplePet(id = "pet-1", name = "Future")),
                    medications = emptyList(),
                    schedules = emptyList(),
                    schedulePhases = emptyList(),
                    doseEvents = emptyList(),
                )

            try {
                BackupImporter(
                    petRepo,
                    InMemoryMedRepo(),
                    InMemoryScheduleRepo(),
                    InMemoryDoseEventRepo(),
                ).import(backup)
                error("Expected BackupFormatException for schemaVersion > current")
            } catch (e: BackupFormatException) {
                // OK
            }
            // Nothing should have been written.
            assertNull(petRepo.getById("pet-1"))
        }

    // ---- fixtures ----------------------------------------------------------

    private fun samplePet(
        id: String,
        name: String,
        archivedAt: Instant? = null,
    ): Pet =
        Pet(
            id = id,
            name = name,
            species = Species.DOG,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 12.0,
            notes = null,
            createdAt = now,
            archivedAt = archivedAt,
        )

    private fun sampleMed(
        id: String,
        petId: String,
    ): Medication =
        Medication(
            id = id,
            petId = petId,
            name = "Test-Med",
            doseAmount = "10mg",
            notes = null,
            createdAt = now,
            discontinuedAt = null,
        )

    private fun sampleSchedule(
        id: String,
        medicationId: String,
    ): Schedule =
        Schedule(
            id = id,
            medicationId = medicationId,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
            createdAt = now,
        )

    private fun sampleEvent(
        id: String,
        scheduleId: String,
        medicationId: String,
    ): DoseEvent =
        DoseEvent(
            id = id,
            scheduleId = scheduleId,
            medicationId = medicationId,
            scheduledAt = now,
            firedAt = now,
            resolvedAt = now,
            status = DoseStatus.GIVEN,
            note = null,
        )
}

// ---- In-test minimal repository fakes ---------------------------------------

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
        phasesById.asStateFlow().map { snap ->
            (snap[scheduleId] ?: emptyList()).sortedBy(SchedulePhase::phaseOrder)
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.asStateFlow().map { snap ->
            snap.values.map { sched -> ScheduleWithPhases(sched, phasesById.value[sched.id] ?: emptyList()) }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.asStateFlow().map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = phasesById.asStateFlow().map { it.values.flatten() }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        val orders = phases.map { it.phaseOrder }.sorted()
        require(orders == orders.indices.toList()) {
            "phases must have dense phaseOrder 0..N-1; got $orders"
        }
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
    ): Flow<List<DoseEvent>> =
        state.asStateFlow().map { snap ->
            snap.values.filter { it.scheduledAt >= sinceInclusive }
        }

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
        state.asStateFlow().map { snap ->
            snap.values
                .filter { it.medicationId == medicationId && it.status == DoseStatus.GIVEN }
                .maxByOrNull { it.scheduledAt }
        }

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
        state.asStateFlow().map { snap ->
            snap.values.filter { it.scheduledAt >= sinceInclusive && it.status == DoseStatus.GIVEN }
        }

    override fun observeAll(): Flow<List<DoseEvent>> = state.asStateFlow().map { it.values.toList() }

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent = error("unused in BackupImporterTest")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent = error("unused in BackupImporterTest")

    override suspend fun delete(doseEventId: String) {
        state.update { it - doseEventId }
    }

    override suspend fun upsert(event: DoseEvent) {
        state.update { it + (event.id to event) }
    }
}
