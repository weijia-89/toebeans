package app.toebeans.android.preferences

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [FirstLaunchPreferences]. Vibe-careful tier (UX gate, not the medication path).
 *
 * The assertions cover:
 *  - Fresh install starts with `firstLaunchSeen = false`.
 *  - `markSeen()` is observable via the StateFlow.
 *  - The flag persists across reconstruction from the same context (i.e. a SharedPreferences
 *    write happened, not just an in-memory mutation).
 *  - `resetForTest` correctly returns to fresh-install state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FirstLaunchPreferencesTest {
    private lateinit var context: Context
    private lateinit var prefs: FirstLaunchPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Start each test from a clean prefs file.
        context
            .getSharedPreferences("toebeans_first_launch_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = FirstLaunchPreferences(context)
    }

    @Test
    fun `fresh install reports firstLaunchSeen = false`() {
        assertFalse(
            "a brand-new install must NOT be considered as having seen the first-launch prompt",
            prefs.firstLaunchSeen.value,
        )
    }

    @Test
    fun `markSeen flips the flag to true`() {
        prefs.markSeen()
        assertTrue(prefs.firstLaunchSeen.value)
    }

    @Test
    fun `markSeen is idempotent — calling twice stays true`() {
        prefs.markSeen()
        prefs.markSeen()
        assertTrue(prefs.firstLaunchSeen.value)
    }

    @Test
    fun `markSeen survives reconstruction from the same context`() {
        prefs.markSeen()
        val rebuilt = FirstLaunchPreferences(context)
        assertTrue(
            "the seen flag must persist to SharedPreferences and be readable on rebuild",
            rebuilt.firstLaunchSeen.value,
        )
    }

    @Test
    fun `resetForTest returns to fresh-install state`() {
        prefs.markSeen()
        prefs.resetForTest()
        assertFalse(prefs.firstLaunchSeen.value)
    }
}
