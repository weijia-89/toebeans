package app.toebeans.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Init-block validation tests for the domain models. Adversarially-derived: every `require()`
 * has a test that triggers it.
 *
 * These are NOT vibe-dangerous code; they exist to ensure the domain rejects garbage at the
 * constructor boundary instead of letting it propagate to the UI or persistence layer.
 */
class ModelValidationTest {
    private val now = Instant.parse("2026-05-15T12:00:00Z")

    @Test
    fun `Pet rejects blank id`() {
        assertFailsWith<IllegalArgumentException> {
            Pet(
                id = "",
                name = "Mochi",
                species = Species.CAT,
                birthdate = null,
                weightKg = null,
                notes = null,
                createdAt = now,
                archivedAt = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Pet(
                id = "   ",
                name = "Mochi",
                species = Species.CAT,
                birthdate = null,
                weightKg = null,
                notes = null,
                createdAt = now,
                archivedAt = null,
            )
        }
    }

    @Test
    fun `Pet rejects blank name`() {
        assertFailsWith<IllegalArgumentException> {
            Pet(
                id = "p1",
                name = "",
                species = Species.CAT,
                birthdate = null,
                weightKg = null,
                notes = null,
                createdAt = now,
                archivedAt = null,
            )
        }
    }

    @Test
    fun `Pet rejects non-positive weight`() {
        assertFailsWith<IllegalArgumentException> {
            Pet(
                id = "p1",
                name = "Mochi",
                species = Species.CAT,
                birthdate = null,
                weightKg = 0.0,
                notes = null,
                createdAt = now,
                archivedAt = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Pet(
                id = "p1",
                name = "Mochi",
                species = Species.CAT,
                birthdate = null,
                weightKg = -2.5,
                notes = null,
                createdAt = now,
                archivedAt = null,
            )
        }
    }

    @Test
    fun `Pet accepts a null weight (it is optional)`() {
        Pet(
            id = "p1",
            name = "Mochi",
            species = Species.CAT,
            birthdate = null,
            weightKg = null,
            notes = null,
            createdAt = now,
            archivedAt = null,
        )
        // No assertion: just check construction does not throw.
    }

    @Test
    fun `Medication rejects blank fields`() {
        val valid =
            Medication(
                id = "m1",
                petId = "p1",
                name = "Pred",
                doseAmount = "10mg",
                notes = null,
                createdAt = now,
                discontinuedAt = null,
            )
        assertFailsWith<IllegalArgumentException> { valid.copy(id = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(petId = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(name = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(doseAmount = "") }
    }

    @Test
    fun `Schedule rejects blank ids`() {
        val valid =
            Schedule(
                id = "s1",
                medicationId = "m1",
                startDate = LocalDate(2026, 6, 1),
                endDate = null,
                createdAt = now,
            )
        assertFailsWith<IllegalArgumentException> { valid.copy(id = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(medicationId = "") }
    }

    @Test
    fun `Schedule rejects endDate before startDate`() {
        assertFailsWith<IllegalArgumentException> {
            Schedule(
                id = "s1",
                medicationId = "m1",
                startDate = LocalDate(2026, 6, 10),
                endDate = LocalDate(2026, 6, 5),
                createdAt = now,
            )
        }
    }

    @Test
    fun `Schedule accepts endDate equal to startDate (single-day schedule)`() {
        Schedule(
            id = "s1",
            medicationId = "m1",
            startDate = LocalDate(2026, 6, 1),
            endDate = LocalDate(2026, 6, 1),
            createdAt = now,
        )
    }

    @Test
    fun `SchedulePhase rejects doseTimesLocal-dosesPerDay mismatch`() {
        // SchedulePhase already had init validation before this commit; covering it for parity.
        assertFailsWith<IllegalArgumentException> {
            SchedulePhase(
                id = "ph1",
                scheduleId = "s1",
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0)), // only 1 time but 2 dosesPerDay
                doseAmount = null,
            )
        }
    }

    // The calculator's F5 global-ordering claim is asserted-by-construction (not by a sort
    // call) and depends on SchedulePhase.init enforcing strict-ascending doseTimesLocal. The
    // init invariant exists at SchedulePhase.kt:47 but had no direct test. Pin it here so a
    // future refactor that relaxes the init check cannot silently break F5.
    @Test
    fun `SchedulePhase rejects descending doseTimesLocal`() {
        assertFailsWith<IllegalArgumentException> {
            SchedulePhase(
                id = "ph1",
                scheduleId = "s1",
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(20, 0), LocalTime(8, 0)), // descending
                doseAmount = null,
            )
        }
    }

    @Test
    fun `SchedulePhase rejects equal doseTimesLocal, strict ascending not merely sorted`() {
        // Equal times would produce two doses at the same instant, a duplicate dose, which
        // is a medication-critical bug. The init check uses `distinct().size == size` to
        // catch this beyond what `sorted()` alone would allow.
        assertFailsWith<IllegalArgumentException> {
            SchedulePhase(
                id = "ph1",
                scheduleId = "s1",
                phaseOrder = 0,
                durationDays = 1,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(8, 0)), // equal
                doseAmount = null,
            )
        }
    }
}
