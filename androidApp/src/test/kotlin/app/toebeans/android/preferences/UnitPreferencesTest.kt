package app.toebeans.android.preferences

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * D6 app-wide unit preference. Same SharedPreferences pattern as [ThemePreferences];
 * no Gradle dependency. Pounds is the fail-safe default for US owners.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnitPreferencesTest {
    private lateinit var context: Context
    private lateinit var prefs: UnitPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("toebeans_unit_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = UnitPreferences(context)
    }

    @Test
    fun `fresh install defaults to pounds`() {
        assertEquals(WeightUnit.POUNDS, prefs.weightUnit.value)
    }

    @Test
    fun `setWeightUnit is observable on the StateFlow`() {
        prefs.setWeightUnit(WeightUnit.KILOGRAMS)
        assertEquals(WeightUnit.KILOGRAMS, prefs.weightUnit.value)
    }

    @Test
    fun `chosen unit survives reconstruction from the same context`() {
        prefs.setWeightUnit(WeightUnit.KILOGRAMS)
        val rebuilt = UnitPreferences(context)
        assertEquals(WeightUnit.KILOGRAMS, rebuilt.weightUnit.value)
    }
}
