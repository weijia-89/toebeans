package app.toebeans.android.di

import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakePetRepository
import app.toebeans.android.data.FakeScheduleRepository
import app.toebeans.android.ui.home.HomeViewModel
import app.toebeans.android.ui.pets.PetsViewModel
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module. Wires repositories as singletons and ViewModels with their factory
 * scope. Fake repositories are bound for the scaffold milestone; SQLDelight-backed
 * implementations will replace them via a milestone-1 swap (single-line edits).
 */
public val appModule =
    module {
        // Repositories
        single<PetRepository> { FakePetRepository() }
        single<MedicationRepository> { FakeMedicationRepository() }
        single<ScheduleRepository> { FakeScheduleRepository() }

        // ViewModels
        viewModel { HomeViewModel(petRepository = get()) }
        viewModel { PetsViewModel(petRepository = get()) }
    }
