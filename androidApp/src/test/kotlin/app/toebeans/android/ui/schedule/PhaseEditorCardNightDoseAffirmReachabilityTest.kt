package app.toebeans.android.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Night-dose affirm button reachability (B9 accessibility regression pin).
 *
 * The B9 night-dose warning banner in [PhaseEditorCard] wraps its [androidx.compose.material3.Surface]
 * in `clearAndSetSemantics`, which erases the "Yes, that's intentional" [androidx.compose.material3.TextButton]
 * from the semantics tree: the button works by touch but is unreachable by TalkBack.
 * These tests pin the intended behavior — the affirm button is IN the semantics tree,
 * findable by its text, and clicking it fires [PhaseEditorCard]'s onAffirmNightDose.
 *
 * The full card is taller than the compose-test root, so the content is wrapped in a
 * vertical scroll container (mirroring the real screen) and the button is scrolled
 * into view before the click; without this the lower half of the card is measured
 * with zero-height constraints and touch injection has nowhere to land.
 *
 * Render-test conventions follow `NotificationDenialBannerRenderTest` (ADR-0013):
 * Robolectric SDK 33 with a plain Application override so Koin is not booted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PhaseEditorCardNightDoseAffirmReachabilityTest {
    @get:Rule
    val rule = createComposeRule()

    private fun nightDraft() =
        PhaseDraft(
            durationDaysText = "14",
            doseTimes = listOf(LocalTime(3, 0)),
            dayIntervalText = "1",
            doseAmount = "50",
            doseUnit = null,
            error = null,
            nightDoseWarning = true,
        )

    @Test
    fun `night-dose affirm button is reachable in the semantics tree`() {
        rule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PhaseEditorCard(
                    index = 0,
                    draft = nightDraft(),
                    isOnlyPhase = true,
                    onChange = {},
                    onRemove = {},
                )
            }
        }
        rule
            .onNodeWithText("Yes, that's intentional")
            .assertExists()
    }

    @Test
    fun `tapping the night-dose affirm button fires onAffirmNightDose`() {
        var affirmations = 0
        rule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PhaseEditorCard(
                    index = 0,
                    draft = nightDraft(),
                    isOnlyPhase = true,
                    onChange = {},
                    onRemove = {},
                    onAffirmNightDose = { affirmations += 1 },
                )
            }
        }
        rule
            .onNodeWithText("Yes, that's intentional")
            .performScrollTo()
            .performClick()
        assertEquals(1, affirmations)
    }
}
