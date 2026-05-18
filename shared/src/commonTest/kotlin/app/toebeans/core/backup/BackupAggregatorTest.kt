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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test-as-spec for [BackupAggregator]: the data fan-in from the four repository contracts
 * into a single [BackupExport] snapshot. ADR-0016 specifies plain JSON export at v1; the
 * aggregator is the source of truth for what lands in that JSON.
 *
 * The aggregator's contract:
 *  - Reads `observeAll()` from each repository once (single emission via [Flow.first]).
 *  - Includes pets/medications/schedules/phases/doseEvents in full — NO filtering by
 *    archived/discontinued/ended status. The user wants the entire local-state snapshot
 *    in the backup, including archived pets, discontinued meds, and ended schedules;
 *    "active only" semantics are a UI concern.
 *  - `schemaVersion` is always `BackupExport.CURRENT_SCHEMA_VERSION`.
 *  - `exportedAt` and `appVersion` come from the caller (Settings → Export VM).
 *
 * Tests use minimal in-test fakes (not the androidApp Fake repos) so this module stays
 * pure-KMP and tests run on the JVM target.
 */
class BackupAggregatorTest {
    private val now = Instant.parse("2026-05-17T12:00:00Z")

    @Test
    fun `collect returns empty BackupExport when all repositories are empty`() =
        runTest {
            val aggregator =
                BackupAggregator(
                    petRepository = InMemoryPetRepo(),
                    medicationRepository = InMemoryMedRepo(),
                    scheduleRepository = InMemoryScheduleRepo(),
                    doseEventRepository = InMemoryDoseEventRepo(),
                )

            val export =
                aggregator.collect(
                    appVersion = "test-0.1.0",
                    exportedAt = now,
                )

            assertEquals(BackupExport.CURRENT_SCHEMA_VERSION, export.schemaVersion)
            assertEquals(now, export.exportedAt)
            assertEquals("test-0.1.0", export.appVersion)
            assertEquals(emptyList(), export.pets)
            assertEquals(emptyList(), export.medications)
            assertEquals(emptyList(), export.schedules)
            assertEquals(emptyList(), export.schedulePhases)
            assertEquals(emptyList(), export.doseEvents)
        }

    @Test
    fun `collect captures every entity from every repository`() =
        runTest {
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val pet = samplePet(id = "pet-rufus", name = "Rufus")
            val med = sampleMed(id = "med-apoquel", petId = pet.id, name = "Apoquel", doseAmount = "5.4mg")
            val schedule = sampleSchedule(id = "sched-rufus-apoquel", medicationId = med.id)
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
            val event =
                DoseEvent(
                    id = "dose-1",
                    scheduleId = schedule.id,
                    medicationId = med.id,
                    scheduledAt = now,
                    firedAt = now,
                    resolvedAt = now,
                    status = DoseStatus.GIVEN,
                    note = null,
                )

            petRepo.upsert(pet)
            medRepo.upsert(med)
            scheduleRepo.upsert(schedule, listOf(phase))
            doseEventRepo.put(event)

            val aggregator = BackupAggregator(petRepo, medRepo, scheduleRepo, doseEventRepo)
            val export = aggregator.collect(appVersion = "test-0.1.0", exportedAt = now)

            assertEquals(1, export.pets.size)
            assertEquals("Rufus", export.pets.single().name)
            assertEquals(1, export.medications.size)
            assertEquals("Apoquel", export.medications.single().name)
            assertEquals(1, export.schedules.size)
            assertEquals("sched-rufus-apoquel", export.schedules.single().id)
            assertEquals(1, export.schedulePhases.size)
            assertEquals(0, export.schedulePhases.single().phaseOrder)
            assertEquals(1, export.doseEvents.size)
            assertEquals(DoseStatus.GIVEN, export.doseEvents.single().status)
        }

