package app.toebeans.android.ui.emergency

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rendered contract for the Emergency card (ADR-0013 pattern: Robolectric + Compose
 * ui-test, SDK 34, plain Application so Koin never boots). Pins what a user in a
 * panic can observe: collapsed by default, one tap to exactly the two verified
 * hotlines and nothing else. The first-line explanation, find-a-vet guidance, and
 * disclaimer stay in the Settings dialog via [EmergencyResourcesContent]; a separate
 * test pins that full content. Expected display strings are hard-coded here so drift
 * against strings.xml fails at this layer too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EmergencyCardRenderTest {
    @get:Rule
    val rule = createComposeRule()

    private val firstLine =
        "If your pet may have eaten something toxic or is in distress, " +
            "call your veterinarian or the nearest emergency animal hospital now."
    private val findVetGuidance =
        "Need an emergency vet? Search online for \"emergency veterinarian near me\" " +
            "or call your regular vet's after-hours line."
    private val disclaimer =
        "toebeans does not provide medical advice. " +
            "In an emergency, call a veterinary professional."

    @Test
    fun `card renders collapsed - title visible, hotline rows absent`() {
        rule.setContent { EmergencyCard(onCall = {}) }
        rule.onNodeWithText("Emergency").assertIsDisplayed()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertDoesNotExist()
        rule.onNodeWithText("Pet Poison Helpline").assertDoesNotExist()
    }

    @Test
    fun `expanding reveals only the two hotlines and no first-line, find-vet, or disclaimer text`() {
        rule.setContent { EmergencyCard(onCall = {}) }
        expandCard()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertIsDisplayed()
        rule.onNodeWithText("(888) 426-4435").assertIsDisplayed()
        rule.onNodeWithText("24/7 · consultation fee may apply").assertIsDisplayed()
        rule.onNodeWithText("Pet Poison Helpline").assertIsDisplayed()
        rule.onNodeWithText("(855) 764-7661").assertIsDisplayed()
        rule.onNodeWithText("24/7 · $99 per incident fee applies").assertIsDisplayed()
        rule.onNodeWithText(firstLine).assertDoesNotExist()
        rule.onNodeWithText(findVetGuidance).assertDoesNotExist()
        rule.onNodeWithText(disclaimer).assertDoesNotExist()
    }

    @Test
    fun `settings emergency content still renders first line, find-a-vet guidance, and disclaimer`() {
        rule.setContent { EmergencyResourcesContent(onCall = {}) }
        rule.onNodeWithText(firstLine).assertIsDisplayed()
        rule.onNodeWithText(findVetGuidance).assertIsDisplayed()
        rule.onNodeWithText(disclaimer).assertIsDisplayed()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertIsDisplayed()
        rule.onNodeWithText("Pet Poison Helpline").assertIsDisplayed()
    }

    @Test
    fun `collapsing again hides the hotline rows`() {
        rule.setContent { EmergencyCard(onCall = {}) }
        expandCard()
        rule
            .onNodeWithContentDescription("Hide emergency contacts")
            .performClick()
        rule.onNodeWithText("ASPCA Animal Poison Control").assertDoesNotExist()
        rule.onNodeWithText("Pet Poison Helpline").assertDoesNotExist()
    }

    @Test
    fun `tapping the ASPCA row fires the call callback with its exact tel uri`() {
        val calls = mutableListOf<Uri>()
        rule.setContent { EmergencyCard(onCall = { calls += it }) }
        expandCard()
        rule
            .onNodeWithContentDescription("Call ASPCA Animal Poison Control: (888) 426-4435")
            .performClick()
        assertEquals(listOf(Uri.parse("tel:8884264435")), calls)
    }

    @Test
    fun `tapping the Pet Poison Helpline row fires the call callback with its exact tel uri`() {
        val calls = mutableListOf<Uri>()
        rule.setContent { EmergencyCard(onCall = { calls += it }) }
        expandCard()
        rule
            .onNodeWithContentDescription("Call Pet Poison Helpline: (855) 764-7661")
            .performClick()
        assertEquals(listOf(Uri.parse("tel:8557647661")), calls)
    }

    private fun expandCard() {
        rule
            .onNodeWithContentDescription("Show emergency contacts")
            .performClick()
    }
}
