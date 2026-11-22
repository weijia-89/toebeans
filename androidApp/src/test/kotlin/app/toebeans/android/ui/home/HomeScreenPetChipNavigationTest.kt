package app.toebeans.android.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One-handed Today contract: with one pet the chip opens that pet's page. With several
 * pets the chip keeps filtering the dose list, and a trailing control opens the pet page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HomeScreenPetChipNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `single pet chip opens pet detail instead of filtering`() {
        var navigatedPetId: String? = null
        var filteredPetId: String? = null
        rule.setContent {
            HomeScreenContent(
                state = HomeUiState(pets = listOf(pet("p1", "Luna"))),
                onAddPet = {},
                onAddReminder = {},
                onPetFilterSelect = { filteredPetId = it },
                onClearPetFilter = {},
                onPetClick = { navigatedPetId = it },
                onEditDose = { _, _, _ -> },
                onMarkGiven = { _, _, _ -> },
                contentPadding = PaddingValues(0.dp),
            )
        }

        rule.onNodeWithContentDescription("Luna, cat").performClick()

        assertEquals("p1", navigatedPetId)
        assertNull(filteredPetId)
    }

    @Test
    fun `multiple pets keep chip filter and trailing control navigates`() {
        var navigatedPetId: String? = null
        var filteredPetId: String? = null
        rule.setContent {
            HomeScreenContent(
                state =
                    HomeUiState(
                        pets = listOf(pet("p1", "Luna"), pet("p2", "Rufus")),
                        medCountByPetId = mapOf("p1" to 1, "p2" to 0),
                    ),
                onAddPet = {},
                onAddReminder = {},
                onPetFilterSelect = { filteredPetId = it },
                onClearPetFilter = {},
                onPetClick = { navigatedPetId = it },
                onEditDose = { _, _, _ -> },
                onMarkGiven = { _, _, _ -> },
                contentPadding = PaddingValues(0.dp),
            )
        }

        rule.onNodeWithContentDescription("Luna, cat, 1 med").performClick()
        assertEquals("p1", filteredPetId)
        assertNull(navigatedPetId)

        rule.onNodeWithContentDescription("Open Luna").performClick()
        assertEquals("p1", navigatedPetId)
    }

    private fun pet(
        id: String,
        name: String,
    ) = Pet(
        id = id,
        name = name,
        species = Species.CAT,
        birthdate = null,
        weightKg = null,
        notes = null,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        archivedAt = null,
    )
}
