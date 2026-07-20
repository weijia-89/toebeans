package app.toebeans.core.model

import kotlinx.serialization.Serializable

/**
 * Standard units for pet medication dosing. Separated from the numeric/text amount
 * so the UI can present a constrained picker and so display formatting is consistent
 * across surfaces (Today, Reminders, Notifications, Settings).
 *
 * The [label] is the user-facing lowercase form used in phrases like "5 mg" or
 * "2 tablets". It does NOT attempt pluralisation — the amount string (e.g. "1" vs "2")
 * already carries cardinality, and plural rules vary by locale.
 */
@Serializable
public enum class DoseUnit(public val label: String) {
    MG("mg"),
    ML("mL"),
    G("g"),
    TABLET("tablet"),
    CAPSULE("capsule"),
    DROP("drop"),
    TSP("tsp"),
    TBSP("tbsp"),
    UNIT("unit"),
    IU("IU"),
    CC("cc"),
    PUMP("pump"),
    SPRAY("spray"),
    PATCH("patch"),
    SACHET("sachet"),
    SCOOP("scoop"),
    PILL("pill"),
}

/**
 * Safely parse a [DoseUnit] from its enum name (e.g. "MG", "TABLET").
 * Returns [default] if [name] is null, blank, or not a known unit.
 *
 * Used by repository mappers so a corrupted or future-unrecognized DB value does not
 * crash the app at read time. A null default preserves legacy display behavior.
 */
public fun parseDoseUnitOrDefault(name: String?, default: DoseUnit? = null): DoseUnit? =
    if (name.isNullOrBlank()) {
        default
    } else {
        try {
            enumValueOf<DoseUnit>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
