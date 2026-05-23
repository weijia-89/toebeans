package app.toebeans.android.di

import app.toebeans.android.BuildConfig
import app.toebeans.android.data.FakeDoseEventRepository
import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakePetRepository
import app.toebeans.android.preferences.FirstLaunchPreferences
import app.toebeans.android.preferences.ThemePreferences
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
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.SqlDelightScheduleRepository
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.ScheduleCalculator
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module. Wires repositories as singletons and ViewModels with their factory
 * scope. Fake repositories are bound for the scaffold milestone; SQLDelight-backed
 * implementations will replace them via a milestone-1 swap (single-line edits).
 */
public val appModule =
    module {
        // Repositories. M1 Decision 4a Phase 6 wires [ScheduleRepository] to SQLDelight;
        // Pet, Medication, and DoseEvent remain in-memory fakes until their SqlDelight
        // impls land (Phases 2–4 and 7). The hybrid is intentional: UI and backup tests
        // exercise the real schedule persistence path while sibling repos catch up.
        //
        // **When the full four-repo cutover lands:** the driver construction MUST include
        // an AndroidSqliteDriver.Callback that enables foreign-key enforcement via
        // `PRAGMA foreign_keys=ON`. SQLite has FKs off by default and SQLDelight does not
        // enable them automatically. See `docs/adr/0010-sqlite-foreign-keys.md` for the
        // canonical pattern and the test contract that gates the wire-up PR.
        single { DatabaseFactory(androidContext()).create() }
        single<PetRepository> { FakePetRepository() }
        single<MedicationRepository> { FakeMedicationRepository() }
        single<ScheduleRepository> {
            SqlDelightScheduleRepository(
                database = get<ToebeansDatabase>(),
                dispatcher = Dispatchers.IO,
            )
        }
        single<DoseEventRepository> { FakeDoseEventRepository() }

        // Schedule calculator (pure, KMP commonMain). Stateless, single instance is correct.
        // Vibe-dangerous per AGENTS.md; the binding is exercised at app startup by HomeViewModel.
        single<ScheduleCalculator> { DefaultScheduleCalculator() }

        // Preferences (SharedPreferences-backed; no new deps per AGENTS.md).
        single { ThemePreferences(androidContext()) }
        single { FirstLaunchPreferences(androidContext()) }

        // Backup pipeline per ADR-0016. The serializer is stateless (just wraps a
        // configured Json instance); aggregator and importer fan in/out over the
        // four repository contracts. All are pure-KMP; no Android types leak in
        // through their constructors so swapping the repository bindings for
        // SQLDelight-backed impls in milestone 1 leaves these untouched.
        single { BackupSerializer() }
        single {
            BackupAggregator(
                petRepository = get(),
                medicationRepository = get(),
                scheduleRepository = get(),
                doseEventRepository = get(),
            )
        }
        single {
            BackupImporter(
                petRepository = get(),
                medicationRepository = get(),
                scheduleRepository = get(),
                doseEventRepository = get(),
            )
        }

        // ViewModels
        viewModel {
            PetDetailViewModel(
                petRepository = get(),
                medicationRepository = get(),
                scheduleRepository = get(),
                doseEventRepository = get(),
            )
        }
        viewModel { PetEditViewModel(petRepository = get()) }
        viewModel { MedicationEditViewModel(medicationRepository = get()) }
        viewModel {
            ScheduleCreateViewModel(
                medicationRepository = get(),
                scheduleRepository = get(),
                scheduleCalculator = get(),
            )
        }
        viewModel {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            ScheduleDetailViewModel(
                petRepository = get(),
                medicationRepository = get(),
                scheduleRepository = get(),
            )
        }
        viewModel {
            HomeViewModel(
                petRepository = get(),
                medicationRepository = get(),
                scheduleRepository = get(),
                doseEventRepository = get(),
                scheduleCalculator = get(),
            )
        }
        viewModel {
            PetsViewModel(
                petRepository = get(),
                medicationRepository = get(),
            )
        }
        viewModel {
            ReminderListViewModel(
                petRepository = get(),
                medicationRepository = get(),
                scheduleRepository = get(),
            )
        }
        viewModel { SettingsViewModel(prefs = get()) }
        viewModel {
            ExportBackupViewModel(
                aggregator = get(),
                serializer = get(),
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
        viewModel {
            ImportBackupViewModel(
                serializer = get(),
                importer = get(),
            )
        }
    }
