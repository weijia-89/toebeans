package app.toebeans.android.ui.home

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun `dose with a medicationId pointing at a known medication is projected as a RecentDoseUi`() {
        // The medicationId is denormalized onto DoseEvent (see model KDoc) so the
        // lookup is direct rather than going through the schedule. This test covers
        // the structural fix that replaced a `replaceFirst("sched-", "med-")` hack
        // that only worked for the seeded demo IDs.
        val pet = pet(id = "p-luna", name = "Luna", species = Species.CAT)
        // Schedule and medication IDs intentionally unrelated — a real user creating a
        // new medication and schedule today gets UUIDs that share no prefix. The hack
        // would silently drop this row; this implementation must keep it.
        val med = med(id = "med-abc123", petId = "p-luna", name = "Methimazole")
        val dose = givenDose(id = "d-1", scheduleId = "sched-xyz789", medicationId = "med-abc123", at = T2)

        val state = HomeViewModel.joinToUiState(listOf(pet), listOf(med), listOf(dose))

        assertEquals(1, state.recentDoses.size)
        val row = state.recentDoses.single()
        assertEquals("d-1", row.id)
        assertEquals("Luna", row.petName)
        assertEquals("species is title-cased for display", "Cat", row.petSpecies)
        assertEquals("Methimazole", row.medicationName)
        assertEquals("med-abc123", row.medicationId)
        assertEquals("givenAt prefers resolvedAt, falls back to scheduledAt", T2, row.givenAt)
    }

    @Test
    fun `dose whose medicationId does not match any known medication triggers StaleEventGuard in debug`() {
        // Contract change (Tier A #4, crash-on-render-of-stale-event safety net): a dose
        // referencing a missing medication is treated as a bug surface. In debug builds
        // (which is what unit tests always run under) StaleEventGuard throws to surface
        // join-bug regressions in CI. In release builds it would log + skip; that path is
        // tested at the guard level in StaleEventGuardTest.
        //
        // Previously this test asserted silent-skip behavior. That silent filter is what
        // hid the replaceFirst("sched-","med-") bug for the entire scaffold milestone;
        // the regression test below this one is the long-form proof. We pin the new
        // contract here.
        val pet = pet(id = "p-1")
        val med = med(id = "med-x", petId = "p-1")
        val orphanDose = givenDose(id = "d-orphan", scheduleId = "sched-y", medicationId = "med-deleted")

        val ex =
            assertThrows(IllegalStateException::class.java) {
                HomeViewModel.joinToUiState(listOf(pet), listOf(med), listOf(orphanDose))
            }
        assertTrue(
            "the exception must name the stale row + missing field for actionable debugging",
            ex.message?.contains("d-orphan") == true && ex.message?.contains("med-deleted") == true,
        )
    }

    @Test
    fun `regression - schedule and medication with unrelated UUIDs still join correctly`() {
        // This is the regression test for the bug the cold review surfaced: user-created
        // schedules are `sched-<UUID>` and medications are `med-<DIFFERENT-UUID>`, so the
        // old `replaceFirst("sched-", "med-")` produced `med-<schedule-UUID>` which never
        // matched any real medication. With the medicationId column on DoseEvent, the
        // lookup is structural and works regardless of ID format.
        val pet = pet(id = "p-1", name = "Rufus")
        val med = med(id = "med-${"a".repeat(32)}", petId = "p-1", name = "Apoquel")
        val dose =
            givenDose(
                id = "d-1",
                scheduleId = "sched-${"b".repeat(32)}", // unrelated UUID
                medicationId = med.id,
                at = T2,
            )

        val state = HomeViewModel.joinToUiState(listOf(pet), listOf(med), listOf(dose))

        assertEquals(
            "user-created med + schedule with unrelated UUIDs must still produce a row",
            1,
            state.recentDoses.size,
        )
        assertEquals("Apoquel", state.recentDoses.single().medicationName)
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
        medicationId: String,
        at: Instant = T1,
    ): DoseEvent =
        DoseEvent(
            id = id,
            scheduleId = scheduleId,
            medicationId = medicationId,
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
