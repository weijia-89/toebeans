package app.toebeans.android.ui.settings

import app.toebeans.android.data.DemoDataIds
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
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ViewModel contract for the Settings "Remove demo data" affordance (S3).
 *
 * Demo rows are identified by the fixed demo id set in [DemoDataIds] (the schema does
 * not tag demo rows; the spec records this honest scoping). Seeding goes through the
 * real [loadDemoData] path so the ids under test are the ids the first-launch dialog
 * actually writes.
 *
 * Lifecycle honesty: clear never touches [app.toebeans.android.preferences.FirstLaunchPreferences];
 * `firstLaunchSeen` stays true so the first-launch dialog does not reappear. The VM has
 * no reference to that store, so this is pinned by construction, not by assertion here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsClearDemoDataTest {
    private val now: Instant = Instant.parse("2026-11-08T15:00:00Z")
    private val timeZone: TimeZone = TimeZone.UTC

    private lateinit var petRepo: FakePetRepository
    private lateinit var medRepo: FakeMedicationRepository
    private lateinit var schedRepo: FakeScheduleRepository
    private lateinit var doseRepo: FakeDoseEventRepository
    private lateinit var actuator: CancelRecordingNotificationActuator
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The fakes share one module-global store across the whole test JVM; reset it so
        // tests (and sibling classes) don't see each other's rows. Same reset
        // LoadDemoDataTest performs for its horizon test.
        pets.value = emptyMap()
        medications.value = emptyMap()
        schedules.value = emptyMap()
        phasesByScheduleId.value = emptyMap()
        doseEvents.value = emptyMap()
        petRepo = FakePetRepository()
        medRepo = FakeMedicationRepository()
        schedRepo = FakeScheduleRepository()
        doseRepo = FakeDoseEventRepository(medRepo, schedRepo)
        actuator = CancelRecordingNotificationActuator()
        viewModel =
            SettingsViewModel(
                prefs = ThemePreferences(RuntimeEnvironment.getApplication()),
                petRepository = petRepo,
                medicationRepository = medRepo,
                scheduleRepository = schedRepo,
                doseEventRepository = doseRepo,
                notificationActuator = actuator,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clear removes exactly the demo ids and the user pet survives`() =
        runTest {
            seedDemo()
            seedUserPet()
            viewModel.clearDemoData()

            val pets = petRepo.observeAll().first()
            assertEquals(listOf("pet-user-tabby"), pets.map { it.id })
            assertEquals(0, medRepo.observeAll().first().size)
            assertEquals(0, schedRepo.observeAll().first().size)
        }

    @Test
    fun `clear is a no-op when no demo rows exist`() =
        runTest {
            seedUserPet()
            viewModel.clearDemoData()

            assertEquals(1, petRepo.observeAll().first().size)
            assertEquals(0, actuator.cancelled.size)
        }

    @Test
    fun `every pending demo dose event is cancelled before deletion`() =
        runTest {
            seedDemo()
            val pendingDemoDoseIds =
                doseRepo
                    .observeAll()
                    .first()
                    .filter { it.status == DoseStatus.PENDING && it.scheduleId in DemoDataIds.scheduleIds }
                    .map { it.id }
            assertTrue("demo load should materialize pending doses", pendingDemoDoseIds.isNotEmpty())

            viewModel.clearDemoData()

            assertEquals(pendingDemoDoseIds.sorted(), actuator.cancelled.sorted())
        }

    @Test
    fun `given demo dose rows are not cancelled`() =
        runTest {
            seedDemo()
            val givenDose =
                doseRepo
                    .observeAll()
                    .first()
                    .first { it.status == DoseStatus.PENDING && it.scheduleId in DemoDataIds.scheduleIds }
            doseRepo.upsert(givenDose.copy(status = DoseStatus.GIVEN))

            viewModel.clearDemoData()

            assertTrue(givenDose.id !in actuator.cancelled)
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

    private suspend fun seedUserPet() {
        petRepo.upsert(
            Pet(
                id = "pet-user-tabby",
                name = "Tabby",
                species = Species.CAT,
                birthdate = LocalDate(2021, 5, 1),
                weightKg = 3.5,
                notes = null,
                createdAt = now,
                archivedAt = null,
            ),
        )
        assertNotNull(petRepo.getById("pet-user-tabby"))
    }
}

/** Records [cancel] calls; scheduling and showing stay inert. */
private class CancelRecordingNotificationActuator : NotificationActuator {
    val cancelled = mutableListOf<String>()
    val scheduled = mutableListOf<ScheduledReminder>()

    override fun schedule(reminder: ScheduledReminder) {
        scheduled.add(reminder)
    }

    override fun cancel(reminderId: String) {
        cancelled.add(reminderId)
    }

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}
