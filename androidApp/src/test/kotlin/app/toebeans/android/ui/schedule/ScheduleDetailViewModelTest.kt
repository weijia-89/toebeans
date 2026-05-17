package app.toebeans.android.ui.schedule

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavior tests for [ScheduleDetailViewModel].
 *
 * Pattern mirrors [app.toebeans.android.ui.pets.PetEditViewModelDeleteTest]:
 * Dispatchers.Main replaced with [UnconfinedTestDispatcher] so the viewModelScope
 * collect job runs synchronously inside `runTest`. Repositories are in-memory fakes
 * (local to this file) so this test doesn't depend on the production FakeRepositories
 * module-level state (which is shared across tests and would create cross-test
 * pollution).
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load resolves pet name medication name phases and schedule from id`() =
        runTest {
            val rig = rig()
            val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
            vm.load(SCHED_ID)
            val state = vm.state.value
            assertEquals(SCHED_ID, state.scheduleId)
            assertNotNull("schedule should be loaded", state.schedule)
            assertEquals("Luna", state.petName)
            assertEquals("Amoxicillin", state.medicationName)
            assertEquals(2, state.phases.size)
            assertEquals(0, state.phases[0].phaseOrder)
            assertEquals(1, state.phases[1].phaseOrder)
            assertFalse("loading should be cleared after first emission", state.loading)
        }

    @Test
    fun `delete removes the schedule from the repository and returns true`() =
        runTest {
            val rig = rig()
            val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
            vm.load(SCHED_ID)
            assertTrue(vm.delete())
            assertNull("schedule should be gone after delete", rig.sched.snapshot()[SCHED_ID])
            assertTrue("phases should be gone after delete", rig.sched.phaseSnapshot()[SCHED_ID].isNullOrEmpty())
        }

    @Test
    fun `delete returns false when load has not been called`() =
        runTest {
            val rig = rig()
            val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
            assertFalse(vm.delete())
        }

    @Test
    fun `state transitions to schedule null when the underlying row is removed`() =
        runTest {
            val rig = rig()
            val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
            vm.load(SCHED_ID)
            assertNotNull(vm.state.value.schedule)
            // Simulate a concurrent delete from another surface. The VM should propagate
            // schedule == null without throwing — terminal-deletion state is expected.
            rig.sched.removeForTest(SCHED_ID)
            val after = vm.state.value
            assertNull(after.schedule)
            assertFalse(after.loading)
        }

    @Test
    fun `unknown pet or medication renders placeholder labels without throwing`() =
        runTest {
            val rig = rig()
            // Drop the medication: the schedule still exists, but the join can't resolve.
            // The VM contract here is "render placeholder label" — the screen is reached
            // from the Reminder List which already filters out unjoinable rows. If a race
            // delivers an unjoinable row anyway, we don't want to crash the detail screen.
            rig.med.removeForTest(MED_ID)
            val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
            vm.load(SCHED_ID)
            val state = vm.state.value
            assertNotNull(state.schedule)
            assertEquals("(unknown medication)", state.medicationName)
            assertEquals("(unknown pet)", state.petName)
        }
}

private const val PET_ID = "pet-luna"
private const val MED_ID = "med-amox"
private const val SCHED_ID = "sched-amox-1"

private fun rig(): Rig {
    val now = Clock.System.now()
    val pet =
        Pet(
            id = PET_ID,
            name = "Luna",
            species = Species.CAT,
            birthdate = null,
            weightKg = null,
            notes = null,
            createdAt = now,
            archivedAt = null,
        )
    val med =
        Medication(
            id = MED_ID,
            petId = PET_ID,
            name = "Amoxicillin",
            doseAmount = "50mg",
            notes = null,
            createdAt = now,
            discontinuedAt = null,
        )
    val schedule =
        Schedule(
            id = SCHED_ID,
            medicationId = MED_ID,
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2026, 5, 14),
            createdAt = now,
        )
    val phases =
        listOf(
            SchedulePhase(
                id = "p-1",
                scheduleId = SCHED_ID,
                phaseOrder = 0,
                durationDays = 7,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
                dayInterval = 1,
            ),
            SchedulePhase(
                id = "p-2",
                scheduleId = SCHED_ID,
                phaseOrder = 1,
                durationDays = 7,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = "25mg",
                dayInterval = 1,
            ),
        )
    return Rig(
        pet = InMemoryPetRepo(pet),
        med = InMemoryMedRepo(med),
        sched = InMemoryScheduleRepo(schedule, phases),
    )
}

private data class Rig(
    val pet: InMemoryPetRepo,
    val med: InMemoryMedRepo,
    val sched: InMemoryScheduleRepo,
)

private class InMemoryPetRepo(initial: Pet) : PetRepository {
    private val store = MutableStateFlow(mapOf(initial.id to initial))

    override fun observeAll(): Flow<List<Pet>> = store.asStateFlow().map { it.values.toList() }

    override fun observeById(id: String): Flow<Pet?> = store.asStateFlow().map { it[id] }

    override suspend fun getById(id: String): Pet? = store.value[id]

    override suspend fun upsert(pet: Pet) {
        store.update { it + (pet.id to pet) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }
}

private class InMemoryMedRepo(initial: Medication) : MedicationRepository {
    private val store = MutableStateFlow(mapOf(initial.id to initial))

    override fun observeAll(): Flow<List<Medication>> = store.asStateFlow().map { it.values.toList() }

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override suspend fun getById(id: String): Medication? = store.value[id]

    override suspend fun upsert(medication: Medication) {
        store.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }

    fun removeForTest(id: String) {
        store.update { it - id }
    }
}

private class InMemoryScheduleRepo(
    initialSchedule: Schedule,
    initialPhases: List<SchedulePhase>,
) : ScheduleRepository {
    private val schedules = MutableStateFlow(mapOf(initialSchedule.id to initialSchedule))
    private val phases = MutableStateFlow(mapOf(initialSchedule.id to initialPhases))

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phases.asStateFlow().map { it[scheduleId] ?: emptyList() }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        combine(schedules.asStateFlow(), phases.asStateFlow()) { scheds, phaseMap ->
            scheds.values
                .filter { (it.endDate == null || it.endDate!! >= onOrAfter) }
                .map { ScheduleWithPhases(it, phaseMap[it.id] ?: emptyList()) }
        }

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

    fun snapshot(): Map<String, Schedule> = schedules.value

    fun phaseSnapshot(): Map<String, List<SchedulePhase>> = phases.value

    fun removeForTest(id: String) {
        schedules.update { it - id }
        phases.update { it - id }
    }
}
