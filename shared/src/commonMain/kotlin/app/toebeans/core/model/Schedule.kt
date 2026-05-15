package app.toebeans.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A schedule program for a [Medication]. Composed of one or more [SchedulePhase]s.
 *
 * A non-tapering regimen ("10mg twice daily") is represented as a [Schedule] with one
 * [SchedulePhase]. A tapering regimen is a [Schedule] with N phases in [SchedulePhase.phaseOrder].
 *
 * @property startDate first day the schedule applies, in the device's local calendar.
 * @property endDate inclusive last day; `null` means "until the user stops it" (open-ended).
 */
@Serializable
public data class Schedule(
    val id: String,
    val medicationId: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val createdAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Schedule.id must not be blank" }
        require(medicationId.isNotBlank()) { "Schedule.medicationId must not be blank" }
        endDate?.let {
            require(it >= startDate) {
                "Schedule.endDate ($it) must be >= startDate ($startDate)"
            }
        }
    }
}
