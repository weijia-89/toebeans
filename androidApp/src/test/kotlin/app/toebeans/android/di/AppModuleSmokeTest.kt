package app.toebeans.android.di

import app.toebeans.android.ui.home.HomeViewModel
import app.toebeans.android.ui.medications.MedicationEditViewModel
import app.toebeans.android.ui.pets.PetDetailViewModel
import app.toebeans.android.ui.pets.PetEditViewModel
import app.toebeans.android.ui.pets.PetsViewModel
import app.toebeans.android.ui.reminders.ReminderListViewModel
import app.toebeans.android.ui.schedule.ScheduleCreateViewModel
import app.toebeans.android.ui.schedule.ScheduleDetailViewModel
import app.toebeans.android.ui.settings.ExportBackupViewModel
import app.toebeans.android.ui.settings.ImportBackupViewModel
import app.toebeans.android.ui.settings.SettingsViewModel
import app.toebeans.core.backup.BackupAggregator
import app.toebeans.core.backup.BackupImporter
import app.toebeans.core.backup.BackupSerializer
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Smoke test for [appModule]. Catches the class of bug where a ViewModel constructor adds a
 * new dependency but the DI module is not updated — the unit tests that build the VM directly
 * with mocks pass, `assembleDebug` passes (compile-time only), and the crash only appears on
 * first launch when Koin tries to resolve the missing binding.
 *
 * History: a `ScheduleCalculator` parameter on `HomeViewModel` shipped without a corresponding
 * `single<ScheduleCalculator>` binding. The app crashed on Home tab open with Koin's
 * `NoBeanDefFoundException`. None of the existing gates caught it. This test would have.
 *
 * Robolectric is required because [appModule] registers `ThemePreferences(androidContext())`,
 * which needs a real Android Context. SDK 33 matches the rest of the androidApp test suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppModuleSmokeTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `appModule resolves every ViewModel`() {
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModule)
        }
        // If any of these throw NoBeanDefFoundException the test fails with the exact missing
        // binding identified. Adding a new ViewModel to AppModule requires adding it here too.
        assertNotNull(getKoin().get<HomeViewModel>())
        assertNotNull(getKoin().get<PetsViewModel>())
        assertNotNull(getKoin().get<PetDetailViewModel>())
        assertNotNull(getKoin().get<PetEditViewModel>())
        assertNotNull(getKoin().get<MedicationEditViewModel>())
        assertNotNull(getKoin().get<ScheduleCreateViewModel>())
        assertNotNull(getKoin().get<ScheduleDetailViewModel>())
        assertNotNull(getKoin().get<ReminderListViewModel>())
        assertNotNull(getKoin().get<SettingsViewModel>())
        assertNotNull(getKoin().get<ExportBackupViewModel>())
        assertNotNull(getKoin().get<ImportBackupViewModel>())
        // Also verify the singleton backup pipeline beans resolve, since the VMs above
        // already exercise their constructors via Koin. Asserting them explicitly here
        // produces a clearer failure if the singleton registration breaks (vs the
        // diagnostic landing on the VM that depends on it).
        assertNotNull(getKoin().get<BackupSerializer>())
        assertNotNull(getKoin().get<BackupAggregator>())
        assertNotNull(getKoin().get<BackupImporter>())
    }
}
