package app.toebeans.android.di

import app.toebeans.android.data.FakeDoseEventRepository
import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakePetRepository
import app.toebeans.android.data.FakeScheduleRepository
import app.toebeans.android.preferences.FirstLaunchPreferences
import app.toebeans.android.preferences.ThemePreferences
import app.toebeans.android.ui.home.HomeViewModel
import app.toebeans.android.ui.medications.MedicationEditViewModel
import app.toebeans.android.ui.pets.PetDetailViewModel
import app.toebeans.android.ui.pets.PetEditViewModel
import app.toebeans.android.ui.pets.PetsViewModel
import app.toebeans.android.ui.schedule.ScheduleCreateViewModel
import app.toebeans.android.ui.settings.SettingsViewModel
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.ScheduleCalculator
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
        // Repositories, all in-memory fakes for the scaffold milestone. Each one will be
        // swapped for an SQLDelight-backed impl in one edit when the persistence layer
        // lands.
        //
        // **When SQLDelight wires up (milestone 1):** the driver construction MUST include
        // an AndroidSqliteDriver.Callback that enables foreign-key enforcement via
        // `PRAGMA foreign_keys=ON`. SQLite has FKs off by default and SQLDelight does not
        // enable them automatically. See `docs/adr/0010-sqlite-foreign-keys.md` for the
        // canonical pattern and the test contract that gates the wire-up PR.
        single<PetRepository> { FakePetRepository() }
        single<MedicationRepository> { FakeMedicationRepository() }
        single<ScheduleRepository> { FakeScheduleRepository() }
        single<DoseEventRepository> { FakeDoseEventRepository() }

        // Schedule calculator (pure, KMP commonMain). Stateless, single instance is correct.
        // Vibe-dangerous per AGENTS.md; the binding is exercised at app startup by HomeViewModel.
        single<ScheduleCalculator> { DefaultScheduleCalculator() }

        // Preferences (SharedPreferences-backed; no new deps per AGENTS.md).
        single { ThemePreferences(androidContext()) }
        single { FirstLaunchPreferences(androidContext()) }

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
        viewModel { ScheduleCreateViewModel(medicationRepository = get(), scheduleRepository = get()) }
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
        viewModel { SettingsViewModel(prefs = get()) }
    }
