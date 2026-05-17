package app.toebeans.android.ui.schedule

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.MalformedScheduleException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pre-flight calculator-error tests for [ScheduleCreateViewModel] (B8).
 *
 * The pre-flight runs the calculator against a 30-day window before persisting. If the
 * calculator throws a [MalformedScheduleException], the VM surfaces a user-readable
 * message via [ScheduleCreateUiState.formError] and the upsert is skipped. Field-level
 * validation errors (per-phase) still come from the existing `validatePhase` path;
 * these tests focus on the new calculator-pre-flight branch and its UX contract:
 *
 *   1. EventCountExceeded → friendly message naming the attempted vs max count.
 *   2. Schedule is NOT persisted when the pre-flight rejects it.
 *   3. Any subsequent field mutation clears the banner.
 *   4. The happy path (calculator returns a result) still persists and returns the id.
 *
 * A test-local `FakeCalculator` lets us inject deterministic throws without exercising
 * the real calculator's full event-counting path (which is locked down by its own
 * 15-case test-as-spec in :shared:jvmTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleCreatePreflightTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pre-flight EventCountExceeded surfaces a friendly message and skips upsert`() =
        runTest {
            val medRepo = InMemMedRepo(MED)
            val schedRepo = InMemSchedRepo()
            val calc =
                FakeCalculator(
                    throws =
                        MalformedScheduleException.EventCountExceeded(
                            attemptedCount = 250_000L,
                            maxCount = DefaultScheduleCalculator.MAX_EVENT_COUNT,
                        ),
                )
            val vm =
                ScheduleCreateViewModel(
                    medicationRepository = medRepo,
                    scheduleRepository = schedRepo,
                    scheduleCalculator = calc,
                    timeZone = TimeZone.UTC,
                )
            vm.setMedicationId(MED_ID)
            vm.onStartDateChange(LocalDate(2026, 5, 1))

            val result = vm.save()
            assertNull("save should return null when pre-flight rejects", result)
            assertEquals("calculator should have been invoked exactly once", 1, calc.invocations)
            val msg = vm.state.value.formError
            assertNotNull("formError should be populated", msg)
            assertTrue("message should mention the dose count", msg!!.contains("250000") || msg.contains("250,000"))
            assertTrue("message should mention the safe limit", msg.contains("100000") || msg.contains("100,000"))
            assertTrue("schedule store should still be empty", schedRepo.snapshot().isEmpty())
        }

    @Test
    fun `field mutation after a pre-flight error clears the banner`() =
        runTest {
            val medRepo = InMemMedRepo(MED)
            val schedRepo = InMemSchedRepo()
            val calc =
                FakeCalculator(
                    throws =
                        MalformedScheduleException.EventCountExceeded(
                            attemptedCount = 250_000L,
                            maxCount = DefaultScheduleCalculator.MAX_EVENT_COUNT,
                        ),
                )
            val vm =
                ScheduleCreateViewModel(
                    medicationRepository = medRepo,
                    scheduleRepository = schedRepo,
                    scheduleCalculator = calc,
                    timeZone = TimeZone.UTC,
                )
            vm.setMedicationId(MED_ID)
            vm.onStartDateChange(LocalDate(2026, 5, 1))
            vm.save()
            assertNotNull(vm.state.value.formError)

            // A field mutation should clear the banner — the user is now addressing it.
            vm.onEndDateChange(LocalDate(2026, 5, 10))
            assertNull("formError should clear on field change", vm.state.value.formError)

            // Touching a phase field should also clear it (independent path).
            vm.save() // re-populate
            assertNotNull(vm.state.value.formError)
            vm.updatePhase(0) { it.copy(doseAmount = "5mg") }
            assertNull("formError should clear on phase change", vm.state.value.formError)
        }

    @Test
    fun `happy path calculator success persists the schedule and returns its id`() =
        runTest {
            val medRepo = InMemMedRepo(MED)
            val schedRepo = InMemSchedRepo()
            val calc = FakeCalculator(throws = null)
            val vm =
                ScheduleCreateViewModel(
                    medicationRepository = medRepo,
                    scheduleRepository = schedRepo,
                    scheduleCalculator = calc,
                    timeZone = TimeZone.UTC,
                )
            vm.setMedicationId(MED_ID)
            vm.onStartDateChange(LocalDate(2026, 5, 1))

            val id = vm.save()
            assertNotNull("save should return a schedule id", id)
            assertNull("formError should be null on success", vm.state.value.formError)
            assertEquals(1, schedRepo.snapshot().size)
            assertEquals(id, schedRepo.snapshot().keys.single())
        }

    @Test
    fun `integration with real DefaultScheduleCalculator - realistic schedule passes pre-flight`() =
        runTest {
            // Integration test, not unit. Per AGENTS.md the calculator is vibe-dangerous, so the
            // pre-flight wiring (UI -> real calculator) gets its own end-to-end check. The unit
            // tests above use a FakeCalculator to exercise our error mapping deterministically;
            // this test exercises the REAL DefaultScheduleCalculator with a representative
            // schedule (2 phases, 7 days each, 2 doses per day, 1-day interval) and asserts the
            // pre-flight returns null and the upsert lands.
            val medRepo = InMemMedRepo(MED)
            val schedRepo = InMemSchedRepo()
            val realCalc = DefaultScheduleCalculator()
            val vm =
                ScheduleCreateViewModel(
                    medicationRepository = medRepo,
                    scheduleRepository = schedRepo,
                    scheduleCalculator = realCalc,
                    timeZone = TimeZone.UTC,
                )
            vm.setMedicationId(MED_ID)
            vm.onStartDateChange(LocalDate(2026, 5, 1))
            vm.onEndDateChange(LocalDate(2026, 5, 14))
            // Replace the default single 7-day phase with a 2-phase taper to push the calculator
            // through actual phase concatenation (the most complex code path in the algorithm).
            vm.updatePhase(0) {
                it.copy(durationDaysText = "7", doseTimes = listOf(LocalTime(8, 0), LocalTime(20, 0)))
            }
            vm.addPhase()
            vm.updatePhase(1) {
                it.copy(durationDaysText = "7", doseTimes = listOf(LocalTime(8, 0)))
            }

            val id = vm.save()
            assertNotNull("realistic 2-phase taper should pre-flight green", id)
            assertNull("formError should be null on integration success", vm.state.value.formError)
            assertEquals(1, schedRepo.snapshot().size)
        }

    @Test
    fun `integration with real DefaultScheduleCalculator - pre-flight catches unreachable malformed input`() {
        // Direct invocation of runPreflight with the real calculator and deliberately malformed
        // input (duplicate phaseOrder). The save() loop cannot produce this state because it
        // assigns phaseOrder = idx, but a future refactor that exposes user-supplied phase
        // orders could. Locking down the integration here proves the catch-list maps the real
        // exception types, not just the FakeCalculator's throws.
        val realCalc = DefaultScheduleCalculator()
        val vm =
            ScheduleCreateViewModel(
                medicationRepository = InMemMedRepo(MED),
                scheduleRepository = InMemSchedRepo(),
                scheduleCalculator = realCalc,
                timeZone = TimeZone.UTC,
            )
        val schedule = sampleSchedule()
        val duplicate =
            listOf(
                samplePhase().copy(id = "phase-a", phaseOrder = 0),
                samplePhase().copy(id = "phase-b", phaseOrder = 0), // duplicate!
            )
        val msg = vm.runPreflight(schedule, duplicate)
        assertNotNull("real calculator should throw on duplicate phaseOrder", msg)
        assertTrue("message should name the duplicate position", msg!!.contains("0"))
    }

    @Test
    fun `runPreflight returns null when calculator succeeds and a string otherwise`() {
        // Direct unit on the preflight method — bypasses the full save() flow so we can
        // independently lock down the per-exception message mapping. Useful because the
        // save()-path test only covers EventCountExceeded; this covers the other branches
        // without having to drive the entire form to a state that would synthesize each.
        val calc =
            FakeCalculator(
                throws =
                    MalformedScheduleException.DuplicatePhaseOrder(
                        phaseOrder = 1,
                        phaseIds = listOf("a", "b"),
                    ),
            )
        val vm =
            ScheduleCreateViewModel(
                medicationRepository = InMemMedRepo(MED),
                scheduleRepository = InMemSchedRepo(),
                scheduleCalculator = calc,
                timeZone = TimeZone.UTC,
            )
        val schedule = sampleSchedule()
        val phases = listOf(samplePhase())
        val msg = vm.runPreflight(schedule, phases)
        assertNotNull(msg)
        assertTrue("DuplicatePhaseOrder message should name the position", msg!!.contains("1"))
    }
}

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

private fun sampleSchedule(): Schedule =
    Schedule(
        id = "sched-1",
        medicationId = MED_ID,
        startDate = LocalDate(2026, 5, 1),
        endDate = null,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

private fun samplePhase(): SchedulePhase =
    SchedulePhase(
        id = "phase-1",
        scheduleId = "sched-1",
        phaseOrder = 0,
        durationDays = 7,
        dosesPerDay = 1,
        doseTimesLocal = listOf(LocalTime(8, 0)),
        doseAmount = null,
        dayInterval = 1,
    )

/**
 * Configurable test calculator. Records invocations and either throws the configured
 * exception or returns an empty list (which is a valid calculator output — the form's
 * persist path doesn't depend on the dose list itself).
 */
private class FakeCalculator(
    private val throws: MalformedScheduleException?,
) : ScheduleCalculator {
    var invocations: Int = 0
        private set

    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> {
        invocations += 1
        throws?.let { throw it }
        return emptyList()
    }
}

private class InMemMedRepo(initial: Medication) : MedicationRepository {
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

private class InMemSchedRepo : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())
    private val phases = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phases.asStateFlow().map { it[scheduleId] ?: emptyList() }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.asStateFlow().map { snap ->
            snap.values
                .filter { it.endDate == null || it.endDate!! >= onOrAfter }
                .map { ScheduleWithPhases(it, phases.value[it.id] ?: emptyList()) }
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
}
