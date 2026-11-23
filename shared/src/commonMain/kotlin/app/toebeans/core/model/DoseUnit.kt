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
public enum class DoseUnit(
    public val label: String,
) {
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
public fun parseDoseUnitOrDefault(
    name: String?,
    default: DoseUnit? = null,
): DoseUnit? =
    if (name.isNullOrBlank()) {
        default
    } else {
        try {
            enumValueOf<DoseUnit>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

/**
 * Plausible measuring units for a recorded formulation, ordered pills first for
 * solid oral forms. This is picker guidance only: every list is a narrowing of the
 * same 17 [DoseUnit] values, never a restriction, and the UI always keeps an override
 * back to the full list. An unknown formulation returns the full list unchanged.
 *
 * No amount is prefilled and no dose is calculated here.
 */
public object DoseUnitReference {
    private val solidOral =
        setOf("tablet", "tablets", "capsule", "capsules", "pill", "pills", "solid", "chewable")
    private val liquidOral =
        setOf("liquid", "solution", "suspension", "syrup", "drops", "oral solution", "oral suspension")
    private val topical =
        setOf("topical", "patch", "cream", "ointment", "spray", "transdermal")

    public fun unitsForFormulation(formulation: String): List<DoseUnit> {
        val key =
            formulation
                .trim()
                .lowercase()
                .replace('_', ' ')
                .replace('-', ' ')
        return when (key) {
            in solidOral -> listOf(DoseUnit.TABLET, DoseUnit.CAPSULE, DoseUnit.PILL, DoseUnit.MG, DoseUnit.G)
            in liquidOral ->
                listOf(DoseUnit.ML, DoseUnit.CC, DoseUnit.TSP, DoseUnit.TBSP, DoseUnit.DROP, DoseUnit.MG)
            in topical ->
                listOf(DoseUnit.PATCH, DoseUnit.PUMP, DoseUnit.SPRAY, DoseUnit.G, DoseUnit.MG, DoseUnit.ML)
            else -> DoseUnit.entries.toList()
        }
    }
}
