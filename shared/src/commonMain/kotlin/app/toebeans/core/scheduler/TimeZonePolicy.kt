package app.toebeans.core.scheduler

import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.HomeTimezone
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import kotlinx.datetime.TimeZone

/**
 * Pure policy that maps a persisted [Pet] plus [Schedule.anchorMode] to the timezone the
 * caller passes into [ScheduleCalculator.computeScheduledDoses]. The calculator stays pure
 * and keeps its explicit `timeZone` argument (ADR-0007).
 *
 * Quarantine wins before anchor mode is considered: a pet with an invalid stored zone id
 * never silently falls back to the device zone on a medication-critical path.
 */
public object TimeZonePolicy {
    public sealed interface Resolution {
        public data class Use(
            val timeZone: TimeZone,
        ) : Resolution

        /** The pet has an invalid stored id. Block materialization and show the repair banner. */
        public data object Quarantined : Resolution
    }

    public fun resolve(
        schedule: Schedule,
        pet: Pet,
        deviceTimeZone: TimeZone,
    ): Resolution {
        val home = pet.homeTimezone
        if (home != null && !HomeTimezone.isValid(home)) return Resolution.Quarantined
        return when (schedule.anchorMode) {
            AnchorMode.FOLLOW_PHONE -> Resolution.Use(deviceTimeZone)
            AnchorMode.ELAPSED_INTERVAL -> Resolution.Use(TimeZone.UTC)
            AnchorMode.STAY_HOME_TZ ->
                if (home == null) Resolution.Use(deviceTimeZone) else Resolution.Use(TimeZone.of(home))
        }
    }
}
