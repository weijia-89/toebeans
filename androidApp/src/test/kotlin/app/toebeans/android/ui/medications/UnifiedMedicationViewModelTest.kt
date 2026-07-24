package app.toebeans.android.ui.medications

import app.toebeans.android.data.FakeDoseEventRepository
import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakeScheduleRepository
import app.toebeans.core.data.InMemoryMedicationNameIndex
import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private object FakeNotificationActuator : NotificationActuator {
    override fun schedule(reminder: ScheduledReminder) {}

    override fun cancel(reminderId: String) {}

    override fun show(reminder: ScheduledReminder) {}
}

class UnifiedMedicationViewModelTest {
    private fun createViewModel(
        medicationRepo: FakeMedicationRepository = FakeMedicationRepository(),
        scheduleRepo: FakeScheduleRepository = FakeScheduleRepository(),
        doseEventRepo: FakeDoseEventRepository = FakeDoseEventRepository(medicationRepo, scheduleRepo),
    ): UnifiedMedicationViewModel =
        UnifiedMedicationViewModel(
            medicationRepository = medicationRepo,
            scheduleRepository = scheduleRepo,
            doseEventRepository = doseEventRepo,
            scheduleCalculator = DefaultScheduleCalculator(),
            notificationActuator = FakeNotificationActuator,
            medicationNameIndex = InMemoryMedicationNameIndex(emptyList()),
            timeZone = TimeZone.UTC,
        )

    @Test
    fun `save valid state creates medication and schedule`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("Amoxicillin")
            vm.onDoseAmountChange("10")
            vm.onDoseUnitChange(DoseUnit.MG)
            vm.onStartDateChange(LocalDate(2026, 1, 1))
            val scheduleId = vm.save()
            assertNotNull(scheduleId)
            assertEquals("Amoxicillin", vm.state.value.name)
        }

    @Test
    fun `save blank name surfaces name error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onDoseAmountChange("10")
            vm.onDoseUnitChange(DoseUnit.MG)
            val result = vm.save()
            assertNull(result)
            assertEquals("Required", vm.state.value.nameError)
        }

    @Test
    fun `save blank dose amount surfaces dose amount error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("Amoxicillin")
            vm.onDoseUnitChange(DoseUnit.MG)
            val result = vm.save()
            assertNull(result)
            assertEquals("Required", vm.state.value.doseAmountError)
        }

    @Test
    fun `save null dose unit surfaces dose unit error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("Amoxicillin")
            vm.onDoseAmountChange("10")
            val result = vm.save()
            assertNull(result)
            assertEquals("Required", vm.state.value.doseUnitError)
        }

    @Test
    fun `save null start date surfaces start date error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("Amoxicillin")
            vm.onDoseAmountChange("10")
            vm.onDoseUnitChange(DoseUnit.MG)
            vm.onStartDateChange(null)
            val result = vm.save()
            assertNull(result)
            assertEquals("Required", vm.state.value.startDateError)
        }

    @Test
    fun `save invalid phase surfaces phase error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("Amoxicillin")
            vm.onDoseAmountChange("10")
            vm.onDoseUnitChange(DoseUnit.MG)
            vm.updatePhase(0) { it.copy(durationDaysText = "0") }
            val result = vm.save()
            assertNull(result)
            assertNotNull(
                vm.state.value.phases[0]
                    .error,
            )
        }

    @Test
    fun `save preflight failure surfaces form error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("Amoxicillin")
            vm.onDoseAmountChange("10")
            vm.onDoseUnitChange(DoseUnit.MG)
            vm.onAnchorModeChange(AnchorMode.ELAPSED_INTERVAL)
            vm.updatePhase(0) { it.copy(dayIntervalText = "2") }
            val result = vm.save()
            assertNull(result)
            assertNotNull(vm.state.value.formError)
        }

    @Test
    fun `field mutation clears own error`() =
        runTest {
            val vm = createViewModel()
            vm.setPetId("pet-1")
            vm.onNameChange("")
            vm.save()
            assertNotNull(vm.state.value.nameError)
            vm.onNameChange("A")
            assertNull(vm.state.value.nameError)
        }

    @Test
    fun `update phase recomputes night dose warning`() =
        runTest {
            val vm = createViewModel()
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(3, 0))) }
            assertTrue(
                vm.state.value.phases[0]
                    .nightDoseWarning,
            )
        }

    @Test
    fun `affirm night dose clears warning`() =
        runTest {
            val vm = createViewModel()
            vm.updatePhase(0) { it.copy(doseTimes = listOf(LocalTime(3, 0))) }
            vm.affirmNightDose(0)
            assertEquals(
                false,
                vm.state.value.phases[0]
                    .nightDoseWarning,
            )
        }

    @Test
    fun `add phase appends blank phase`() =
        runTest {
            val vm = createViewModel()
            vm.addPhase()
            assertEquals(2, vm.state.value.phases.size)
        }

    @Test
    fun `remove phase removes at index`() =
        runTest {
            val vm = createViewModel()
            vm.addPhase()
            vm.removePhase(0)
            assertEquals(1, vm.state.value.phases.size)
        }
}
