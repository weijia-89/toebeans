package app.toebeans.android.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Placement contract for the Emergency card on the Today screen: it renders in BOTH
 * HomeScreenContent branches — the zero-pets empty state (the card must be reachable
 * before any pet exists; an emergency does not wait for onboarding) and the populated
 * LazyColumn — and it is collapsed until the user expands it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HomeScreenEmergencyRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `zero-pets today screen still renders the emergency card`() {
        rule.setContent {
            HomeScreenContent(
                state = HomeUiState(pets = emptyList()),
                onAddPet = {},
                onAddReminder = {},
                onPetFilterSelect = {},
                onClearPetFilter = {},
                onEditDose = { _, _, _ -> },
                onMarkGiven = { _, _, _ -> },
                contentPadding = PaddingValues(0.dp),
            )
        }
        rule.onNodeWithText("Emergency").assertIsDisplayed()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertDoesNotExist()
    }

    @Test
    fun `populated today screen renders the emergency card above the pets section`() {
        val pet =
            Pet(
                id = "pet-1",
                name = "Rufus",
                species = Species.DOG,
                birthdate = null,
                weightKg = 12.0,
                notes = null,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                archivedAt = null,
            )
        rule.setContent {
            HomeScreenContent(
                state = HomeUiState(pets = listOf(pet), medCountByPetId = mapOf("pet-1" to 0)),
                onAddPet = {},
                onAddReminder = {},
                onPetFilterSelect = {},
                onClearPetFilter = {},
                onEditDose = { _, _, _ -> },
                onMarkGiven = { _, _, _ -> },
                contentPadding = PaddingValues(0.dp),
            )
        }
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("Emergency").assertIsDisplayed()
        rule.onNodeWithText("Your pets").assertIsDisplayed()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertDoesNotExist()
    }

    @Test
    fun `emergency card expands from the today screen to show the hotlines`() {
        rule.setContent {
            HomeScreenContent(
                state = HomeUiState(pets = emptyList()),
                onAddPet = {},
                onAddReminder = {},
                onPetFilterSelect = {},
                onClearPetFilter = {},
                onEditDose = { _, _, _ -> },
                onMarkGiven = { _, _, _ -> },
                contentPadding = PaddingValues(0.dp),
            )
        }
        rule.onNodeWithContentDescription("Show emergency contacts").performClick()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertIsDisplayed()
        rule.onNodeWithText("Pet Poison Helpline").assertIsDisplayed()
    }
}
