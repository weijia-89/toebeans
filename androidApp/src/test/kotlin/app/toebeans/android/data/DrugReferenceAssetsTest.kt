package app.toebeans.android.data

import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The real asset-backed reader must fail closed: with no entry bundled, every lookup
 * returns null and nothing throws. Absence of data never blocks the medication form.
 * No entry content ships in this slice, so only the null paths are observable here.
 */
@RunWith(RobolectricTestRunner::class)
class DrugReferenceAssetsTest {
    private fun reader(): DrugReferenceAssets = DrugReferenceAssets(RuntimeEnvironment.getApplication().assets)

    @Test
    fun `empty bundled entries directory returns no entry`() {
        assertNull(reader().find("AnyMedication"))
    }

    @Test
    fun `blank medication name returns no entry`() {
        assertNull(reader().find("   "))
    }
}
