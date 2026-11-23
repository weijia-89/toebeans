package app.toebeans.android.ui.medications

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rendered-UI contract for the D1 "End" bottom-bar action menu on Medication edit.
 *
 * The ViewModel-level contracts (discontinue / reactivate / delete) stay in the
 * `MedicationEditViewModel*` unit tests. This class pins the rendered half that those
 * tests cannot see: the destructive actions are reachable through a single trailing
 * "End" menu in the bottom bar, Reactivate stays immediate, "End" is hidden in add
 * mode, and the discontinued banner's spoken announcement names the new location.
 *
 * Pattern: SDK 34, plain Application, no Koin boot — the ViewModel is injected
 * directly, following `ScheduleDetailDeleteDialogRenderTest` (ADR-0013).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MedicationEditBottomActionsRenderTest {
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
    fun `end menu in bottom bar reveals Discontinue and Delete for an active medication`() {
        val vm = editViewModel(discontinued = false)
        rule.setContent { content(vm) }

        rule.onNodeWithText("End").assertIsDisplayed()
        rule.onNodeWithText("Save changes").assertIsDisplayed()

        rule.onNodeWithText("End").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Discontinue").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun `end menu reveals Reactivate instead of Discontinue for a discontinued medication`() {
        val vm = editViewModel(discontinued = true)
        rule.setContent { content(vm) }

        rule.onNodeWithText("End").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Reactivate").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onAllNodesWithText("Discontinue").assertCountEquals(0)
    }

    @Test
    fun `end menu is hidden in add mode while Save medication stays visible`() {
        val vm = addViewModel()
        rule.setContent { content(vm) }

        rule.onNodeWithText("Save medication").assertIsDisplayed()
        rule.onAllNodesWithText("End").assertCountEquals(0)
    }

    @Test
    fun `discontinued banner announcement names the End menu`() {
        val vm = editViewModel(discontinued = true)
        rule.setContent { content(vm) }

        rule
            .onNodeWithContentDescription(
                "Reminders are off. Open End in the bottom bar and tap Reactivate to resume.",
                substring = true,
            ).assertIsDisplayed()
    }

    @Test
    fun `dose time control is a read-only picker trigger, not a free-text field, and opens the time picker`() {
        val vm = editViewModelWithSinglePhase()
        rule.setContent { content(vm) }

        rule.onNodeWithText("08:00").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Change dose time").assertHasClickAction()
        rule.onAllNodesWithText("Update time").assertCountEquals(0)

        rule.onNodeWithContentDescription("Change dose time").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("OK").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun content(vm: MedicationEditViewModel) {
        MaterialTheme {
            MedicationEditScreen(
                petId = "pet-1",
                medicationId = vm.state.value.medicationId,
                onBack = {},
                onSaved = {},
                viewModel = vm,
            )
        }
    }

    private fun editViewModel(discontinued: Boolean): MedicationEditViewModel {
        val base = seedMed(id = "med-1", petId = "pet-1", name = "Methimazole")
        val med =
            if (discontinued) {
                base.copy(discontinuedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L))
            } else {
                base
            }
        val vm =
            medicationEditViewModel(
                medRepo = InMemoryMedRepo(initial = med),
                pet = seedPet("pet-1", "Luna"),
                scheduleRepo = InMemoryScheduleRepo(),
            )
        vm.load("med-1")
        return vm
    }

    private fun addViewModel(): MedicationEditViewModel =
        medicationEditViewModel(
            medRepo = InMemoryMedRepo(initial = null),
            pet = seedPet("pet-1", "Luna"),
            scheduleRepo = InMemoryScheduleRepo(),
        )

    private fun editViewModelWithSinglePhase(): MedicationEditViewModel {
        val schedule =
            Schedule(
                id = "s-1",
                medicationId = "med-1",
                startDate = LocalDate(2026, 1, 1),
                endDate = null,
                createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            )
        val phase =
            SchedulePhase(
                id = "p-s-1-0",
                scheduleId = "s-1",
                phaseOrder = 0,
                durationDays = 14,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
                doseUnit = DoseUnit.MG,
            )
        val vm =
            medicationEditViewModel(
                medRepo = InMemoryMedRepo(initial = seedMed(id = "med-1", petId = "pet-1", name = "Methimazole")),
                pet = seedPet("pet-1", "Luna"),
                scheduleRepo =
                    InMemoryScheduleRepo(
                        initialSchedules = listOf(schedule),
                        initialPhases = listOf(phase),
                    ),
            )
        vm.load("med-1")
        return vm
    }
}