    @Test
    fun `collect includes archived pets, discontinued medications, and ended schedules`() =
        runTest {
            // ADR-0016: backup snapshot is full local state, not "active only". A user who
            // archives a pet or discontinues a med and then exports must find them in the
            // file so import on a new device restores the full history.
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val archivedPet = samplePet(id = "pet-luna", name = "Luna", archivedAt = now)
            val discontinuedMed =
                sampleMed(
                    id = "med-discontinued",
                    petId = archivedPet.id,
                    name = "OldMed",
                    discontinuedAt = now,
                )
            val endedSchedule =
                Schedule(
                    id = "sched-ended",
                    medicationId = discontinuedMed.id,
                    startDate = LocalDate(2025, 1, 1),
                    endDate = LocalDate(2025, 6, 1),
                    createdAt = now,
                )
            val phase =
                SchedulePhase(
                    id = "phase-ended",
                    scheduleId = endedSchedule.id,
                    phaseOrder = 0,
                    durationDays = 30,
                    dosesPerDay = 2,
                    doseTimesLocal = listOf(LocalTime(7, 0), LocalTime(19, 0)),
                    doseAmount = null,
                )

            petRepo.upsert(archivedPet)
            medRepo.upsert(discontinuedMed)
            scheduleRepo.upsert(endedSchedule, listOf(phase))

            val export =
                BackupAggregator(petRepo, medRepo, scheduleRepo, doseEventRepo)
                    .collect(appVersion = "test", exportedAt = now)

            assertEquals(1, export.pets.size, "archived pet MUST appear in backup")
            assertEquals(now, export.pets.single().archivedAt)
            assertEquals(1, export.medications.size, "discontinued medication MUST appear in backup")
            assertEquals(now, export.medications.single().discontinuedAt)
            assertEquals(1, export.schedules.size, "ended schedule MUST appear in backup")
            assertEquals(LocalDate(2025, 6, 1), export.schedules.single().endDate)
        }

    @Test
    fun `collect output roundtrips through BackupSerializer with field equality`() =
        runTest {
            // End-to-end contract: aggregator → JSON → decode → equality. If this passes,
            // the file the user exports decodes back to an identical BackupExport on
            // import. This is the load-bearing test for the entire export/import flow.
            val petRepo = InMemoryPetRepo()
            val medRepo = InMemoryMedRepo()
            val scheduleRepo = InMemoryScheduleRepo()
            val doseEventRepo = InMemoryDoseEventRepo()

            val pet = samplePet(id = "pet-1", name = "Fido")
            petRepo.upsert(pet)

            val aggregator = BackupAggregator(petRepo, medRepo, scheduleRepo, doseEventRepo)
            val exported = aggregator.collect(appVersion = "test-0.1.0", exportedAt = now)
            val serializer = BackupSerializer()
            val json = serializer.encodeToString(exported)
            val decoded = serializer.decodeFromString(json)

            assertEquals(exported, decoded, "aggregator output must roundtrip through BackupSerializer")
            assertTrue(
                json.contains("\"name\":\"Fido\""),
                "JSON must contain pet name verbatim (plain JSON per ADR-0016); was: $json",
            )
        }

    // ---- fixture helpers ----------------------------------------------------------

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
        name: String,
        doseAmount: String = "10mg",
        discontinuedAt: Instant? = null,
    ): Medication =
        Medication(
            id = id,
            petId = petId,
            name = name,
            doseAmount = doseAmount,
            notes = null,
            createdAt = now,
            discontinuedAt = discontinuedAt,
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
}

// ---- In-test minimal fakes (commonTest-local; do not depend on androidApp) ---------

private class InMemoryPetRepo : PetRepository {
    private val state = MutableStateFlow<Map<String, Pet>>(emptyMap())

    override fun observeAll(): Flow<List<Pet>> = state.asStateFlow().map { it.values.sortedBy(Pet::name) }

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
        state.asStateFlow().map { snap ->
            snap.values.filter { it.petId == petId }.sortedBy(Medication::name)
        }

    override fun observeAll(): Flow<List<Medication>> = state.asStateFlow().map { it.values.sortedBy(Medication::name) }

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
        schedules.asStateFlow().map { snap ->
            snap.values.filter { it.medicationId == medicationId }
        }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phasesById.asStateFlow().map { snap ->
            (snap[scheduleId] ?: emptyList()).sortedBy(SchedulePhase::phaseOrder)
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.asStateFlow().map { snap ->
            snap.values
                .filter { it.endDate == null || it.endDate!! >= onOrAfter }
                .map { sched -> ScheduleWithPhases(sched, phasesById.value[sched.id] ?: emptyList()) }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.asStateFlow().map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = phasesById.asStateFlow().map { it.values.flatten() }

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

    fun put(event: DoseEvent) {
        state.update { it + (event.id to event) }
    }

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> =
        // Test stub: BackupAggregatorTest exercises observeAll, not observeForPet. Filtering
        // by pet would require a schedule join; this returns the time-filtered subset only
        // so the interface is satisfied without pulling in the full fake-state plumbing.
        state.asStateFlow().map { snap -> snap.values.filter { it.scheduledAt >= sinceInclusive } }

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
