package app.toebeans.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.yearsUntil

/**
 * Renders a [LocalDate] birthdate as a human age string relative to [today].
 *
 * Rationale for granularity buckets:
 *  - ≥ 2 years: "%d years old" (years are accurate enough at this scale)
 *  - ≥ 1 year, < 2 years: "1 year old" (no fractional months; the "1 year"
 *    milestone is emotionally meaningful enough that "1 year and 3 months"
 *    feels over-precise on a quick-glance card)
 *  - ≥ 2 months, < 1 year: "%d months old"
 *  - ≥ 2 weeks, < 2 months: "%d weeks old"
 *  - ≥ 1 week, < 2 weeks: "1 week old"
 *  - < 1 week: "%d days old"
 *  - 0 days: "born today"
 *  - future date (data entry error): "future birthdate" — defensive, we don't
 *    want to render "−3 days old" which would confuse a stressed user.
 *
 * All computation is done via kotlinx.datetime period helpers, which are
 * timezone-agnostic on [LocalDate] (no DST or zone-shift surprises). Caller
 * passes [today] explicitly so the function is pure and unit-testable. UI
 * code should pass `Clock.System.todayIn(TimeZone.currentSystemDefault())`.
 *
 * Locale: this v1 formatter is English-only. Localization (Plurals.xml on
 * Android, .stringsdict on iOS) lands in the i18n milestone. Numbers are
 * always rendered with Western digits regardless of locale, which is the
 * least-bad default for a numeric-quantity context.
 */
public object PetAgeFormatter {
    public fun format(
        birthdate: LocalDate,
        today: LocalDate,
    ): String {
        val days = birthdate.daysUntil(today)
        if (days < 0) return "future birthdate"
        if (days == 0) return "born today"
        if (days < 7) return pluralize(days, "day")

        val weeks = days / 7
        if (weeks < 2) return "1 week old"

        val months = birthdate.monthsUntil(today)
        if (months < 2) return pluralize(weeks, "week")
        if (months < 12) return pluralize(months, "month")

        val years = birthdate.yearsUntil(today)
        if (years < 2) return "1 year old"
        return pluralize(years, "year")
    }

    private fun pluralize(
        n: Int,
        unit: String,
    ): String = if (n == 1) "1 $unit old" else "$n ${unit}s old"
}

