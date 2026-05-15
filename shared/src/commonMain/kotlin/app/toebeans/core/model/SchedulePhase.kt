package app.toebeans.core.model

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * One phase of a [Schedule]. Phases are ordered by [phaseOrder] and concatenated in time:
 * phase 0 runs for [durationDays] starting at the schedule's `startDate`; phase 1 starts the day
 * after phase 0 ends; and so on.
 *
 * @property doseTimesLocal local wall-clock times at which a dose is due. Length MUST equal
 *           [dosesPerDay]. Must be ascending within the day. Stored as `HH:MM` strings on disk;
 *           parsed to [LocalTime] in memory.
 * @property doseAmount optional override of the parent [Medication]'s dose amount for this phase.
 *           Used for tapers: e.g. phase 0 doseAmount = "10mg", phase 1 doseAmount = "5mg".
 */
@Serializable
public data class SchedulePhase(
    val id: String,
    val scheduleId: String,
    val phaseOrder: Int,
    val durationDays: Int,
    val dosesPerDay: Int,
    val doseTimesLocal: List<LocalTime>,
    val doseAmount: String?,
) {
    init {
        require(durationDays > 0) { "durationDays must be > 0 (was $durationDays)" }
        require(dosesPerDay in 1..MAX_DOSES_PER_DAY) {
            "dosesPerDay must be in 1..$MAX_DOSES_PER_DAY (was $dosesPerDay)"
        }
        require(doseTimesLocal.size == dosesPerDay) {
            "doseTimesLocal.size (${doseTimesLocal.size}) must equal dosesPerDay ($dosesPerDay)"
        }
        require(doseTimesLocal == doseTimesLocal.sorted()) {
            "doseTimesLocal must be ascending within the day; got $doseTimesLocal"
        }
        require(phaseOrder >= 0) { "phaseOrder must be >= 0 (was $phaseOrder)" }
    }

    public companion object {
        /**
         * Six is the practical ceiling for owner-administered pet medications. Higher frequencies
         * are clinical-care territory (post-op, ICU) and out of scope for toebeans v1.
         */
        public const val MAX_DOSES_PER_DAY: Int = 6
    }
}
