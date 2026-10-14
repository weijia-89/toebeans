package app.toebeans.android.ui.firstlaunch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0011 first launch. The dialog cannot finish until the notification
 * permission request returns on API 33+ when it is not already granted.
 */
class FirstLaunchNotificationGateTest {
    @Test
    fun `api 32 finishes the choice without a permission request`() {
        val step = firstLaunchTap(32, granted = false, FirstLaunchChoice.START_EMPTY)
        assertFalse(step.requestPermission)
        assertEquals(FirstLaunchChoice.START_EMPTY, step.finishNow)
    }

    @Test
    fun `api 33 with permission already granted finishes without a request`() {
        val step = firstLaunchTap(33, granted = true, FirstLaunchChoice.LOAD_DEMO)
        assertFalse(step.requestPermission)
        assertEquals(FirstLaunchChoice.LOAD_DEMO, step.finishNow)
    }

    @Test
    fun `api 33 without permission holds the dialog until the request returns`() {
        val step = firstLaunchTap(33, granted = false, FirstLaunchChoice.START_EMPTY)
        assertTrue(step.requestPermission)
        assertNull(step.finishNow)
    }

    @Test
    fun `a denial records the first-run banner and still finishes the choice`() {
        val finish = firstLaunchPermissionResult(granted = false, FirstLaunchChoice.LOAD_DEMO)
        assertEquals(FirstLaunchChoice.LOAD_DEMO, finish.choice)
        assertTrue(finish.showFirstRunBanner)
        assertEquals(
            "Notifications are off, so a dose alarm will not show. Tap to fix in Settings.",
            FIRST_RUN_NOTIFICATION_DENIAL_COPY,
        )
    }

    @Test
    fun `a grant finishes the choice without the first-run banner`() {
        val finish = firstLaunchPermissionResult(granted = true, FirstLaunchChoice.START_EMPTY)
        assertEquals(FirstLaunchChoice.START_EMPTY, finish.choice)
        assertFalse(finish.showFirstRunBanner)
    }
}
