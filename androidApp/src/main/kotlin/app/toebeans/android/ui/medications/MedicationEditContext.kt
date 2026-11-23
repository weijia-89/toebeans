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

/** Editable-field form (24-hour) for the D2 single-phase dose-time field. */
internal fun formatLocalTimeInput(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/**
 * Parse the editable "HH:MM" field. Returns null on anything malformed or out of range;
 * [LocalTime] itself enforces hour 0..23 and minute 0..59.
 */
internal fun parseLocalTimeInput(value: String): LocalTime? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return try {
        LocalTime(hour, minute)
    } catch (_: IllegalArgumentException) {
        null
    }
}
