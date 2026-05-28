package app.toebeans.android.ui.medications

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationEditContextTest {
    @Test
    fun `empty schedules yields no-schedule copy`() {
        val hint = buildMedicationEditScheduleHint(emptyList(), emptyList())
        assertTrue(hint.contains("No reminder schedule"))
    }

    @Test
    fun `schedule with phase dose times appends time hint`() {
        val schedule =
            Schedule(
                id = "sch-1",
                medicationId = "med-1",
                startDate = LocalDate(2026, 3, 1),
                endDate = null,
                createdAt = Clock.System.now(),
            )
        val phase =
            SchedulePhase(
                id = "ph-1",
                scheduleId = "sch-1",
                phaseOrder = 0,
                durationDays = 7,
                dosesPerDay = 1,
                doseTimesLocal = listOf(LocalTime(8, 0)),
                doseAmount = null,
            )
        val hint = buildMedicationEditScheduleHint(listOf(schedule), listOf(phase))
        assertEquals(
            "Reminder schedule: 2026-03-01 → ongoing · 8:00 AM",
            hint,
        )
    }
}
