package app.toebeans.android.ui.pets

import app.toebeans.android.preferences.WeightUnit
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * D6 read/write boundary for [PetEditViewModel]. Storage stays `weight_kg`; the ViewModel
 * converts at the edge using the app-wide preference value passed in at construction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetEditViewModelWeightUnitTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load converts stored kilograms to pounds in the edit field`() =
        runTest {
            val repo = WeightUnitPetRepo(seedPet(10.0))
            val vm = PetEditViewModel(repo, WeightUnit.POUNDS)
            vm.load("pet-1")
            assertEquals("22.0", vm.state.value.weightKgText)
        }

    @Test
    fun `save converts entered pounds to kilograms for storage`() =
        runTest {
            val repo = WeightUnitPetRepo(seedPet(10.0))
            val vm = PetEditViewModel(repo, WeightUnit.POUNDS)
            vm.load("pet-1")
            vm.onWeightChange("22.0462262185")
            assert(vm.save())
            assertEquals(10.0, repo.getById("pet-1")!!.weightKg!!, 1e-6)
        }

    @Test
    fun `kilogram preference loads and saves kilograms unchanged`() =
        runTest {
            val repo = WeightUnitPetRepo(seedPet(12.4))
            val vm = PetEditViewModel(repo, WeightUnit.KILOGRAMS)
            vm.load("pet-1")
            assertEquals("12.4", vm.state.value.weightKgText)
            vm.onWeightChange("13.5")
            assert(vm.save())
            assertEquals(13.5, repo.getById("pet-1")!!.weightKg!!, 0.0)
        }

    @Test
    fun `invalid weight still surfaces an error in the chosen unit`() =
        runTest {
            val repo = WeightUnitPetRepo(seedPet(10.0))
            val vm = PetEditViewModel(repo, WeightUnit.POUNDS)
            vm.load("pet-1")
            vm.onWeightChange("0")
            assertFalse(vm.save())
            assertNotNull(vm.state.value.weightError)
        }
}

private fun seedPet(weightKg: Double): Pet =
    Pet(
        id = "pet-1",
        name = "Rufus",
        species = Species.DOG,
        birthdate = LocalDate(2020, 1, 1),
        weightKg = weightKg,
        notes = null,
        createdAt = Clock.System.now(),
        archivedAt = null,
    )

private class WeightUnitPetRepo(
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
