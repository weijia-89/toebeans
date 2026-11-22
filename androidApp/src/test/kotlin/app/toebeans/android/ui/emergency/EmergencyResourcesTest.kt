package app.toebeans.android.ui.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pure contract for the static emergency-contact data (the only "content" this feature
 * ships). Pins the exact verified numbers and fee disclosures so a typo in strings.xml
 * or in the dial digits fails here, not on a phone during an emergency.
 *
 * Numbers verified 2026-11-15 against the primary pages:
 *   - ASPCA: https://www.aspca.org/pet-care/animal-poison-control ("(888) 426-4435",
 *     "A consultation fee may apply", "24/7, 365 days a year")
 *   - Pet Poison Helpline: https://www.petpoisonhelpline.com/ ("(855) 764-7661",
 *     "$99 incident fee applies", "available 24/7")
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EmergencyResourcesTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `exactly two hotlines with the verified display strings`() {
        val hotlines = EmergencyResources.hotlines
        assertEquals(2, hotlines.size)
        val aspca = hotlines[0]
        assertEquals("ASPCA Animal Poison Control", context.getString(aspca.nameRes))
        assertEquals("(888) 426-4435", context.getString(aspca.phoneRes))
        val pph = hotlines[1]
        assertEquals("Pet Poison Helpline", context.getString(pph.nameRes))
        assertEquals("(855) 764-7661", context.getString(pph.phoneRes))
    }

    @Test
    fun `dial digits match the digits printed on screen for every hotline`() {
        EmergencyResources.hotlines.forEach { hotline ->
            val display = context.getString(hotline.phoneRes)
            assertEquals(display.filter(Char::isDigit), hotline.phoneDigits)
        }
    }

    @Test
    fun `both hotlines disclose 24-7 availability and a possible fee`() {
        EmergencyResources.hotlines.forEach { hotline ->
            val detail = context.getString(hotline.detailRes)
            assertTrue(detail.contains("24/7"))
            assertTrue(detail.contains("fee", ignoreCase = true))
        }
    }

    @Test
    fun `telUri is scheme tel with the exact digits and nothing else`() {
        val uri = EmergencyResources.telUri("8884264435")
        assertEquals("tel", uri.scheme)
        assertEquals("8884264435", uri.schemeSpecificPart)
    }

    @Test
    fun `dial intent is ACTION_DIAL carrying the tel data with no extras`() {
        val intent = emergencyDialIntent(EmergencyResources.telUri("8557647661"))
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals(Uri.parse("tel:8557647661"), intent.data)
        assertEquals(0, intent.extras?.size() ?: 0)
    }
}
