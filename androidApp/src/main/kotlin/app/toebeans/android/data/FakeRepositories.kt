package app.toebeans.android.data

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * In-memory repository implementations for the UI scaffold milestone. Pre-seeded with two
 * example pets so the empty-state and populated-state UIs can both be reviewed without
 * persistence wired up.
 *
 * **Not production-grade.** The SQLDelight-backed implementations will land in milestone 1
 * (under .../core/data/ with backing DAOs from the generated Database type). This file
 * will be deleted at that point.
 *
 * All three repos share a single companion-object seeded store so cross-screen edits are
 * visible. State survives within the process but is lost on process death — that is fine
 * for scaffold review.
 */
public class FakePetRepository : PetRepository {
    override fun observeAll(): Flow<List<Pet>> = pets.asStateFlow().map { it.values.sortedBy(Pet::name) }

    override suspend fun getById(id: String): Pet? = pets.value[id]

    override fun observeById(id: String): Flow<Pet?> = pets.asStateFlow().map { it[id] }

    override suspend fun upsert(pet: Pet) {
        pets.update { it + (pet.id to pet) }
    }

    override suspend fun delete(id: String) {
        pets.update { it - id }
    }
}

public class FakeMedicationRepository : MedicationRepository {
    override fun observeForPet(petId: String): Flow<List<Medication>> =
        medications.asStateFlow().map { snap ->
            snap.values.filter { it.petId == petId }.sortedBy(Medication::name)
        }

    override suspend fun getById(id: String): Medication? = medications.value[id]

    override suspend fun upsert(medication: Medication) {
        medications.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        medications.update { it - id }
    }
}

public class FakeScheduleRepository : ScheduleRepository {
    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap ->
            snap.values.filter { it.medicationId == medicationId }.sortedByDescending(Schedule::createdAt)
        }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phasesByScheduleId.asStateFlow().map { snap ->
            (snap[scheduleId] ?: emptyList()).sortedBy(SchedulePhase::phaseOrder)
        }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        // Validate phaseOrder is a dense 0..N-1 sequence to match the real DB constraint.
        val orders = phases.map { it.phaseOrder }.sorted()
        require(orders == orders.indices.toList()) {
            "phases must have dense phaseOrder 0..N-1; got $orders"
        }
        require(phases.all { it.scheduleId == schedule.id }) {
            "every phase.scheduleId must match schedule.id"
        }
        schedules.update { it + (schedule.id to schedule) }
        phasesByScheduleId.update { it + (schedule.id to phases) }
    }

    override suspend fun delete(id: String) {
        schedules.update { it - id }
        phasesByScheduleId.update { it - id }
    }
}

// Module-internal seeded store. All four fakes (Pet, Medication, Schedule, DoseEvent)
// read from / write to this. `internal` (not `private`) so the sibling
// FakeDoseEventRepository in the same package can join across the maps without
// duplicating the storage. Timestamps are arbitrary 2024 values; they only matter
// once a real history is recorded.
internal val seedCreatedAt: Instant = Instant.parse("2024-01-01T00:00:00Z")

internal val pets =
    MutableStateFlow(
        mapOf(
            "pet-rufus" to
                Pet(
                    id = "pet-rufus",
                    name = "Rufus",
                    species = Species.DOG,
                    birthdate = LocalDate(2019, 3, 14),
                    weightKg = 12.4,
                    notes = null,
                    createdAt = seedCreatedAt,
                    archivedAt = null,
                ),
            "pet-luna" to
                Pet(
                    id = "pet-luna",
                    name = "Luna",
                    species = Species.CAT,
                    birthdate = LocalDate(2022, 7, 1),
                    weightKg = 4.1,
                    notes = null,
                    createdAt = seedCreatedAt,
                    archivedAt = null,
                ),
        ),
    )

// Seed one medication for Luna so the dose-log surface (Last dose / Log dose now) has
// something to demo against on first launch. Real users will get an empty state by
// default; the seed only exists because the scaffold has no persistence yet, and a
// stressed pet owner reviewing the app needs to see what the populated state looks like.
// Luna actually has hyperthyroidism in the fictional canon, hence Methimazole.
internal val medications =
    MutableStateFlow<Map<String, Medication>>(
        mapOf(
            "med-luna-methimazole" to
                Medication(
                    id = "med-luna-methimazole",
                    petId = "pet-luna",
                    name = "Methimazole",
                    doseAmount = "2.5 mg",
                    notes = "Crush and hide in churu — Luna spits out whole pills.",
                    createdAt = seedCreatedAt,
                    discontinuedAt = null,
                ),
        ),
    )

// Twice-daily schedule for Methimazole, starting on the seed date with no end. This
// gives the Log Dose button something to record against — without an active schedule,
// the button is gated to "Add a schedule first."
internal val schedules =
    MutableStateFlow<Map<String, Schedule>>(
        mapOf(
            "sched-luna-methimazole" to
                Schedule(
                    id = "sched-luna-methimazole",
                    medicationId = "med-luna-methimazole",
                    startDate = LocalDate(2024, 1, 1),
                    endDate = null,
                    createdAt = seedCreatedAt,
                ),
        ),
    )

internal val phasesByScheduleId =
    MutableStateFlow<Map<String, List<SchedulePhase>>>(
        mapOf(
            "sched-luna-methimazole" to
                listOf(
                    SchedulePhase(
                        id = "phase-luna-methimazole-0",
                        scheduleId = "sched-luna-methimazole",
                        phaseOrder = 0,
                        durationDays = SchedulePhase.MAX_DURATION_DAYS,
                        dosesPerDay = 2,
                        doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                        doseAmount = null,
                        dayInterval = 1,
                    ),
                ),
        ),
    )

// DoseEvent store — used by [FakeDoseEventRepository] in the same package.
internal val doseEvents = MutableStateFlow<Map<String, app.toebeans.core.model.DoseEvent>>(emptyMap())

/** Utility for forms that need to display "now" in the device's local zone. */
public fun nowLocalTime(zone: TimeZone = TimeZone.currentSystemDefault()): LocalTime =
    Clock.System
        .now()
        .toLocalDateTime(zone)
        .time
