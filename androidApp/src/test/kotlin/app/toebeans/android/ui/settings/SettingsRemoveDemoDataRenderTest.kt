package app.toebeans.android.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.toebeans.android.data.FakeDoseEventRepository
import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakePetRepository
import app.toebeans.android.data.FakeScheduleRepository
import app.toebeans.android.data.doseEvents
import app.toebeans.android.data.loadDemoData
import app.toebeans.android.data.medications
import app.toebeans.android.data.pets
import app.toebeans.android.data.phasesByScheduleId
import app.toebeans.android.data.schedules
import app.toebeans.android.preferences.ThemePreferences
import app.toebeans.core.backup.BackupAggregator
import app.toebeans.core.backup.BackupImporter
import app.toebeans.core.backup.BackupSerializer
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ADR-0013 rendered-UI contract for the Settings "Remove demo data" affordance (S3).
 *
 * The ViewModel-level contract (exactly the fixed demo id set, pending alarms cancelled,
 * no-op when absent) is pinned by [SettingsClearDemoDataTest]. This class pins the
 * rendered half: the destructive TextButton sits in the Data card after the Import row,
 * the confirmation dialog appears with the destructive copy, Cancel keeps all data, and
 * the confirm action wires through to [SettingsViewModel.clearDemoData].
 *
 * SDK 34 and the plain-Application override follow the
 * [app.toebeans.android.ui.schedule.ScheduleDetailDeleteDialogRenderTest] pattern:
 * no Koin boot, the ViewModel is injected directly, and the export/import VMs are real
 * classes over the same in-memory fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsRemoveDemoDataRenderTest {
    @get:Rule
    val rule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-08T15:00:00Z")
    private val timeZone: TimeZone = TimeZone.UTC

    private lateinit var petRepo: FakePetRepository
    private lateinit var medRepo: FakeMedicationRepository
    private lateinit var schedRepo: FakeScheduleRepository
    private lateinit var doseRepo: FakeDoseEventRepository
    private lateinit var actuator: CancelCountingActuator

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The fakes share one module-global store across the whole test JVM; reset it so
        // sibling test classes don't see each other's rows (same reset LoadDemoDataTest
        // performs for its horizon test).
        pets.value = emptyMap()
        medications.value = emptyMap()
        schedules.value = emptyMap()
        phasesByScheduleId.value = emptyMap()
        doseEvents.value = emptyMap()
        petRepo = FakePetRepository()
        medRepo = FakeMedicationRepository()
        schedRepo = FakeScheduleRepository()
        doseRepo = FakeDoseEventRepository(medRepo, schedRepo)
        actuator = CancelCountingActuator()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `remove demo data button opens the confirmation dialog with destructive copy`() {
        runTest { seedDemo() }
        rule.setContent { content() }

        rule.onNodeWithText("Remove demo data").performScrollTo().performClick()

        rule.onNodeWithText("Remove demo data?").assertIsDisplayed()
        rule
            .onNodeWithText(
                "This deletes the 2 demo pets, 6 demo medications, their schedules, " +
                    "and their dose history. This can't be undone.",
            ).assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun `cancel keeps the demo data`() {
        runTest { seedDemo() }
        rule.setContent { content() }
        rule.onNodeWithText("Remove demo data").performScrollTo().performClick()

        rule.onNodeWithText("Cancel").performClick()

        rule.waitForIdle()
        runTest {
            assertEquals(2, petRepo.observeAll().first().size)
            assertEquals(6, medRepo.observeAll().first().size)
            assertEquals(6, schedRepo.observeAll().first().size)
        }
        assertEquals(0, actuator.cancelCount)
    }

    @Test
    fun `confirm wires through to the ViewModel clear and the demo data is gone`() {
        runTest { seedDemo() }
        rule.setContent { content() }
        rule.onNodeWithText("Remove demo data").performScrollTo().performClick()

        rule.onNodeWithText("Remove").performClick()

        rule.waitForIdle()
        runTest {
            assertEquals(0, petRepo.observeAll().first().size)
            assertEquals(0, medRepo.observeAll().first().size)
            assertEquals(0, schedRepo.observeAll().first().size)
        }
        assertTrue("pending demo alarms should be cancelled", actuator.cancelCount > 0)
    }

    @Composable
    private fun content() {
        val viewModel =
            SettingsViewModel(
                prefs = ThemePreferences(RuntimeEnvironment.getApplication()),
                petRepository = petRepo,
                medicationRepository = medRepo,
                scheduleRepository = schedRepo,
                doseEventRepository = doseRepo,
                notificationActuator = actuator,
            )
        MaterialTheme {
            SettingsScreen(
                viewModel = viewModel,
                exportViewModel =
                    ExportBackupViewModel(
                        aggregator = BackupAggregator(petRepo, medRepo, schedRepo, doseRepo),
                        serializer = BackupSerializer(),
                        appVersion = "test",
                    ),
                importViewModel =
                    ImportBackupViewModel(
                        serializer = BackupSerializer(),
                        importer = BackupImporter(petRepo, medRepo, schedRepo, doseRepo),
                    ),
            )
        }
    }

    private suspend fun seedDemo() {
        loadDemoData(
            petRepository = petRepo,
            medicationRepository = medRepo,
            scheduleRepository = schedRepo,
            doseEventRepository = doseRepo,
            scheduleCalculator = DefaultScheduleCalculator(),
            notificationActuator = actuator,
            now = now,
            timeZone = timeZone,
        )
    }
}

private class CancelCountingActuator : NotificationActuator {
    var cancelCount = 0
        private set

    override fun schedule(reminder: ScheduledReminder) = Unit

    override fun cancel(reminderId: String) {
        cancelCount += 1
    }

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}
