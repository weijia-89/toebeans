package app.toebeans.android.ui.medications

import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D5 option A link-out path. The app stores only name + label + URL: no advice text, no
 * paraphrase, no dose numbers. A tap opens the system browser via ACTION_VIEW, which is a
 * platform Intent and adds no network library or Gradle dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrugReferenceLinkOutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tapping a source label opens the catalog URL`() {
        var openedUrl: String? = null
        rule.setContent {
            MaterialTheme {
                DrugReferenceLinkOutCard(onOpenUrl = { openedUrl = it })
            }
        }

        rule
            .onNodeWithContentDescription("Open Merck Veterinary Manual reference for Methimazole")
            .performClick()

        assertEquals("https://www.merckvetmanual.com/", openedUrl)
    }

    @Test
    fun `link-out catalog carries no advice text or dose numbers`() {
        val doseUnits = "mg|mcg|kg|g|ml|cc|tsp|tbsp|tablet|tablets|capsule|iu|unit|units|drop|drops"
        val dosePattern = Regex("""\b\d+(?:\.\d+)?\s*(?:$doseUnits)\b""", RegexOption.IGNORE_CASE)
        assertTrue("a link-out catalog must not be empty", DrugReferenceCatalog.entries.isNotEmpty())
        DrugReferenceCatalog.entries.forEach { entry ->
            assertTrue(entry.medication.isNotBlank())
            assertTrue(entry.sourceLabel.isNotBlank())
            assertTrue(entry.url.startsWith("https://"))
            val blob = listOf(entry.medication, entry.sourceLabel, entry.url).joinToString(" ")
            assertFalse("link-out entry carries a dose number: $entry", dosePattern.containsMatchIn(blob))
        }
    }

    @Test
    fun `viewUrlIntent builds an ACTION_VIEW intent for the source URL`() {
        val url = "https://www.merckvetmanual.com/"
        val intent = viewUrlIntent(url)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
    }
}
