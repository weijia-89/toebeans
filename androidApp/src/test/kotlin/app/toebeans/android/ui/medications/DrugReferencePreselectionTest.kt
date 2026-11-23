package app.toebeans.android.ui.medications

import app.toebeans.android.data.DrugReferenceEntry
import app.toebeans.android.data.DrugReferenceReader
import app.toebeans.android.data.FakeDoseEventRepository
import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakeScheduleRepository
import app.toebeans.core.data.InMemoryMedicationNameIndex
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDrugReferenceReader(
    private val byName: Map<String, DrugReferenceEntry> = emptyMap(),
) : DrugReferenceReader {
    override fun find(name: String): DrugReferenceEntry? = byName[name.trim().lowercase()]
}

private class NoopActuator : NotificationActuator {
    override fun schedule(reminder: ScheduledReminder) {}

    override fun cancel(reminderId: String) {}

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}

/**
 * D7 narrow mechanism. Every fixture uses a synthetic medication name and carries no
 * dose amount, no advice text and no model output: only a formulation and a unit.
 */
class DrugReferencePreselectionTest {
    private fun vm(reader: DrugReferenceReader) =
        UnifiedMedicationViewModel(
            medicationRepository = FakeMedicationRepository(),
            scheduleRepository = FakeScheduleRepository(),
            doseEventRepository = FakeDoseEventRepository(FakeMedicationRepository(), FakeScheduleRepository()),
            scheduleCalculator = DefaultScheduleCalculator(),
            notificationActuator = NoopActuator(),
            medicationNameIndex = InMemoryMedicationNameIndex(emptyList()),
            timeZone = TimeZone.UTC,
            drugReferenceReader = reader,
        )

    private fun today() =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.UTC)
            .date

    @Test
    fun `approved entry preselects unit and narrows picker pills first`() =
        runTest {
            val vm =
                vm(
                    FakeDrugReferenceReader(
                        mapOf(
                            "testsolid" to
                                DrugReferenceEntry("TestSolid", "tablet", DoseUnit.TABLET),
                        ),
                    ),
                )

            vm.onNameChange("TestSolid")

            val state = vm.state.value
            assertEquals(DoseUnit.TABLET, state.doseUnit)
            assertEquals(DoseUnit.TABLET, state.suggestedUnits.first())
            assertTrue(DoseUnit.CAPSULE in state.suggestedUnits)
            assertTrue(state.suggestedUnits.size < DoseUnit.entries.size)
            assertEquals(state.suggestedUnits, state.effectiveUnits)
        }

    @Test
    fun `no entry leaves unit unset keeps full picker and does not block save`() =
        runTest {
            val vm = vm(FakeDrugReferenceReader())

            vm.onNameChange("UnknownMedication")
            val state = vm.state.value
            assertNull(state.doseUnit)
            assertEquals(DoseUnit.entries.toList(), state.suggestedUnits)
            assertEquals(DoseUnit.entries.size, state.effectiveUnits.size)

            vm.setPetId("pet-1")
            vm.onDoseAmountChange("10")
            vm.onDoseUnitChange(DoseUnit.ML)
            assertNotNull(vm.save())
        }

    @Test
    fun `override restores the full unit list`() =
        runTest {
            val vm =
                vm(
                    FakeDrugReferenceReader(
                        mapOf("testsolid" to DrugReferenceEntry("TestSolid", "tablet", DoseUnit.TABLET)),
                    ),
                )
            vm.onNameChange("TestSolid")
            assertTrue(vm.state.value.effectiveUnits.size < DoseUnit.entries.size)

            vm.onShowAllUnitsChange(true)

            assertEquals(DoseUnit.entries.toList(), vm.state.value.effectiveUnits)
        }

    @Test
    fun `soft warning appears for decimal amount with count unit`() =
        runTest {
            val vm =
                vm(
                    FakeDrugReferenceReader(
                        mapOf("testsolid" to DrugReferenceEntry("TestSolid", "tablet", DoseUnit.TABLET)),
                    ),
                )
            vm.onNameChange("TestSolid")

            vm.onDoseAmountChange("1.5")
            assertTrue(vm.state.value.unitMismatchWarning)

            vm.onDoseAmountChange("2")
            assertFalse(vm.state.value.unitMismatchWarning)
        }

    @Test
    fun `soft warning is dismissible and never blocks save`() =
        runTest {
            val vm =
                vm(
                    FakeDrugReferenceReader(
                        mapOf("testsolid" to DrugReferenceEntry("TestSolid", "tablet", DoseUnit.TABLET)),
                    ),
                )
            vm.onNameChange("TestSolid")
            vm.onDoseAmountChange("1.5")
            assertTrue(vm.state.value.unitMismatchWarning)
            assertNull(vm.state.value.doseAmountError)

            vm.dismissUnitMismatchWarning()
            assertFalse(vm.state.value.unitMismatchWarning)

            vm.setPetId("pet-1")
            vm.onStartDateChange(today())
            assertNotNull(vm.save())
        }

    @Test
    fun `preselection does not overwrite a manually chosen unit`() =
        runTest {
            val vm =
                vm(
                    FakeDrugReferenceReader(
                        mapOf("testsolid" to DrugReferenceEntry("TestSolid", "tablet", DoseUnit.TABLET)),
                    ),
                )
            vm.onNameChange("TestSolid")
            assertEquals(DoseUnit.TABLET, vm.state.value.doseUnit)

            vm.onDoseUnitChange(DoseUnit.MG)
            vm.onNameChange("TestSolid")

            assertEquals(DoseUnit.MG, vm.state.value.doseUnit)
        }

    @Test
    fun `narrowed set always contains the preselected unit`() =
        runTest {
            val vm =
                vm(
                    FakeDrugReferenceReader(
                        mapOf("testliquid" to DrugReferenceEntry("TestLiquid", "liquid", DoseUnit.PATCH)),
                    ),
                )

            vm.onNameChange("TestLiquid")

            val units = vm.state.value.suggestedUnits
            assertEquals(DoseUnit.PATCH, units.first())
            assertTrue(DoseUnit.PATCH in units)
        }
}
