package app.toebeans.android.ui.schedule

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.ScheduledReminder
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
 * v0.1-followups #9 — midnight-straddle ("dose times cross midnight") warning during
 * phase creation.
 *
 * Per the prompt, the new banner is purely an informational nudge. The ViewModel
 * derivation lives on `PhaseDraft.crossesMidnight`, recomputed by `updatePhase` on
 * every edit by feeding the phase's `doseTimes` through
 * [MidnightStraddleDetection.crossesMidnight]. The boolean is NOT persisted on the
 * domain `SchedulePhase`; it is purely a UI derivation.
 *
 * These tests pin the state transitions a user would actually experience: editing the
 * dose-time list into a straddling configuration must flip the flag on, and editing
 * back out must flip it off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleCreateMidnightStraddleTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `editing dose times into a straddling configuration sets crossesMidnight true`() =
        runTest {
            val vm = newVm()
            // The initial blank draft uses [LocalTime(8, 0)] — single dose, does not
            // straddle. Confirms the recompute path produces false from the default
            // state before any user edits.
            assertFalse(
                "precondition: blank draft should not straddle",
                vm.state.value.phases[0]
                    .crossesMidnight,
            )

            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(23, 0), LocalTime(1, 0))) }

            assertTrue(
                "23:00 + 01:00 should flip crossesMidnight on",
                vm.state.value.phases[0]
                    .crossesMidnight,
            )
        }

    @Test
    fun `editing back to a non-straddling configuration clears crossesMidnight`() =
        runTest {
            val vm = newVm()
            // Step 1: enter a straddling configuration so the flag is on.
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(23, 0), LocalTime(1, 0))) }
            assertTrue(
                "precondition: straddle flag set after first edit",
                vm.state.value.phases[0]
                    .crossesMidnight,
            )

            // Step 2: user revises the schedule into a daytime rhythm. Flag must clear.
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(8, 0), LocalTime(20, 0))) }

            assertFalse(
                "flag should clear once dose times no longer straddle",
                vm.state.value.phases[0]
                    .crossesMidnight,
            )
        }

    // --- Test rig ----------------------------------------------------------

    private fun newVm(): ScheduleCreateViewModel =
        ScheduleCreateViewModel(
            medicationRepository = InMemMedRepoMidnight(MED),
            scheduleRepository = InMemSchedRepoMidnight(),
            doseEventRepository = NoopDoseRepoMidnight,
            scheduleCalculator = NoopCalculatorMidnight,
            notificationActuator = NoopNotificationActuatorMidnight,
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

/**
 * No-op calculator. The straddle warning is a UX nudge; it does not exercise the
 * calculator path, only the field-level derivation. Same shape as the test rig in
 * `ScheduleCreateNightDoseTest`.
 */
private object NoopCalculatorMidnight : ScheduleCalculator {
    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> = emptyList()
}

private class InMemMedRepoMidnight(
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

private class InMemSchedRepoMidnight : ScheduleRepository {
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

private object NoopDoseRepoMidnight : DoseEventRepository {
    override fun observeForPet(
        petId: String,
        sinceInclusive: kotlinx.datetime.Instant,
    ): kotlinx.coroutines.flow.Flow<List<DoseEvent>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeLastGivenForMedication(medicationId: String): kotlinx.coroutines.flow.Flow<DoseEvent?> =
        kotlinx.coroutines.flow.flowOf(null)

    override fun observeAllRecent(
        sinceInclusive: kotlinx.datetime.Instant,
    ): kotlinx.coroutines.flow.Flow<List<DoseEvent>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<DoseEvent>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: kotlinx.datetime.Instant,
        note: String?,
    ): DoseEvent = error("unused")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: kotlinx.datetime.Instant,
        resolvedAt: kotlinx.datetime.Instant,
        note: String?,
    ): DoseEvent = error("unused")

    override suspend fun delete(doseEventId: String) = Unit

    override suspend fun upsert(event: DoseEvent) = Unit
}

private object NoopNotificationActuatorMidnight : NotificationActuator {
    override fun schedule(reminder: ScheduledReminder) = Unit

    override fun cancel(reminderId: String) = Unit

    override fun show(reminder: ScheduledReminder) = Unit
}
