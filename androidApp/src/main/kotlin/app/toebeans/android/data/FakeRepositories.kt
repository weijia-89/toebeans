package app.toebeans.android.data

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
import kotlinx.coroutines.flow.combine
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

    override fun observeAll(): Flow<List<Medication>> =
        medications.asStateFlow().map { snap ->
            snap.values.sortedBy(Medication::name)
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

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phasesByScheduleId.asStateFlow().map { snap ->
            (snap[scheduleId] ?: emptyList()).sortedBy(SchedulePhase::phaseOrder)
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        // Two-source combine: re-emit when either schedules OR phases change. Filtering
        // and joining are pure, so this is just a fanout. SQLDelight will replace with
        // a single LEFT JOIN query.
        combine(schedules.asStateFlow(), phasesByScheduleId.asStateFlow()) { scheds, phaseMap ->
            scheds.values
                .asSequence()
                .filter { sched ->
                    // Cross-module smart-cast won't propagate, so bind locally.
                    val end = sched.endDate
                    end == null || end >= onOrAfter
                }.sortedBy { it.createdAt }
                .map { sched ->
                    ScheduleWithPhases(
                        schedule = sched,
                        phases =
                            (phaseMap[sched.id] ?: emptyList())
                                .sortedBy(SchedulePhase::phaseOrder),
                    )
                }.toList()
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
//
// **Seed posture as of M1.2:** the stores start EMPTY. The Luna + Rufus demo data is
// loaded on demand by [loadDemoData] from the first-launch dialog (see
// `ui.firstlaunch.FirstLaunchDialog`). Before this change, the seed was unconditional
// at process start, which was useful for reviewer demos but confusing for real
// first-time testers who saw two pre-existing pets they hadn't created. The first-launch
// prompt keeps the demo on a one-tap opt-in while defaulting real users to a clean slate.
internal val seedCreatedAt: Instant = Instant.parse("2024-01-01T00:00:00Z")

internal val pets = MutableStateFlow<Map<String, Pet>>(emptyMap())

internal val medications = MutableStateFlow<Map<String, Medication>>(emptyMap())

internal val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())

internal val phasesByScheduleId = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

// DoseEvent store — used by [FakeDoseEventRepository] in the same package.
internal val doseEvents = MutableStateFlow<Map<String, app.toebeans.core.model.DoseEvent>>(emptyMap())

/**
 * Idempotently populate the in-memory fakes with the Luna + Rufus demo data. Called from
 * the first-launch dialog when the user taps "Load demo data". Safe to call repeatedly —
 * each call replaces the existing entries for the same demo IDs and leaves any
 * user-created entries alone.
 *
 * Luna has hyperthyroidism in the fictional canon, hence the Methimazole BID schedule.
 * The schedule is anchored at 2024-01-01 with no end so the Today screen renders future
 * doses regardless of when the app is first launched.
 */
public fun loadDemoData() {
    val rufus =
        Pet(
            id = "pet-rufus",
            name = "Rufus",
            species = Species.DOG,
            birthdate = LocalDate(2019, 3, 14),
            weightKg = 12.4,
            notes = null,
            createdAt = seedCreatedAt,
            archivedAt = null,
        )
    val luna =
        Pet(
            id = "pet-luna",
            name = "Luna",
            species = Species.CAT,
            birthdate = LocalDate(2022, 7, 1),
            weightKg = 4.1,
            notes = null,
            createdAt = seedCreatedAt,
            archivedAt = null,
        )
    pets.update { it + (rufus.id to rufus) + (luna.id to luna) }

    val methimazole =
        Medication(
            id = "med-luna-methimazole",
            petId = luna.id,
            name = "Methimazole",
            doseAmount = "2.5 mg",
            notes = "Crush and hide in churu — Luna spits out whole pills.",
            createdAt = seedCreatedAt,
            discontinuedAt = null,
        )
    medications.update { it + (methimazole.id to methimazole) }

    val methSchedule =
        Schedule(
            id = "sched-luna-methimazole",
            medicationId = methimazole.id,
            startDate = LocalDate(2024, 1, 1),
            endDate = null,
            createdAt = seedCreatedAt,
        )
    schedules.update { it + (methSchedule.id to methSchedule) }

    val methPhase =
        SchedulePhase(
            id = "phase-luna-methimazole-0",
            scheduleId = methSchedule.id,
            phaseOrder = 0,
            durationDays = SchedulePhase.MAX_DURATION_DAYS,
            dosesPerDay = 2,
            doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
            doseAmount = null,
            dayInterval = 1,
        )
    phasesByScheduleId.update { it + (methSchedule.id to listOf(methPhase)) }
}

/** Utility for forms that need to display "now" in the device's local zone. */
public fun nowLocalTime(zone: TimeZone = TimeZone.currentSystemDefault()): LocalTime =
    Clock.System
        .now()
        .toLocalDateTime(zone)
        .time
