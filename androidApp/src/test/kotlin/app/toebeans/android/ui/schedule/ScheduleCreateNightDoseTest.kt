package app.toebeans.android.ui.schedule

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.scheduler.ScheduleCalculator
import app.toebeans.core.scheduler.ScheduledDose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier B9 — sleep-disruption ("night dose") warning during phase creation.
 *
 * Per ADR-0004 decision D2 and v0.1-followups #1, a phase whose `doseTimesLocal`
 * contains any time in `[00:00, 06:00)` surfaces a non-blocking UX warning prompting
 * the user to confirm they intend to be woken up. The user can affirm via an explicit
 * "Yes, that's intentional" action. Saving is NOT gated on affirmation — this is a
 * notification-only nudge, not a hard validation.
 *
 * ## Boundaries
 *
 *  - `[00:00, 06:00)` is inclusive at the lower bound and exclusive at the upper.
 *    A dose at exactly 06:00 does NOT trigger the warning (people wake up at 6am
 *    on purpose; nothing to nudge about). A dose at exactly 00:00 DOES trigger
 *    (matches D2's worked example of "first dose at startDate 0000 local").
 *
 * ## Persistence of affirmation
 *
 *  - The spec (ADR-0004 D2 + v0.1-followups #1) does NOT specify affirmation
 *    persistence semantics. Defaulting to the safer policy: any subsequent edit
 *    to a phase's dose times after affirmation clears the affirmation flag. The
 *    user must re-affirm if the new dose times still fall in the night window.
 *    Rationale: a user who adds a SECOND night dose by accident should re-see
 *    the warning rather than have it pre-dismissed by the earlier affirmation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleCreateNightDoseTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dose time at 03_00 triggers night-dose warning`() =
        runTest {
            val vm = newVm()
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(3, 0))) }
            val phase = vm.state.value.phases[0]
            assertTrue(
                "phase with 03:00 dose should trigger nightDoseWarning",
                phase.nightDoseWarning,
            )
            assertFalse(
                "freshly-triggered warning should not be pre-affirmed",
                phase.nightDoseAffirmed,
            )
        }

    @Test
    fun `dose time at 06_00 exactly does NOT trigger warning (upper bound is exclusive)`() =
        runTest {
            val vm = newVm()
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(6, 0))) }
            assertFalse(
                "06:00 is outside [00:00, 06:00); no warning expected",
                vm.state.value.phases[0]
                    .nightDoseWarning,
            )
        }

    @Test
    fun `dose time at 00_00 exactly DOES trigger warning (lower bound is inclusive)`() =
        runTest {
            val vm = newVm()
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(0, 0))) }
            assertTrue(
                "00:00 is inside [00:00, 06:00); warning expected per D2's worked example",
                vm.state.value.phases[0]
                    .nightDoseWarning,
            )
        }

    @Test
    fun `affirmNightDose clears the warning and sets the affirmation flag`() =
        runTest {
            val vm = newVm()
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(3, 0))) }
            assertTrue(
                vm.state.value.phases[0]
                    .nightDoseWarning,
            )

            vm.affirmNightDose(0)

            val phase = vm.state.value.phases[0]
            assertFalse("warning should clear after affirmation", phase.nightDoseWarning)
            assertTrue("affirmation flag should be set", phase.nightDoseAffirmed)
        }

    @Test
    fun `editing dose times after affirmation resets the affirmation (re-prompts user)`() =
        runTest {
            val vm = newVm()
            // Step 1: set a night dose, user affirms.
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(3, 0))) }
            vm.affirmNightDose(0)
            assertTrue(
                "precondition: affirmation set",
                vm.state.value.phases[0]
                    .nightDoseAffirmed,
            )

            // Step 2: user edits dose times — adds a SECOND night dose (e.g. by mistake,
            // or intentionally). Per "Reset on edit" policy, the affirmation must be
            // cleared so the user re-sees the warning and re-confirms.
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(3, 0), LocalTime(4, 0))) }

            val phase = vm.state.value.phases[0]
            assertFalse(
                "affirmation should reset after any edit to dose times",
                phase.nightDoseAffirmed,
            )
            assertTrue(
                "warning should re-trigger since night doses still present",
                phase.nightDoseWarning,
            )
        }

    // --- Test rig ----------------------------------------------------------

    private fun newVm(): ScheduleCreateViewModel =
        ScheduleCreateViewModel(
            medicationRepository = InMemMedRepoB9(MED),
            scheduleRepository = InMemSchedRepoB9(),
            scheduleCalculator = NoopCalculator,
            timeZone = TimeZone.UTC,
        )

    companion object {
        private const val MED_ID = "med-amox"
        private const val PET_ID = "pet-luna"

        private val MED =
            Medication(
                id = MED_ID,
                petId = PET_ID,
                name = "Amoxicillin",
                doseAmount = "50mg",
                notes = null,
                createdAt = Instant.fromEpochMilliseconds(0),
                discontinuedAt = null,
            )
    }
}

/** No-op calculator: B9 doesn't exercise the calculator path, only the field-level warning. */
private object NoopCalculator : ScheduleCalculator {
    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> = emptyList()
}

private class InMemMedRepoB9(
    initial: Medication,
) : MedicationRepository {
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
}

private class InMemSchedRepoB9 : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())
    private val phases = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phases.asStateFlow().map {
            it[scheduleId]
                ?: emptyList()
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.asStateFlow().map { snap ->
            snap.values
                .filter { it.endDate == null || it.endDate!! >= onOrAfter }
                .map { ScheduleWithPhases(it, phases.value[it.id] ?: emptyList()) }
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
