package app.toebeans.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary-driven tests for [PetAgeFormatter]. Each bucket in the format() logic gets at
 * least one test at its lower edge and one inside the bucket. The boundaries themselves
 * are the most interesting cases — humans tend to round dramatically at "1 year" and
 * "1 month" so getting those exact is what makes the card not lie.
 *
 * `today` is fixed to 2026-05-15 so the test reads like a real shelter-intake form. All
 * birthdates are computed by subtracting from that anchor.
 */
class PetAgeFormatterTest {
    private val today = LocalDate(2026, 5, 15)

    @Test
    fun `born today renders as born today`() {
        assertEquals("born today", PetAgeFormatter.format(today, today))
    }

    @Test
    fun `one day old`() {
        assertEquals("1 day old", PetAgeFormatter.format(LocalDate(2026, 5, 14), today))
    }

    @Test
    fun `six days old still in days bucket`() {
        assertEquals("6 days old", PetAgeFormatter.format(LocalDate(2026, 5, 9), today))
    }

    @Test
    fun `exactly 7 days flips to 1 week`() {
        assertEquals("1 week old", PetAgeFormatter.format(LocalDate(2026, 5, 8), today))
    }

    @Test
    fun `13 days is still 1 week old`() {
        // Inside the singular-week bucket: 1 ≤ weeks < 2.
        assertEquals("1 week old", PetAgeFormatter.format(LocalDate(2026, 5, 2), today))
    }

    @Test
    fun `2 weeks renders plural`() {
        assertEquals("2 weeks old", PetAgeFormatter.format(LocalDate(2026, 5, 1), today))
    }

    @Test
    fun `7 weeks still in weeks bucket`() {
        // 7 weeks = 49 days; still under 2 months.
        assertEquals("7 weeks old", PetAgeFormatter.format(LocalDate(2026, 3, 27), today))
    }

    @Test
    fun `exactly 2 months flips to months`() {
        assertEquals("2 months old", PetAgeFormatter.format(LocalDate(2026, 3, 15), today))
    }

    @Test
    fun `11 months still in months bucket`() {
        assertEquals("11 months old", PetAgeFormatter.format(LocalDate(2025, 6, 15), today))
    }

    @Test
    fun `exactly 1 year renders singular`() {
        assertEquals("1 year old", PetAgeFormatter.format(LocalDate(2025, 5, 15), today))
    }

    @Test
    fun `1 year and 6 months still renders as 1 year old`() {
        // The rationale comment in PetAgeFormatter explains why we don't say "1 year
        // and 6 months" on quick-glance cards.
        assertEquals("1 year old", PetAgeFormatter.format(LocalDate(2024, 11, 15), today))
    }

    @Test
    fun `exactly 2 years renders plural`() {
        assertEquals("2 years old", PetAgeFormatter.format(LocalDate(2024, 5, 15), today))
    }

    @Test
    fun `senior dog 14 years old`() {
        assertEquals("14 years old", PetAgeFormatter.format(LocalDate(2012, 5, 15), today))
    }

    @Test
    fun `future birthdate renders defensively`() {
        // Data entry error: a user typo'd a future date. We refuse to render a negative
        // age rather than confuse them with "−3 days old".
        assertEquals("future birthdate", PetAgeFormatter.format(LocalDate(2026, 5, 16), today))
    }

    @Test
    fun `birthday tomorrow not yet 1 year old`() {
        // Adopted-and-aging-in: born 2025-05-16, today 2026-05-15 → not yet a year.
        // Should render as "11 months old", not "1 year old". The yearsUntil contract
        // requires the full year span to have elapsed.
        assertEquals("11 months old", PetAgeFormatter.format(LocalDate(2025, 5, 16), today))
    }
}
