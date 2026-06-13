package app.toebeans.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class PetDetailMedicationNavTest {
    @Test
    fun noSchedule_routesToScheduleCreate() {
        assertEquals(
            PetDetailMedicationDestination.SCHEDULE_CREATE,
            resolvePetDetailMedicationDestination(activeScheduleId = null),
        )
    }

    @Test
    fun hasSchedule_routesToMedicationEdit() {
        assertEquals(
            PetDetailMedicationDestination.MEDICATION_EDIT,
            resolvePetDetailMedicationDestination(activeScheduleId = "sched-1"),
        )
    }
}
