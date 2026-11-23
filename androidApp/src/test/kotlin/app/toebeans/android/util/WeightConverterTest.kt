package app.toebeans.android.util

import app.toebeans.android.preferences.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit-conversion math for D6. The app stores one canonical column (`weight_kg`);
 * conversion happens only at the UI edge. These cases pin the edge math so a display
 * change can never leak into storage.
 */
class WeightConverterTest {
    @Test
    fun `pounds to kilograms uses the exact international pound`() {
        assertEquals(
            10.0,
            WeightConverter.unitToKg(22.0462262185, WeightUnit.POUNDS),
            1e-6,
        )
    }

    @Test
    fun `kilograms to pounds round trips through the chosen unit`() {
        val pounds = WeightConverter.kgToUnit(12.4, WeightUnit.POUNDS)
        assertEquals(12.4, WeightConverter.unitToKg(pounds, WeightUnit.POUNDS), 1e-9)
    }

    @Test
    fun `kilograms are unchanged when the unit is kilograms`() {
        assertEquals(12.4, WeightConverter.kgToUnit(12.4, WeightUnit.KILOGRAMS), 0.0)
        assertEquals(12.4, WeightConverter.unitToKg(12.4, WeightUnit.KILOGRAMS), 0.0)
    }

    @Test
    fun `formatWeight renders one decimal in pounds by default`() {
        assertEquals("27.3 lb", WeightConverter.formatWeight(12.4, WeightUnit.POUNDS))
    }

    @Test
    fun `formatWeight renders one decimal in kilograms when chosen`() {
        assertEquals("12.4 kg", WeightConverter.formatWeight(12.4, WeightUnit.KILOGRAMS))
    }

    @Test
    fun `expandAbbreviation expands both units for TalkBack`() {
        assertEquals("9.0 pounds", WeightConverter.expandAbbreviation("9.0 lb"))
        assertEquals("4.1 kilograms", WeightConverter.expandAbbreviation("4.1 kg"))
    }

    @Test
    fun `fieldLabel reflects the configured unit`() {
        assertEquals("Weight (lb, optional)", WeightConverter.fieldLabel(WeightUnit.POUNDS))
        assertEquals("Weight (kg, optional)", WeightConverter.fieldLabel(WeightUnit.KILOGRAMS))
    }
}
