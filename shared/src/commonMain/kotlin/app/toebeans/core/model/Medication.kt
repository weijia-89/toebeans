package app.toebeans.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A medication prescribed to one [Pet]. Decoupled from [Schedule]: a single medication may have
 * multiple historical schedules (e.g. a steroid course, then a maintenance dose later).
 *
 * @property doseAmount free-text default amount (e.g. "10mg"). At v1 we do not parse this — we
 *           render it next to the dose reminder. The amount may be overridden per
 *           [SchedulePhase.doseAmount] for tapers.
 */
@Serializable
public data class Medication(
    val id: String,
    val petId: String,
    val name: String,
    val doseAmount: String,
    val notes: String?,
    val createdAt: Instant,
    val discontinuedAt: Instant?,
) {
    init {
        require(id.isNotBlank()) { "Medication.id must not be blank" }
        require(petId.isNotBlank()) { "Medication.petId must not be blank" }
        require(name.isNotBlank()) { "Medication.name must not be blank" }
        require(doseAmount.isNotBlank()) { "Medication.doseAmount must not be blank" }
    }

    public val isActive: Boolean get() = discontinuedAt == null
}
