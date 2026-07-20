package app.toebeans.core.model

/**
 * Format a dose amount and unit for display across the app.
 * When [unit] is null, returns just the amount (legacy fallback).
 *
 * Examples:
 *   - formatDose("5", DoseUnit.MG) → "5 mg"
 *   - formatDose("1", DoseUnit.TABLET) → "1 tablet"
 *   - formatDose("10mg", null) → "10mg" (legacy, pre-unit-picker data)
 */
public fun formatDose(
    amount: String,
    unit: DoseUnit?,
): String =
    if (unit != null) {
        "$amount ${unit.label}"
    } else {
        amount
    }
