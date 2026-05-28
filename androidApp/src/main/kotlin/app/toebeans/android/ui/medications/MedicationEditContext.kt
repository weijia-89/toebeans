package app.toebeans.android.ui.medications

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.LocalTime

/**
 * Read-only schedule summary for [MedicationEditScreen]'s context card.
 * Pure function so schedule/phase join logic is unit-testable without a ViewModel harness.
 */
internal fun buildMedicationEditScheduleHint(
    schedules: List<Schedule>,
    phasesForEarliestSchedule: List<SchedulePhase>,
): String {
    if (schedules.isEmpty()) {
        return "No reminder schedule yet — add one after saving from the pet's medications."
    }
    val schedule = schedules.minBy { it.startDate }
    val endLabel = schedule.endDate?.toString() ?: "ongoing"
    val range = "Reminder schedule: ${schedule.startDate} → $endLabel"
    val firstPhase = phasesForEarliestSchedule.minByOrNull { it.phaseOrder } ?: return range
    val times =
        firstPhase.doseTimesLocal
            .take(3)
            .joinToString(", ") { formatLocalTimeHint(it) }
    return if (times.isNotEmpty()) "$range · $times" else range
}

internal fun formatLocalTimeHint(time: LocalTime): String {
    val hour24 = time.hour
    val hour12 = ((hour24 + 11) % 12) + 1
    val minute = time.minute.toString().padStart(2, '0')
    val suffix = if (hour24 < 12) "AM" else "PM"
    return "$hour12:$minute $suffix"
}
