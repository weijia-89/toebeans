package app.toebeans.core.model

import kotlinx.datetime.TimeZone

/**
 * Validity and quarantine rule for [Pet.homeTimezone].
 *
 * A non-null id that is not in [TimeZone.availableZoneIds] is *quarantined*: the value is
 * kept verbatim, no device-zone fallback is applied, and callers pause new dose times
 * until the owner repairs it. Because quarantine is derived from the persisted value it
 * survives process restarts without another column or migration.
 */
public object HomeTimezone {
    /** True when [zoneId] is an id kotlinx-datetime can resolve (IANA or fixed offset). */
    public fun isValid(zoneId: String): Boolean = zoneId in TimeZone.availableZoneIds

    /** True when the pet carries a stored id that cannot be resolved. */
    public fun isQuarantined(pet: Pet): Boolean {
        val id = pet.homeTimezone ?: return false
        return !isValid(id)
    }
}
