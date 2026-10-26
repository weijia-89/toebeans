package app.toebeans.android.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.height
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.width
import app.toebeans.core.model.DoseUnit
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0013 rendered-UI contract for B9: the night-dose warning banner in
 * [PhaseEditorCard].
 *
 * The VM-level contract (warning flips on for any dose time in [00:00, 06:00), affirm
 * clears it, dose-time edits re-surface it) is pinned by [ScheduleCreateNightDoseTest].
 * This class pins the rendered half with the ADR-0013 reference shape: a draft with
 * `nightDoseWarning = true` renders the banner whose TalkBack announcement is the
 * exact ADR sentence as the `contentDescription` (with the `Warning: ` prefix), the
 * live region is polite, and the affirm action wires through to
 * `onAffirmNightDose`.
 *
 * The banner sits far down the card, below the fold of the default test window, so the
 * card is hosted in a scrollable column and the banner is scrolled into view before it
 * is asserted on. The banner Surface hides its whole subtree from semantics with
 * `clearAndSetSemantics`, so the affirm TextButton inside it has no semantics node and
 * cannot be reached with the standard text/content-description matchers. The affirm
 * tap is therefore placed at touch level inside the banner's own bounds (the button is
 * the bottom element of the banner column) rather than by matcher; this works because
 * pointer-input hit testing is independent of the semantics tree.
 *
 * SDK 33 and the plain-Application override follow the
 * [app.toebeans.android.ui.home.NotificationDenialBannerRenderTest] pattern (ADR-0013
 * implementer notes). No DI is involved; the card is rendered directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PhaseEditorCardNightDoseBannerTest {
    @get:Rule
    val rule = createComposeRule()

    private val warningDescription =
        "Warning: This dose fires between midnight and 6am. " +
            "Confirm you want to be woken up."

    @Test
    fun `night-dose banner renders with the exact ADR copy and polite live region`() {
        var affirmations = 0
        rule.setContent { nightCard(onAffirmNightDose = { affirmations += 1 }) }

        val banner =
            rule
                .onNodeWithContentDescription(warningDescription)
                .performScrollTo()
                .assertIsDisplayed()
        assertEquals(
            "banner must be a polite live region so TalkBack announces it on appearance",
            LiveRegionMode.Polite,
            banner.fetchSemanticsNode().config[SemanticsProperties.LiveRegion],
        )
        assertEquals("rendering the banner alone must not affirm it", 0, affirmations)
    }

    @Test
    fun `affirm button invokes onAffirmNightDose`() {
        var affirmations = 0
        rule.setContent { nightCard(onAffirmNightDose = { affirmations += 1 }) }
        rule.waitForIdle()
        assertEquals("no affirmations before the tap", 0, affirmations)

        // Touch-level tap on the affirm button: bottom element of the banner column.
        // A 40dp button above a 12dp bottom padding puts its center between a quarter
        // and a third of the banner height up from the bottom edge for any 1..2 line
        // message, and a quarter of the width from the left lands inside the button
        // label for any realistic banner width.
        rule
            .onNodeWithContentDescription(warningDescription)
            .performScrollTo()
            .performTouchInput {
                click(Offset(x = width * 0.25f, y = height - height / 4f))
            }
        rule.waitForIdle()

        assertEquals("affirm button must wire through to onAffirmNightDose", 1, affirmations)
    }

    @Test
    fun `banner is absent when nightDoseWarning is false`() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PhaseEditorCard(
                        index = 0,
                        draft = dayPhaseDraft(),
                        isOnlyPhase = true,
                        onChange = {},
                        onRemove = {},
                    )
                }
            }
        }

        rule
            .onNodeWithContentDescription(
                "Warning: This dose fires",
                substring = true,
            ).assertDoesNotExist()
    }

    @Composable
    private fun nightCard(onAffirmNightDose: () -> Unit) {
        MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PhaseEditorCard(
                    index = 0,
                    draft = nightPhaseDraft(),
                    isOnlyPhase = true,
                    onChange = {},
                    onRemove = {},
                    onAffirmNightDose = onAffirmNightDose,
                )
            }
        }
    }

    private fun nightPhaseDraft(): PhaseDraft =
        PhaseDraft(
            durationDaysText = "7",
            doseTimes = listOf(LocalTime(3, 0)),
            dayIntervalText = "1",
            doseAmount = "",
            doseUnit = DoseUnit.MG,
            error = null,
            nightDoseWarning = true,
        )

    private fun dayPhaseDraft(): PhaseDraft =
        PhaseDraft(
            durationDaysText = "7",
            doseTimes = listOf(LocalTime(8, 0)),
            dayIntervalText = "1",
            doseAmount = "",
            doseUnit = DoseUnit.MG,
            error = null,
            nightDoseWarning = false,
        )
}
