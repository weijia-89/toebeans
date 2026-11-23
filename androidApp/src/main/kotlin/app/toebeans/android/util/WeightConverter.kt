package app.toebeans.android.util

import app.toebeans.android.preferences.WeightUnit

/**
 * Pure unit conversion at the UI edge for D6. The app stores exactly one canonical
 * column, `weight_kg`; pounds are only ever a presentation of that value. No platform
 * types, no clock, no I/O — every function here is directly unit-testable.
 */
internal object WeightConverter {
    /** Exact international avoirdupois pound. One source of truth for both directions. */
    internal const val KG_PER_POUND: Double = 0.45359237

    internal fun kgToUnit(
        kg: Double,
        unit: WeightUnit,
    ): Double =
        when (unit) {
            WeightUnit.POUNDS -> kg / KG_PER_POUND
            WeightUnit.KILOGRAMS -> kg
        }

    internal fun unitToKg(
        value: Double,
        unit: WeightUnit,
    ): Double =
        when (unit) {
            WeightUnit.POUNDS -> value * KG_PER_POUND
            WeightUnit.KILOGRAMS -> value
        }

    /** One decimal place matches the existing kg display and keeps the field compact. */
    internal fun formatWeight(
        kg: Double,
        unit: WeightUnit,
    ): String = "%.1f %s".format(kgToUnit(kg, unit), unit.shortLabel)

    /** TalkBack does not reliably expand "lb" or "kg" — spell them out in merged labels. */
    internal fun expandAbbreviation(label: String): String =
        label
            .replace(" lb", " pounds")
            .replace(" kg", " kilograms")

    internal fun fieldLabel(unit: WeightUnit): String = "Weight (${unit.shortLabel}, optional)"
}
