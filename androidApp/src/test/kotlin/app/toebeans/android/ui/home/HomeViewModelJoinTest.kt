package app.toebeans.android.ui.home

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HomeViewModel.Companion.joinToUiState] — the pure join of pets +
 * medications + recent doses into the Home screen's [HomeUiState].
 *
 * The function is `internal` and lives on the companion specifically so it's testable
 * without a `viewModelScope` or a coroutine test rig. Same-module visibility is enough.
 *
 * Test cases lock down:
 *  - The discontinued-medications-excluded-from-count rule (Home and Pets screens both
 *    rely on this; off-by-one here would silently mis-count a pet with two active +
 *    one discontinued med as 3).
 *  - The seed-data schedule-id → medication-id naming convention used in v0.1 to avoid
 *    pulling in a fourth flow. Phase 2 (SQLDelight join through schedules) will replace
 *    this; until then a regression here would silently drop every recently-logged dose.
 *  - Edge cases around empty inputs so the initial-load state doesn't render garbage.
 */
class HomeViewModelJoinTest {
    @Test
    fun `empty pets empty meds empty doses yields empty state`() {
        val state = HomeViewModel.joinToUiState(emptyList(), emptyList(), emptyList())
        assertTrue(state.pets.isEmpty())
        assertTrue(state.medCountByPetId.isEmpty())
        assertTrue(state.recentDoses.isEmpty())
        assertEquals("join always returns loading=false", false, state.loading)
    }

    @Test
    fun `pets present but no meds yields empty med counts`() {
        val state = HomeViewModel.joinToUiState(listOf(pet("p-1"), pet("p-2")), emptyList(), emptyList())
        assertEquals(2, state.pets.size)
        assertTrue("no meds → no counts (not zero entries)", state.medCountByPetId.isEmpty())
    }

    @Test
    fun `discontinued medications are excluded from the per-pet count`() {
        val meds =
            listOf(
                med(id = "m-1", petId = "p-1", discontinuedAt = null),
                med(id = "m-2", petId = "p-1", discontinuedAt = null),
                med(id = "m-3", petId = "p-1", discontinuedAt = T1),
            )
        val state = HomeViewModel.joinToUiState(listOf(pet("p-1")), meds, emptyList())
        assertEquals("two active, one discontinued → count 2", 2, state.medCountByPetId["p-1"])
    }

    @Test
    fun `med counts are grouped per pet across multiple pets`() {
        val meds =
            listOf(
                med(id = "m-1", petId = "p-1"),
                med(id = "m-2", petId = "p-2"),
                med(id = "m-3", petId = "p-2"),
                med(id = "m-4", petId = "p-3"),
            )
        val state =
            HomeViewModel.joinToUiState(
                listOf(pet("p-1"), pet("p-2"), pet("p-3")),
                meds,
                emptyList(),
            )
        assertEquals(1, state.medCountByPetId["p-1"])
        assertEquals(2, state.medCountByPetId["p-2"])
        assertEquals(1, state.medCountByPetId["p-3"])
    }

    @Test
    fun `dose with matching schedule-to-med-id is projected as a RecentDoseUi`() {
        // Seed-data convention: "sched-luna-methimazole" maps to "med-luna-methimazole"
        // by replacing the "sched-" prefix with "med-". The join exploits this until
        // SQLDelight provides a real schedules-table join.
        val pet = pet(id = "p-luna", name = "Luna", species = Species.CAT)
        val med = med(id = "med-luna-methimazole", petId = "p-luna", name = "Methimazole")
        val dose = givenDose(id = "d-1", scheduleId = "sched-luna-methimazole", at = T2)

        val state = HomeViewModel.joinToUiState(listOf(pet), listOf(med), listOf(dose))

        assertEquals(1, state.recentDoses.size)
        val row = state.recentDoses.single()
        assertEquals("d-1", row.id)
        assertEquals("Luna", row.petName)
        assertEquals("species is title-cased for display", "Cat", row.petSpecies)
        assertEquals("Methimazole", row.medicationName)
        assertEquals("givenAt prefers resolvedAt, falls back to scheduledAt", T2, row.givenAt)
    }

    @Test
    fun `dose whose schedule-id does not map to a known med is skipped`() {
        // Defensive: if the seed-data naming convention is violated (a real SQLDelight
        // join would always succeed here, but during v0.1 a hand-crafted dose row could
        // miss), we drop the row rather than render half-broken UI. The other doses on
        // the same flow tick still come through.
        val pet = pet(id = "p-1")
        val med = med(id = "med-x", petId = "p-1")
        val goodDose = givenDose(id = "d-good", scheduleId = "sched-x")
        val orphanDose = givenDose(id = "d-orphan", scheduleId = "sched-nonexistent")

        val state = HomeViewModel.joinToUiState(listOf(pet), listOf(med), listOf(goodDose, orphanDose))

        assertEquals("orphan dose dropped, good dose kept", 1, state.recentDoses.size)
        assertEquals("d-good", state.recentDoses.single().id)
        assertNull(
            "orphan dose must not appear in the projected list",
            state.recentDoses.firstOrNull { it.id == "d-orphan" },
        )
    }

    // ---------- builders ----------

    private fun pet(
        id: String,
        name: String = "Pet-$id",
        species: Species = Species.DOG,
    ): Pet =
        Pet(
            id = id,
            name = name,
            species = species,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 10.0,
            notes = null,
            createdAt = T0,
            archivedAt = null,
        )

    private fun med(
        id: String,
        petId: String,
        name: String = "Med-$id",
        discontinuedAt: Instant? = null,
    ): Medication =
        Medication(
            id = id,
            petId = petId,
            name = name,
            doseAmount = "1 tablet",
            notes = null,
            createdAt = T0,
            discontinuedAt = discontinuedAt,
        )

    private fun givenDose(
        id: String,
        scheduleId: String,
        at: Instant = T1,
    ): DoseEvent =
        DoseEvent(
            id = id,
            scheduleId = scheduleId,
            scheduledAt = at,
            firedAt = at,
            resolvedAt = at,
            status = DoseStatus.GIVEN,
            note = null,
        )

    companion object {
        private val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
        private val T1: Instant = Instant.parse("2026-05-16T10:00:00Z")
        private val T2: Instant = Instant.parse("2026-05-16T18:30:00Z")
    }
}
