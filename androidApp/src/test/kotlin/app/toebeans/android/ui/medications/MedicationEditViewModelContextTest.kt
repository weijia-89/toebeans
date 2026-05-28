package app.toebeans.android.ui.medications

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditViewModelContextTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setPetId and load populate pet name and schedule hint`() =
        runTest {
            val med = seedMed("med-1", "pet-1", "Methimazole")
            val schedule =
                Schedule(
                    id = "sch-1",
                    medicationId = "med-1",
                    startDate = LocalDate(2026, 1, 1),
                    endDate = null,
                    createdAt = Clock.System.now(),
                )
            val phase =
                SchedulePhase(
                    id = "ph-1",
                    scheduleId = "sch-1",
                    phaseOrder = 0,
                    durationDays = 30,
                    dosesPerDay = 2,
                    doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                    doseAmount = null,
                )
            val vm =
                medicationEditViewModel(
                    InMemoryMedRepo(med),
                    seedPet("pet-1", "Luna"),
                    InMemoryScheduleRepo(listOf(schedule), listOf(phase)),
                )
            vm.setPetId("pet-1")
            vm.load("med-1")
            assertEquals("Luna", vm.state.value.petName)
            val hint = vm.state.value.scheduleHint
            requireNotNull(hint)
            assertTrue(hint.contains("2026-01-01"))
            assertTrue(hint.contains("8:00 AM"))
        }
}
