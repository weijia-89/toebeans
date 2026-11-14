package app.toebeans.android.ui.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0013 rendered-UI contract for S2 schedule edit.
 *
 * Pins the rendered half of edit mode: with `editScheduleId` set and a loaded schedule,
 * the reused create form shows the "Edit schedule" title and the prefilled dose time
 * (the "20:00" button text of the preexisting phase's second TimePickerField), and the
 * top-bar Edit action on the Schedule Detail screen hands petId + medicationId to the
 * nav callback so the shell can open the prefilled form.
 *
 * SDK 34 and the plain-Application override follow the
 * [ScheduleDetailDeleteDialogRenderTest] pattern (ADR-0013 implementer notes); the
 * ViewModels are injected directly, no Koin boot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScheduleEditPrefillRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `edit mode renders the edit title and the prefilled dose time`() {
        val rig = rigEdit()
        val vm = rig.createVm()
        runBlocking { vm.loadForEdit(SCHED_ID) }

        rule.setContent {
            MaterialTheme {
                ScheduleCreateScreen(
                    petId = PET_ID,
                    medicationId = MED_ID,
                    editScheduleId = SCHED_ID,
                    onBack = {},
                    onSaved = {},
                    viewModel = vm,
                )
            }
        }

        rule.onNodeWithText("Edit schedule").assertIsDisplayed()
        // Both prefilled dose times must exist in the semantics tree; the phase card sits
        // below the fold in the test viewport, so existence (not visibility) is the pin.
        rule.onNodeWithText("08:00").assertExists()
        rule.onNodeWithText("20:00").assertExists()
    }

    @Test
    fun `top-bar Edit action on detail hands off pet and medication ids`() {
        val rig = rigEdit()
        val detailVm = rig.detailVm()
        detailVm.load(SCHED_ID)
        var editPetId: String? = null
        var editMedicationId: String? = null

        rule.setContent {
            MaterialTheme {
                ScheduleDetailScreen(
                    scheduleId = SCHED_ID,
                    onBack = {},
                    onEdit = { petId, medicationId ->
                        editPetId = petId
                        editMedicationId = medicationId
                    },
                    viewModel = detailVm,
                )
            }
        }

        rule.onNodeWithText("Edit").performClick()
        rule.waitForIdle()

        assertEquals(PET_ID, editPetId)
        assertEquals(MED_ID, editMedicationId)
    }
}

private const val PET_ID = "pet-luna"
private const val MED_ID = "med-amox"
private const val SCHED_ID = "sched-amox-1"

private class RigEdit(
    val petRepo: FakePetRepoEditRender,
    val medRepo: FakeMedRepoEditRender,
    val schedRepo: FakeScheduleRepoEditRender,
    val doseRepo: FakeDoseRepoEditRender,
) {
    fun createVm(): ScheduleCreateViewModel =
        ScheduleCreateViewModel(
            medicationRepository = medRepo,
            scheduleRepository = schedRepo,
            doseEventRepository = doseRepo,
            scheduleCalculator = DefaultScheduleCalculator(),
            notificationActuator = RecordingActuatorEditRender(),
            timeZone = TimeZone.UTC,
        )

    fun detailVm(): ScheduleDetailViewModel = ScheduleDetailViewModel(petRepo, medRepo, schedRepo)
}

private fun rigEdit(): RigEdit {
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
            doseAmount = "50",
            doseUnit = DoseUnit.MG,
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
                doseUnit = DoseUnit.MG,
                dayInterval = 1,
            ),
        )
    return RigEdit(
        petRepo = FakePetRepoEditRender(pet),
        medRepo = FakeMedRepoEditRender(med),
        schedRepo = FakeScheduleRepoEditRender(schedule, phases),
        doseRepo = FakeDoseRepoEditRender(),
    )
}

private class FakePetRepoEditRender(
    initial: Pet,
) : PetRepository {
    private val store = MutableStateFlow(mapOf(initial.id to initial))

    override fun observeAll(): Flow<List<Pet>> = store.map { it.values.toList() }

    override fun observeById(id: String): Flow<Pet?> = store.map { it[id] }

    override suspend fun getById(id: String): Pet? = store.value[id]

    override suspend fun upsert(pet: Pet) {
        store.value = store.value + (pet.id to pet)
    }

    override suspend fun delete(id: String) {
        store.value = store.value - id
    }
}

private class FakeMedRepoEditRender(
    initial: Medication,
) : MedicationRepository {
    private val store = MutableStateFlow(mapOf(initial.id to initial))

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

private class FakeScheduleRepoEditRender(
    initialSchedule: Schedule,
    initialPhases: List<SchedulePhase>,
) : ScheduleRepository {
    private val schedules = MutableStateFlow(mapOf(initialSchedule.id to initialSchedule))
    private val phases = MutableStateFlow(mapOf(initialSchedule.id to initialPhases))

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

private class FakeDoseRepoEditRender : DoseEventRepository {
    private val store = MutableStateFlow<List<app.toebeans.core.model.DoseEvent>>(emptyList())

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<app.toebeans.core.model.DoseEvent>> =
        MutableStateFlow(store.value.filter { it.scheduledAt >= sinceInclusive })

    override fun observeLastGivenForMedication(medicationId: String): Flow<app.toebeans.core.model.DoseEvent?> =
        MutableStateFlow(
            store.value
                .filter { it.medicationId == medicationId && it.status == DoseStatus.GIVEN }
                .maxByOrNull { it.scheduledAt },
        )

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<app.toebeans.core.model.DoseEvent>> =
        MutableStateFlow(store.value.filter { it.status == DoseStatus.GIVEN && it.scheduledAt >= sinceInclusive })

    override fun observeRecentForHistory(
        sinceInclusive: Instant,
        limit: Int,
    ): Flow<List<app.toebeans.core.model.DoseEvent>> =
        MutableStateFlow(
            store.value
                .filter { it.scheduledAt >= sinceInclusive }
                .sortedByDescending { it.scheduledAt }
                .take(limit),
        )

    override fun observeAll(): Flow<List<app.toebeans.core.model.DoseEvent>> = store

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): app.toebeans.core.model.DoseEvent = error("unused in edit render tests")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): app.toebeans.core.model.DoseEvent = error("unused in edit render tests")

    override suspend fun delete(doseEventId: String) {
        store.value = store.value.filterNot { it.id == doseEventId }
    }

    override suspend fun upsert(event: app.toebeans.core.model.DoseEvent) {
        store.value = store.value.filterNot { it.id == event.id } + event
    }

    override suspend fun markStalePendingAsMissed(cutoff: Instant): Int = 0
}

private class RecordingActuatorEditRender : NotificationActuator {
    override fun schedule(reminder: ScheduledReminder) = Unit

    override fun cancel(reminderId: String) = Unit

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}
