package app.toebeans.android.ui.home

import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [HomeViewModel.Companion.applyPetFilter]. */
class HomeViewModelApplyPetFilterTest {
    private val now = Instant.parse("2026-05-16T08:00:00Z")

    @Test
    fun `null petId leaves lists unchanged and clears filter field`() {
        val base = sampleState()
        val result = HomeViewModel.applyPetFilter(base, null)
        assertNull(result.filterPetId)
        assertEquals(base.dueDoses, result.dueDoses)
        assertEquals(base.recentDoses, result.recentDoses)
    }

    @Test
    fun `unknown petId clears filter and leaves lists unchanged`() {
        val base = sampleState()
        val result = HomeViewModel.applyPetFilter(base, "deleted-pet")
        assertNull(result.filterPetId)
        assertEquals(base.dueDoses, result.dueDoses)
        assertEquals(base.recentDoses, result.recentDoses)
    }

    @Test
    fun `petId narrows due and logged lists to that pet`() {
        val base = sampleState()
        val result = HomeViewModel.applyPetFilter(base, "p-2")
        assertEquals("p-2", result.filterPetId)
        assertEquals(1, result.dueDoses.size)
        assertEquals("p-2", result.dueDoses.single().petId)
        assertEquals(1, result.recentDoses.size)
        assertEquals("p-2", result.recentDoses.single().petId)
    }

    private fun sampleState(): HomeUiState =
        HomeUiState(
            pets = listOf(samplePet("p-1"), samplePet("p-2")),
            dueDoses =
                listOf(
                    due("p-1", "s-1"),
                    due("p-2", "s-2"),
                ),
            recentDoses =
                listOf(
                    logged("p-1", "d-1"),
                    logged("p-2", "d-2"),
                ),
        )

    private fun samplePet(id: String): Pet =
        Pet(
            id = id,
            name = id,
            species = Species.CAT,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 4.0,
            notes = null,
            createdAt = now,
            archivedAt = null,
        )

    private fun due(
        petId: String,
        scheduleId: String,
    ): DueDoseUi =
        DueDoseUi(
            scheduleId = scheduleId,
            medicationId = "m-$petId",
            petId = petId,
            scheduledAt = Instant.parse("2026-05-16T12:00:00Z"),
            petName = petId,
            medicationName = "Med",
            doseAmount = "1 pill",
            givenEventId = null,
            resolvedAt = null,
        )

    private fun logged(
        petId: String,
        id: String,
    ): RecentDoseUi =
        RecentDoseUi(
            id = id,
            petId = petId,
            petName = petId,
            petSpecies = "Cat",
            medicationName = "Med",
            givenAt = Instant.parse("2026-05-16T10:00:00Z"),
        )
}
