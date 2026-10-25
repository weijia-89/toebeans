package app.toebeans.android.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0011 test-contract item 3, rendered half: the Today denial banner renders
 * with the verbatim ADR sentence, and the sentence is the whole control — a tap
 * opens notification settings.
 *
 * First Compose test per ADR-0013 (rendered-UI contract verification). The
 * banner hides its text from the semantics tree with `clearAndSetSemantics` and
 * re-exposes it as `contentDescription = "Warning: <sentence>"`, so the
 * user-observable contract (what TalkBack announces) is the content description.
 * The shared constant-to-ADR-sentence pin lives in
 * `shared/.../NotificationDenialBannerTest.kt`; this test hard-codes the
 * sentence again so a copy drift fails at both layers.
 *
 * SDK 33 is the POST_NOTIFICATIONS fork and the minimum Compose ui-test
 * supports under Robolectric (ADR-0013 implementer notes). The application is
 * overridden to a plain Application so Robolectric does not boot ToebeansApp:
 * its onCreate calls startKoin, which throws KoinApplicationAlreadyStarted-
 * Exception on the second test in the class, and this composable needs no DI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NotificationDenialBannerRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `banner renders with the ADR-0011 sentence as its announced copy`() {
        rule.setContent {
            NotificationDenialBanner(onOpenNotificationSettings = {})
        }
        rule
            .onNodeWithContentDescription(
                "Warning: Your last dose alarm did not show as a notification " +
                    "because notifications are disabled for toebeans. Tap to fix in " +
                    "Settings, or open the History tab to see what fired.",
            ).assertIsDisplayed()
    }

    @Test
    fun `tapping the banner opens notification settings`() {
        var taps = 0
        rule.setContent {
            NotificationDenialBanner(onOpenNotificationSettings = { taps += 1 })
        }
        rule
            .onNodeWithContentDescription(
                "Warning: Your last dose alarm did not show as a notification " +
                    "because notifications are disabled for toebeans. Tap to fix in " +
                    "Settings, or open the History tab to see what fired.",
            ).performClick()
        assertEquals(1, taps)
    }
}
