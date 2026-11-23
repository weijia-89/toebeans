package app.toebeans.android.ui.medications

import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            vm.onDoseUnitChange(DoseUnit.MG)
            assertTrue(vm.save())
            val savedId = vm.state.value.medicationId
            requireNotNull(savedId)
            assertEquals("bbbb", medRepo.getById(savedId)?.name)
            assertEquals(false, vm.state.value.isNew)
        }

    @Test
    fun `single-phase dose time edit writes through ScheduleRepository upsert using typed LocalTime API`() =
        runTest {
            val med = seedMed("med-1", "pet-1", "Methimazole")
            val schedule = schedule("s-1", "med-1")
            val phase = phase("s-1", phaseOrder = 0, time = LocalTime(8, 0))
            val scheduleRepo =
                InMemoryScheduleRepo(
                    initialSchedules = listOf(schedule),
                    initialPhases = listOf(phase),
                )
            val vm =
                medicationEditViewModel(
                    InMemoryMedRepo(initial = med),
                    seedPet("pet-1", "Luna"),
                    scheduleRepo,
                )
            vm.load("med-1")

            assertEquals(listOf(LocalTime(8, 0)), vm.state.value.doseTimes)
            assertTrue(vm.state.value.isSinglePhaseTimeEditable)

            assertTrue(vm.onUpdateTime(LocalTime(9, 30)))
            assertEquals(
                listOf(LocalTime(9, 30)),
                scheduleRepo
                    .observePhases("s-1")
                    .first()
                    .single()
                    .doseTimesLocal,
            )
            assertEquals(listOf(LocalTime(9, 30)), vm.state.value.doseTimes)
        }

    @Test
    fun `multi-phase medication exposes read-only dose times and refuses the guarded typed edit`() =
        runTest {
            val med = seedMed("med-1", "pet-1", "Methimazole")
            val schedule = schedule("s-1", "med-1")
            val phases =
                listOf(
                    phase("s-1", phaseOrder = 0, time = LocalTime(8, 0)),
                    phase("s-1", phaseOrder = 1, time = LocalTime(20, 0)),
                )
            val scheduleRepo =
                InMemoryScheduleRepo(
                    initialSchedules = listOf(schedule),
                    initialPhases = phases,
                )
            val vm =
                medicationEditViewModel(
                    InMemoryMedRepo(initial = med),
                    seedPet("pet-1", "Luna"),
                    scheduleRepo,
                )
            vm.load("med-1")

            assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), vm.state.value.doseTimes)
            assertFalse(vm.state.value.isSinglePhaseTimeEditable)

            assertFalse(vm.onUpdateTime(LocalTime(10, 0)))
            assertEquals(
                listOf(LocalTime(8, 0), LocalTime(20, 0)),
                scheduleRepo.observePhases("s-1").first().flatMap { it.doseTimesLocal },
            )
        }

    @Test
    fun `add mode has no dose times and refuses the guarded typed edit`() =
        runTest {
            val vm =
                medicationEditViewModel(
                    InMemoryMedRepo(),
                    seedPet("pet-1", "Luna"),
                    InMemoryScheduleRepo(),
                )
            vm.setPetId("pet-1")

            assertTrue(
                vm.state.value.doseTimes
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSinglePhaseTimeEditable)
            assertFalse(vm.onUpdateTime(LocalTime(9, 30)))
        }

    private fun schedule(
        id: String,
        medicationId: String,
    ): Schedule =
        Schedule(
            id = id,
            medicationId = medicationId,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
            createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        )

    private fun phase(
        scheduleId: String,
        phaseOrder: Int,
        time: LocalTime,
    ): SchedulePhase =
        SchedulePhase(
            id = "p-$scheduleId-$phaseOrder",
            scheduleId = scheduleId,
            phaseOrder = phaseOrder,
            durationDays = 14,
            dosesPerDay = 1,
            doseTimesLocal = listOf(time),
            doseAmount = null,
            doseUnit = DoseUnit.MG,
        )
}
