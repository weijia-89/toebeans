package app.toebeans.android.ui.pets

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [PetEditViewModel.delete]. The delete path is short (3 lines) but it's the only
 * way today's UI can call the underlying [PetRepository.delete], so a regression here is the
 * difference between "users can clean up a mistake" and "the seed pets are permanent."
 *
 * No Robolectric here: the VM is plain `androidx.lifecycle.ViewModel` and the repository is
 * a tiny in-test fake. Dispatchers.Main is replaced for `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetEditViewModelDeleteTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete removes the loaded pet from the repository`() =
        runTest {
            val repo = InMemoryPetRepo(seedPet("pet-rufus", "Rufus"))
            val vm = PetEditViewModel(repo)
            vm.load("pet-rufus")
            // load() launches in viewModelScope — flush via UnconfinedTestDispatcher (which
            // runs eagerly). State is populated synchronously after the call returns.
            assertTrue(vm.delete())
            assertNull(repo.getById("pet-rufus"))
        }

    @Test
    fun `delete in new-pet mode is a no-op and returns false`() =
        runTest {
            val seed = seedPet("pet-rufus", "Rufus")
            val repo = InMemoryPetRepo(seed)
            val vm = PetEditViewModel(repo)
            // Never called load(): the form is in new-pet mode, petId is null.
            assertFalse(vm.delete())
            // Verify the repo is untouched — a careless implementation that "deletes whatever
            // is loaded" without the null check would clobber unrelated pets via the empty key.
            assertTrue(repo.getById("pet-rufus") === seed)
        }
}

private fun seedPet(
    id: String,
    name: String,
): Pet =
    Pet(
        id = id,
        name = name,
        species = Species.DOG,
        birthdate = LocalDate(2020, 1, 1),
        weightKg = 10.0,
        notes = null,
        createdAt = Clock.System.now(),
        archivedAt = null,
    )

private class InMemoryPetRepo(
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
