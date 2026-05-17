package app.toebeans.android.ui.medications

import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.model.Medication
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
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Sibling of `PetEditViewModelDeleteTest`. Same shape, medication-flavored. */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditViewModelDeleteTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete removes the loaded medication from the repository`() =
        runTest {
            val repo = InMemoryMedRepo(seedMed("med-1", "pet-luna", "Methimazole"))
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            assertTrue(vm.delete())
            assertNull(repo.getById("med-1"))
        }

    @Test
    fun `delete in new-medication mode is a no-op and returns false`() =
        runTest {
            val seed = seedMed("med-1", "pet-luna", "Methimazole")
            val repo = InMemoryMedRepo(seed)
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            // Never load(): the form is in new-med mode.
            assertFalse(vm.delete())
            assertSame(seed, repo.getById("med-1"))
        }
}

private fun seedMed(
    id: String,
    petId: String,
    name: String,
): Medication =
    Medication(
        id = id,
        petId = petId,
        name = name,
        doseAmount = "2.5 mg",
        notes = null,
        createdAt = Clock.System.now(),
        discontinuedAt = null,
    )

private class InMemoryMedRepo(
    initial: Medication,
) : MedicationRepository {
    private val store = MutableStateFlow(mapOf(initial.id to initial))

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override fun observeAll(): Flow<List<Medication>> = store.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: String): Medication? = store.value[id]

    override suspend fun upsert(medication: Medication) {
        store.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }
}
