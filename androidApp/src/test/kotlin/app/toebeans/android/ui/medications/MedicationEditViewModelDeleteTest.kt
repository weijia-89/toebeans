package app.toebeans.android.ui.medications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
            val vm = medicationEditViewModel(repo)
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
            val vm = medicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            // Never load(): the form is in new-med mode.
            assertFalse(vm.delete())
            assertSame(seed, repo.getById("med-1"))
        }
}

// `seedMed` and `InMemoryMedRepo` live in MedicationEditTestFixtures.kt. They are shared
// across the MedicationEditViewModel* test family so two file-private copies don't
// collide at the Kotlin namespace level.
