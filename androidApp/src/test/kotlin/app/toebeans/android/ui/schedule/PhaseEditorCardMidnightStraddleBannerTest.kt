package app.toebeans.android.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import app.toebeans.core.model.DoseUnit
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.1-followups #9, rendered half: the midnight-straddle informational banner in
 * [PhaseEditorCard].
 *
 * The detection contract (which dose-time sets set `PhaseDraft.crossesMidnight`) is
 * pinned by [MidnightStraddleDetectionTest]; the VM recompute/dismiss lifecycle is
 * pinned by [ScheduleCreateMidnightStraddleTest]. This class pins the rendered half
 * per ADR-0013: a draft with `crossesMidnight = true` renders the banner whose
 * TalkBack announcement is the exact follow-up sentence as the `contentDescription`
 * (no prefix, unlike the night-dose warning), the live region is polite, and no
 * banner appears when the flag is false.
 *
 * The banner Surface hides its whole subtree from semantics with
 * `clearAndSetSemantics`, so the visible Text node and the "Got it" TextButton have
 * no semantics nodes; the user-observable contract is the content description.
 *
 * SDK 33 and the plain-Application override follow the
 * [app.toebeans.android.ui.home.NotificationDenialBannerRenderTest] pattern (ADR-0013
 * implementer notes). No DI is involved; the card is rendered directly. Helper and
 * fixture names are deliberately distinct from the sibling B7/B8/B9 render tests so
 * both files can merge without symbol collisions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PhaseEditorCardMidnightStraddleBannerTest {
    @get:Rule
    val rule = createComposeRule()

    private val straddleDescription =
        "Heads up: your dose times cross midnight. " +
            "Doses after midnight land on the next calendar day."

    @Test
    fun `straddle banner renders with the exact copy and polite live region`() {
        rule.setContent { straddleCard() }

        val banner =
            rule
                .onNodeWithContentDescription(straddleDescription)
                .performScrollTo()
                .assertIsDisplayed()
        assertEquals(
            "banner must be a polite live region so TalkBack announces it on appearance",
            LiveRegionMode.Polite,
            banner.fetchSemanticsNode().config[SemanticsProperties.LiveRegion],
        )
    }

    @Test
    fun `banner is absent when crossesMidnight is false`() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PhaseEditorCard(
                        index = 0,
                        draft = plainDraft(),
                        isOnlyPhase = true,
                        onChange = {},
                        onRemove = {},
                    )
                }
            }
        }

        rule
            .onNodeWithContentDescription(
                "Heads up: your dose times cross midnight",
                substring = true,
            ).assertDoesNotExist()
    }

    @Test
    fun `banner content description is the exact sentence with no prefix`() {
        // Pins the announcement contract itself: unlike the night-dose warning
        // ("Warning: ..."), the straddle banner is informational and its
        // contentDescription is the bare sentence. A drift in either direction
        // (prefix added, sentence reworded) fails here before TalkBack users see it.
        rule.setContent { straddleCard() }

        rule
            .onNodeWithContentDescription(straddleDescription, substring = false)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Composable
    private fun straddleCard() {
        MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PhaseEditorCard(
                    index = 0,
                    draft = straddleDraft(),
                    isOnlyPhase = true,
                    onChange = {},
                    onRemove = {},
                )
            }
        }
    }

    /**
     * A `[23:00, 01:00]` draft. The `crossesMidnight` flag is derived through
     * [MidnightStraddleDetection.crossesMidnight] rather than hard-coded, so the
     * render test also exercises the production detection rule on the motivating
     * example.
     */
    private fun straddleDraft(): PhaseDraft {
        val doseTimes = listOf(LocalTime(23, 0), LocalTime(1, 0))
        return PhaseDraft(
            durationDaysText = "7",
            doseTimes = doseTimes,
            dayIntervalText = "1",
            doseAmount = "",
            doseUnit = DoseUnit.MG,
            error = null,
            crossesMidnight = MidnightStraddleDetection.crossesMidnight(doseTimes),
        )
    }

    private fun plainDraft(): PhaseDraft =
        PhaseDraft(
            durationDaysText = "7",
            doseTimes = listOf(LocalTime(8, 0), LocalTime(12, 0), LocalTime(18, 0)),
            dayIntervalText = "1",
            doseAmount = "",
            doseUnit = DoseUnit.MG,
            error = null,
            crossesMidnight = false,
        )
}
