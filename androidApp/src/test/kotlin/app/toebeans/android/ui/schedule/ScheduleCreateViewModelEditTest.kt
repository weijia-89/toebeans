package app.toebeans.android.ui.schedule

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.MalformedScheduleException
import app.toebeans.core.scheduler.ScheduleCalculator
import app.toebeans.core.scheduler.ScheduledDose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/**
 * Edit-mode contract for [ScheduleCreateViewModel] (S2 schedule edit).
 *
 * The create form is reused in edit mode: `loadForEdit` prefills the drafts from the
 * stored schedule + phases, and `save` must (a) upsert under the SAME schedule id with
 * the SAME `createdAt` (the repository update path writes `created_at` verbatim), and
 * (b) keep resolved dose history (GIVEN/SKIPPED/MISSED rows) while recycling only
 * PENDING rows for the edited schedule: each pending row is cancelled with the
 * notification actuator, deleted, and the 72-hour horizon is rematerialized for the
 * new dose times. The calculator preflight (formError mapping) is the same code path
 * as create mode, asserted here by string equality between the two modes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleCreateViewModelEditTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadForEdit prefills the form and save keeps the same schedule id and createdAt`() =
        runTest {
            val schedRepo = FakeScheduleRepoEdit().apply { seed(seededSchedule(), seededPhases()) }
            val vm = buildVm(schedRepo = schedRepo)

            vm.loadForEdit(SCHED_ID)

            assertEquals(SCHED_ID, vm.state.value.editingScheduleId)
            assertEquals(MED_ID, vm.state.value.medicationId)
            assertEquals(todayUtc(), vm.state.value.startDate)
            assertEquals(todayUtc().plus(35, DateTimeUnit.DAY), vm.state.value.endDate)
            assertEquals(
                listOf(LocalTime(8, 0), LocalTime(20, 0)),
                vm.state.value.phases[0]
                    .doseTimes,
            )

            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(9, 15))) }
            val id = vm.save()

            assertEquals("edit must upsert under the SAME schedule id", SCHED_ID, id)
            val saved = schedRepo.snapshot()[SCHED_ID]
            assertNotNull(saved)
            assertEquals("edit must keep the loaded createdAt verbatim", ORIGINAL_CREATED_AT, saved!!.createdAt)
            assertEquals(listOf(LocalTime(9, 15)), schedRepo.phaseSnapshot(SCHED_ID)[0].doseTimesLocal)
        }

    @Test
    fun `resolved dose events survive the edit round-trip`() =
        runTest {
            val schedRepo = FakeScheduleRepoEdit().apply { seed(seededSchedule(), seededPhases()) }
            val doseRepo = FakeDoseRepoEdit()
            doseRepo.seed(givenEvent())
            doseRepo.seed(missedEvent())
            val vm = buildVm(schedRepo = schedRepo, doseRepo = doseRepo)

            vm.loadForEdit(SCHED_ID)
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(9, 15))) }
            vm.save()

            val survivors = doseRepo.snapshot()
            assertEquals(
                "GIVEN row must survive the edit",
                "dose-given-1",
                survivors
                    .first {
                        it.status ==
                            DoseStatus.GIVEN
                    }.id,
            )
            assertEquals(
                "MISSED row must survive the edit",
                "dose-missed-1",
                survivors
                    .first {
                        it.status ==
                            DoseStatus.MISSED
                    }.id,
            )
            assertEquals(
                "resolved rows must still point at the same schedule",
                listOf(SCHED_ID, SCHED_ID),
                listOf(
                    survivors.first { it.id == "dose-given-1" }.scheduleId,
                    survivors.first { it.id == "dose-missed-1" }.scheduleId,
                ),
            )
        }

    @Test
    fun `pending dose event for the old slot is cancelled and deleted, then a new horizon row exists`() =
        runTest {
            val schedRepo = FakeScheduleRepoEdit().apply { seed(seededSchedule(), seededPhases()) }
            val doseRepo = FakeDoseRepoEdit()
            val oldInstant = todayUtc().atStartOfDayIn(TimeZone.UTC) + 20.hours
            val oldEventId = "dose-${SCHED_ID}-${oldInstant.toEpochMilliseconds()}"
            doseRepo.seed(
                DoseEvent(
                    id = oldEventId,
                    scheduleId = SCHED_ID,
                    medicationId = MED_ID,
                    scheduledAt = oldInstant,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )
            val actuator = RecordingActuatorEdit()
            val vm = buildVm(schedRepo = schedRepo, doseRepo = doseRepo, actuator = actuator)

            vm.loadForEdit(SCHED_ID)
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(9, 15))) }
            vm.save()

            assertEquals("stale pending row must be cancelled exactly once", listOf(oldEventId), actuator.cancelled)
            assertTrue("stale pending row must be deleted", doseRepo.snapshot().none { it.id == oldEventId })
            val pendingRows =
                doseRepo.snapshot().filter { it.scheduleId == SCHED_ID && it.status == DoseStatus.PENDING }
            assertTrue("a new horizon row must exist for the new time", pendingRows.isNotEmpty())
            assertTrue(
                "new rows must fire at the edited dose time",
                pendingRows.all { it.scheduledAt.toLocalDateTime(TimeZone.UTC).time == LocalTime(9, 15) },
            )
            assertTrue(
                "no row may remain at the old slot instant",
                pendingRows.none { it.scheduledAt == oldInstant },
            )
        }

    @Test
    fun `edited phase that exceeds the calculator window surfaces the same formError as create mode`() =
        runTest {
            val createVm =
                buildVm(
                    schedRepo = FakeScheduleRepoEdit(),
                    calc = throwingCalculator(),
                )
            createVm.setMedicationId(MED_ID)
            createVm.onStartDateChange(todayUtc())
            createVm.save()
            assertNotNull(createVm.state.value.formError)

            val schedRepo = FakeScheduleRepoEdit().apply { seed(seededSchedule(), seededPhases()) }
            val editVm = buildVm(schedRepo = schedRepo, calc = throwingCalculator())
            editVm.loadForEdit(SCHED_ID)
            editVm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(9, 15))) }
            val result = editVm.save()

            assertNull("save must be rejected when the preflight fails", result)
            assertEquals(
                "edit mode must reuse the create-mode preflight message verbatim",
                createVm.state.value.formError,
                editVm.state.value.formError,
            )
            assertEquals(
                "the stored schedule row must be untouched when the preflight rejects",
                seededSchedule(),
                schedRepo.snapshot()[SCHED_ID],
            )
        }

    private fun throwingCalculator(): ScheduleCalculator =
        ThrowingCalculatorEdit(
            throws =
                MalformedScheduleException.EventCountExceeded(
                    attemptedCount = 250_000L,
                    maxCount = DefaultScheduleCalculator.MAX_EVENT_COUNT,
                ),
        )

    private fun buildVm(
        schedRepo: ScheduleRepository,
        doseRepo: DoseEventRepository = FakeDoseRepoEdit(),
        actuator: NotificationActuator = RecordingActuatorEdit(),
        calc: ScheduleCalculator = DefaultScheduleCalculator(),
    ): ScheduleCreateViewModel =
        ScheduleCreateViewModel(
            medicationRepository = FakeMedRepoEdit(),
            scheduleRepository = schedRepo,
            doseEventRepository = doseRepo,
            scheduleCalculator = calc,
            notificationActuator = actuator,
            timeZone = TimeZone.UTC,
        )

    private fun todayUtc(): LocalDate =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.UTC)
            .date

    private fun seededSchedule(): Schedule =
        Schedule(
            id = SCHED_ID,
            medicationId = MED_ID,
            startDate = todayUtc(),
            endDate = todayUtc().plus(35, DateTimeUnit.DAY),
            createdAt = ORIGINAL_CREATED_AT,
        )

    private fun seededPhases(): List<SchedulePhase> =
        listOf(
            SchedulePhase(
                id = "phase-orig",
                scheduleId = SCHED_ID,
                phaseOrder = 0,
                durationDays = 7,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = null,
                doseUnit = DoseUnit.MG,
                dayInterval = 1,
            ),
        )

    private fun givenEvent(): DoseEvent =
        DoseEvent(
            id = "dose-given-1",
            scheduleId = SCHED_ID,
            medicationId = MED_ID,
            scheduledAt = todayUtc().atStartOfDayIn(TimeZone.UTC) + 8.hours,
            firedAt = todayUtc().atStartOfDayIn(TimeZone.UTC) + 8.hours,
            resolvedAt = todayUtc().atStartOfDayIn(TimeZone.UTC) + 9.hours,
            status = DoseStatus.GIVEN,
            note = null,
        )

    private fun missedEvent(): DoseEvent =
        DoseEvent(
            id = "dose-missed-1",
            scheduleId = SCHED_ID,
            medicationId = MED_ID,
            scheduledAt = todayUtc().atStartOfDayIn(TimeZone.UTC) + 32.hours,
            firedAt = null,
            resolvedAt = todayUtc().atStartOfDayIn(TimeZone.UTC) + 40.hours,
            status = DoseStatus.MISSED,
            note = null,
        )
}

private const val MED_ID = "med-amox"
private const val SCHED_ID = "sched-amox-1"
private val ORIGINAL_CREATED_AT = Instant.fromEpochMilliseconds(1_700_000_000_000)

private fun buildMedEdit(): Medication =
    Medication(
        id = MED_ID,
        petId = "pet-luna",
        name = "Amoxicillin",
        doseAmount = "50",
        doseUnit = DoseUnit.MG,
        notes = null,
        createdAt = ORIGINAL_CREATED_AT,
        discontinuedAt = null,
    )

private class FakeMedRepoEdit : MedicationRepository {
    private val store = MutableStateFlow(mapOf(buildMedEdit().id to buildMedEdit()))

    override fun observeAll(): Flow<List<Medication>> = store.map { it.values.toList() }

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.map { snap -> snap.values.filter { it.petId == petId } }

    override suspend fun getById(id: String): Medication? = store.value[id]

    override suspend fun upsert(medication: Medication) {
        store.value = store.value + (medication.id to medication)
    }

    override suspend fun delete(id: String) {
        store.value = store.value - id
    }
}

private class FakeScheduleRepoEdit : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())
    private val phases = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    fun seed(
        schedule: Schedule,
        phaseRows: List<SchedulePhase>,
    ) {
        schedules.value = schedules.value + (schedule.id to schedule)
        phases.value = phases.value + (schedule.id to phaseRows)
    }

    fun snapshot(): Map<String, Schedule> = schedules.value

    fun phaseSnapshot(scheduleId: String): List<SchedulePhase> = phases.value[scheduleId] ?: emptyList()

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        schedules.map { snap ->
            if (snap.containsKey(scheduleId)) phases.value[scheduleId] ?: emptyList() else emptyList()
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        schedules.map { snap ->
            snap.values
                .filter { it.endDate == null || it.endDate!! >= onOrAfter }
                .map { ScheduleWithPhases(it, phases.value[it.id] ?: emptyList()) }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = phases.map { it.values.flatten() }

    override suspend fun upsert(
        schedule: Schedule,
        phaseRows: List<SchedulePhase>,
    ) {
        schedules.value = schedules.value + (schedule.id to schedule)
        phases.value = phases.value + (schedule.id to phaseRows)
    }

    override suspend fun delete(id: String) {
        schedules.value = schedules.value - id
        phases.value = phases.value - id
    }
}

private class FakeDoseRepoEdit : DoseEventRepository {
    private val store = MutableStateFlow<List<DoseEvent>>(emptyList())

    fun seed(event: DoseEvent) {
        store.value = store.value + event
    }

    fun snapshot(): List<DoseEvent> = store.value

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> = MutableStateFlow(store.value.filter { it.scheduledAt >= sinceInclusive })

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
        MutableStateFlow(
            store.value
                .filter { it.medicationId == medicationId && it.status == DoseStatus.GIVEN }
                .maxByOrNull { it.scheduledAt },
        )

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
        MutableStateFlow(store.value.filter { it.status == DoseStatus.GIVEN && it.scheduledAt >= sinceInclusive })

    override fun observeRecentForHistory(
        sinceInclusive: Instant,
        limit: Int,
    ): Flow<List<DoseEvent>> =
        MutableStateFlow(
            store.value
                .filter { it.scheduledAt >= sinceInclusive }
                .sortedByDescending { it.scheduledAt }
                .take(limit),
        )

    override fun observeAll(): Flow<List<DoseEvent>> = store

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent = error("unused in schedule edit tests")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent = error("unused in schedule edit tests")

    override suspend fun delete(doseEventId: String) {
        store.value = store.value.filterNot { it.id == doseEventId }
    }

    override suspend fun upsert(event: DoseEvent) {
        store.value = store.value.filterNot { it.id == event.id } + event
    }

    override suspend fun markStalePendingAsMissed(cutoff: Instant): Int = 0
}

private class RecordingActuatorEdit : NotificationActuator {
    val scheduled = mutableListOf<ScheduledReminder>()
    val cancelled = mutableListOf<String>()

    override fun schedule(reminder: ScheduledReminder) {
        scheduled.add(reminder)
    }

    override fun cancel(reminderId: String) {
        cancelled.add(reminderId)
    }

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}

private class ThrowingCalculatorEdit(
    private val throws: MalformedScheduleException?,
) : ScheduleCalculator {
    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> {
        throws?.let { throw it }
        return emptyList()
    }
}
