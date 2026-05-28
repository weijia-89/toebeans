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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditViewModelLoadStaleTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load clears previous medication before fetch completes`() =
        runTest {
            val med1 = seedMed("med-1", "pet-1", "Alpha")
            val med2 = seedMed("med-2", "pet-1", "Beta")
            val repo = GatedMedRepo(med1, med2)
            val vm =
                medicationEditViewModel(
                    repo,
                    seedPet("pet-1", "Luna"),
                )
            vm.prepareRoute("pet-1", "med-1")
            assertEquals("Alpha", vm.state.value.name)

            vm.load("med-2")
            assertEquals("med-2", vm.state.value.medicationId)
            assertEquals("", vm.state.value.name)

            repo.releaseMed2()
            assertEquals("Beta", vm.state.value.name)
        }

    @Test
    fun `prepareRoute clears stale medicationId for add flow`() =
        runTest {
            val med = seedMed("med-1", "pet-1", "Alpha")
            val repo = InMemoryMedRepo(med)
            val vm =
                medicationEditViewModel(
                    repo,
                    seedPet("pet-1", "Luna"),
                )
            vm.prepareRoute("pet-1", "med-1")
            assertEquals("med-1", vm.state.value.medicationId)

            vm.prepareRoute("pet-1", null)
            assertNull(vm.state.value.medicationId)
            assertEquals("", vm.state.value.name)
            assertEquals("Luna", vm.state.value.petName)
        }
}

/** Blocks [getById] for med-2 until [releaseMed2] so tests can observe the cleared interim state. */
private class GatedMedRepo(
    med1: Medication,
    private val med2: Medication,
) : MedicationRepository {
    private val store = MutableStateFlow(mapOf(med1.id to med1, med2.id to med2))
    private val gate = Mutex(locked = true)

    fun releaseMed2() {
        gate.unlock()
    }

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override fun observeAll(): Flow<List<Medication>> = store.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: String): Medication? {
        if (id == med2.id) {
            gate.withLock { }
        }
        return store.value[id]
    }

    override suspend fun upsert(medication: Medication) {
        store.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }
}
