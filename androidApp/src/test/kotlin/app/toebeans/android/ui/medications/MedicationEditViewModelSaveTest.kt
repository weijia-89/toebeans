package app.toebeans.android.ui.medications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditViewModelSaveTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save assigns medicationId and persists for observeAll consumers`() =
        runTest {
            val medRepo = InMemoryMedRepo(initial = null)
            val vm =
                medicationEditViewModel(
                    medRepo,
                    seedPet("pet-1", "Luna"),
                    InMemoryScheduleRepo(),
                )
            vm.setPetId("pet-1")
            vm.onNameChange("bbbb")
            vm.onDoseAmountChange("44")
            assertTrue(vm.save())
            val savedId = vm.state.value.medicationId
            requireNotNull(savedId)
            assertEquals("bbbb", medRepo.getById(savedId)?.name)
            assertEquals(false, vm.state.value.isNew)
        }
}
