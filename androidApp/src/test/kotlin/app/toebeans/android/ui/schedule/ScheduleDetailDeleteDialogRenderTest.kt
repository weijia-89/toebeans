package app.toebeans.android.ui.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0013 rendered-UI contract for B7: the Schedule Detail delete confirmation
 * dialog.
 *
 * The ViewModel-level contract (delete() returns true, schedule + phases removed) is
 * pinned by [ScheduleDetailViewModelTest]. This class pins the rendered half: the
 * dialog actually appears from the top-bar Delete affordance, the "This can't be
 * undone." copy is visible to the user, the confirm action is a distinct node from the
 * top-bar Delete, and confirming wires through to [ScheduleDetailViewModel.delete] and
 * the onBack pop.
 *
 * The dialog's confirm button carries the MaterialTheme error container tint. Compose
 * semantics do not expose colors, so the tint is not machine-checkable without the
 * snapshot frameworks ADR-0013 rejected; the rendered contract pinned here is the
 * dialog structure, copy, and callback wiring.
 *
 * SDK 33 and the plain-Application override follow the
 * [app.toebeans.android.ui.home.NotificationDenialBannerRenderTest] pattern (ADR-0013
 * implementer notes): POST_NOTIFICATIONS fork, and no Koin boot since the ViewModel is
 * injected directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ScheduleDetailDeleteDialogRenderTest {
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
    fun `delete confirmation dialog renders with the cannot-be-undone copy and confirm action`() {
        rule.setContent { content(viewModel = rigViewModel()) }

        rule.onNodeWithText("Delete").performClick()

        rule.onNodeWithText("Delete reminder?").assertIsDisplayed()
        rule
            .onNodeWithText(
                "Deleting the reminder for Amoxicillin will also remove its schedule history. " +
                    "This can't be undone.",
            ).assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        assertEquals(
            "top-bar Delete and dialog confirm Delete should both exist while the dialog is open",
            2,
            rule.onAllNodesWithText("Delete").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `confirming the dialog deletes the schedule and pops back`() {
        val rig = rigB7Render()
        var backCount = 0
        val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
        vm.load(SCHED_ID_B7_RENDER)
        rule.setContent {
            MaterialTheme {
                ScheduleDetailScreen(
                    scheduleId = SCHED_ID_B7_RENDER,
                    onBack = { backCount += 1 },
                    viewModel = vm,
                )
            }
        }

        rule.onNodeWithText("Delete").performClick()
        dialogConfirmDelete().performClick()
        rule.waitForIdle()

        assertEquals("confirm should delete the schedule exactly once", 1, rig.sched.deleteCalls)
        assertTrue("schedule row should be gone after confirm", rig.sched.snapshot().isEmpty())
        assertTrue(
            "screen should pop back after a confirmed delete (confirm pop or auto-pop on null schedule)",
            backCount >= 1,
        )
    }

    @Composable
    private fun content(viewModel: ScheduleDetailViewModel) {
        ScheduleDetailScreen(
            scheduleId = SCHED_ID_B7_RENDER,
            onBack = {},
            viewModel = viewModel,
        )
    }

    private fun rigViewModel(): ScheduleDetailViewModel {
        val rig = rigB7Render()
        val vm = ScheduleDetailViewModel(rig.pet, rig.med, rig.sched)
        vm.load(SCHED_ID_B7_RENDER)
        return vm
    }

    /**
     * The top-bar Delete and the dialog confirm both have the exact text "Delete". The
     * dialog confirm is the lower node on screen, so pick it by position. Touch level,
     * not semantics, does the click; no production code change is needed to
     * disambiguate.
     */
    private fun dialogConfirmDelete(): SemanticsNodeInteraction {
        val nodes = rule.onAllNodesWithText("Delete").fetchSemanticsNodes()
        val confirmIndex = nodes.indices.maxBy { nodes[it].boundsInRoot.center.y }
        return rule.onAllNodesWithText("Delete")[confirmIndex]
    }
}

private const val PET_ID_B7_RENDER = "pet-luna"
private const val MED_ID_B7_RENDER = "med-amox"
private const val SCHED_ID_B7_RENDER = "sched-amox-1"

private class RigB7Render(
    val pet: InMemoryPetRepoB7Render,
    val med: InMemoryMedRepoB7Render,
    val sched: InMemoryScheduleRepoB7Render,
)

private fun rigB7Render(): RigB7Render {
    val now = Clock.System.now()
    val pet =
        Pet(
            id = PET_ID_B7_RENDER,
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
            id = MED_ID_B7_RENDER,
            petId = PET_ID_B7_RENDER,
            name = "Amoxicillin",
            doseAmount = "50",
            doseUnit = DoseUnit.MG,
            notes = null,
            createdAt = now,
            discontinuedAt = null,
        )
    val schedule =
        Schedule(
            id = SCHED_ID_B7_RENDER,
            medicationId = MED_ID_B7_RENDER,
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2026, 5, 14),
            createdAt = now,
        )
    val phases =
        listOf(
            SchedulePhase(
                id = "p-1",
                scheduleId = SCHED_ID_B7_RENDER,
                phaseOrder = 0,
                durationDays = 7,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
                doseUnit = DoseUnit.MG,
                dayInterval = 1,
            ),
        )
    return RigB7Render(
        pet = InMemoryPetRepoB7Render(pet),
        med = InMemoryMedRepoB7Render(med),
        sched = InMemoryScheduleRepoB7Render(schedule, phases),
    )
}

private class InMemoryPetRepoB7Render(
    initial: Pet,
) : PetRepository {
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

private class InMemoryMedRepoB7Render(
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

private class InMemoryScheduleRepoB7Render(
    initialSchedule: Schedule,
    initialPhases: List<SchedulePhase>,
) : ScheduleRepository {
    private val schedules = MutableStateFlow(mapOf(initialSchedule.id to initialSchedule))
    private val phases = MutableStateFlow(mapOf(initialSchedule.id to initialPhases))
    var deleteCalls: Int = 0
        private set

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phases.asStateFlow().map {
            it[scheduleId]
                ?: emptyList()
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        combine(schedules.asStateFlow(), phases.asStateFlow()) { scheds, phaseMap ->
            scheds.values
                .filter { (it.endDate == null || it.endDate!! >= onOrAfter) }
                .map { ScheduleWithPhases(it, phaseMap[it.id] ?: emptyList()) }
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
        deleteCalls += 1
        schedules.update { it - id }
        phases.update { it - id }
    }

    fun snapshot(): Map<String, Schedule> = schedules.value
}
