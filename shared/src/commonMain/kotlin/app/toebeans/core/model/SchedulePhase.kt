package app.toebeans.core.model

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * One phase of a [Schedule]. Phases are ordered by [phaseOrder] and concatenated in time:
 * phase 0 runs for [durationDays] starting at the schedule's `startDate`; phase 1 starts the day
 * after phase 0 ends; and so on.
 *
 * Skip-day dosing is expressed via [dayInterval]: `dayInterval=1` means every day (default),
 * `dayInterval=2` means every other day, `dayInterval=7` means once a week. Within an active day,
 * [dosesPerDay] doses fire at the times in [doseTimesLocal].
 *
 * @property doseTimesLocal local wall-clock times at which a dose is due. Length MUST equal
 *           [dosesPerDay]. Must be ascending within the day. Strictly ascending — equal times
 *           are not allowed (would produce duplicate doses). Stored as `HH:MM` strings on disk;
 *           parsed to [LocalTime] in memory.
 * @property doseAmount optional override of the parent [Medication]'s dose amount for this phase.
 *           Used for tapers: e.g. phase 0 doseAmount = "10mg", phase 1 doseAmount = "5mg".
 * @property dayInterval calendar-day interval between dosing days. Defaults to 1 (daily).
 *           Range: 1..[MAX_DAY_INTERVAL]. Monthly dosing is the realistic ceiling
 *           (e.g., bravecto). Phase day 0 is always a dosing day; phase day 1 is a dosing
 *           day iff `dayInterval==1`; phase day N is a dosing day iff `N % dayInterval == 0`.
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
    val dayInterval: Int = 1,
) {
    init {
        require(durationDays in 1..MAX_DURATION_DAYS) {
            "durationDays must be in 1..$MAX_DURATION_DAYS (was $durationDays)"
        }
        require(dosesPerDay in 1..MAX_DOSES_PER_DAY) {
            "dosesPerDay must be in 1..$MAX_DOSES_PER_DAY (was $dosesPerDay)"
        }
        require(doseTimesLocal.size == dosesPerDay) {
            "doseTimesLocal.size (${doseTimesLocal.size}) must equal dosesPerDay ($dosesPerDay)"
        }
        require(doseTimesLocal == doseTimesLocal.sorted() && doseTimesLocal.distinct().size == doseTimesLocal.size) {
            "doseTimesLocal must be strictly ascending within the day; got $doseTimesLocal"
        }
        require(phaseOrder >= 0) { "phaseOrder must be >= 0 (was $phaseOrder)" }
        require(dayInterval in 1..MAX_DAY_INTERVAL) {
            "dayInterval must be in 1..$MAX_DAY_INTERVAL (was $dayInterval)"
        }
    }

    public companion object {
        /**
         * Six is the practical ceiling for owner-administered pet medications. Higher frequencies
         * are clinical-care territory (post-op, ICU) and out of scope for toebeans v1.
         */
        public const val MAX_DOSES_PER_DAY: Int = 6

        /**
         * 10 years. Per ADR-0008: covers any realistic chronic taper, including lifelong
         * methimazole for a young hyperthyroid cat. Beyond this, the schedule should be modeled
         * as multiple phases or recreated. Keeps materialization math bounded.
         */
        public const val MAX_DURATION_DAYS: Int = 3650

        /**
         * 30 days. Per ADR-0008: monthly dosing (e.g., bravecto flea/tick in dogs) is the
         * realistic ceiling for owner-administered "skip days" patterns. Quarterly or yearly
         * dosing belongs in a different feature (vaccination/visit reminders, milestone 4).
         */
        public const val MAX_DAY_INTERVAL: Int = 30
    }
}
