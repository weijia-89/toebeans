package app.toebeans.android.macrobench

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reminder List scroll macrobenchmark for the toebeans Android app.
 *
 * **Budget (ADR-0008):** Reminder List LazyColumn scroll renders at 60 fps median with
 * ≤ 5% of frames exceeding 32 ms on Nokia C12 class hardware. As with
 * [StartupBenchmark], the budget is not enforced at the test level; instead the
 * benchmark emits `FrameTimingMetric` JSON the perf-tracking workflow compares against
 * the ADR-0008 ceiling.
 *
 * ## Why two variants
 *
 * `scrollCompilationNone` measures the worst-case (interpreted) path: the first time a
 * fresh-install user scrolls the list before ART has compiled anything. This is the
 * load-bearing budget number.
 *
 * `scrollCompilationBaselineProfile` measures the same path with AGP's Baseline Profile
 * compilation applied. Until the M1.5 Baseline Profile lands in the app, this variant
 * tells us how much AOT compilation alone would buy us. Useful as a perf-investigation
 * tool, not a budget check.
 *
 * ## Known limitation: demo dataset is single-row in M1
 *
 * The in-process demo seed [app.toebeans.android.data.loadDemoData] currently seeds one
 * pet (Luna), one medication (Methimazole), and one schedule. The Reminder List
 * LazyColumn therefore renders one row. The swipe gestures here still exercise the
 * LazyColumn layout pass, the row composable's recomposition path, and the navigation
 * shell's frame budget, but they cannot meaningfully stress fps under a long list yet.
 *
 * When SQLDelight persistence ships (M1 Tier C) and the demo seed grows to a
 * representative dataset, this benchmark will start producing budget-relevant numbers
 * without code changes. The scaffolding intentionally drives the same `swipeUp` shape
 * the populated case will use.
 *
 * Tracked under `docs/ROADMAP.md` Tier B.
 *
 * ## How to run locally
 *
 * 1. Connect a physical device or boot an API 29+ emulator with hardware acceleration.
 * 2. `./gradlew :macrobench:connectedBenchmarkAndroidTest` produces JSON + a trace
 *    file under `macrobench/build/outputs/connected_android_test_additional_output/`.
 * 3. Inspect `frameDurationCpuMs` and `frameOverrunMs` (median, p99). On a healthy
 *    scroll the median should sit comfortably under 16 ms and p99 under 32 ms.
 *
 * ## Why this is not in PR CI
 *
 * Same reason as [StartupBenchmark]: macrobench needs a profileable on-device target,
 * which GitHub Actions hosted runners cannot supply. Manual + nightly per ADR-0008
 * sequencing.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
public class ReminderListScrollBenchmark {
    @get:Rule
    public val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

    @Test
    public fun scrollCompilationNone(): Unit = scroll(CompilationMode.None())

    @Test
    public fun scrollCompilationBaselineProfile(): Unit = scroll(CompilationMode.Partial())

    private fun scroll(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            compilationMode = compilationMode,
            setupBlock = {
                // Start fresh on the home screen, launch the app, dismiss the
                // first-launch dialog (if shown; only on the first iteration of a clean
                // install), and navigate to the Reminders tab. measureBlock then captures
                // only the swipe frames, not startup or navigation.
                pressHome()
                startActivityAndWait()
                loadDemoDataIfPrompted()
                navigateToReminders()
            },
        ) {
            scrollReminderList()
        }
    }

    /**
     * Tap "Load demo data" on the first-launch dialog if it is showing. After the first
     * iteration the dialog is suppressed by `FirstLaunchPreferences`, so a short timeout
     * + null-check is enough; no error if absent.
     */
    private fun MacrobenchmarkScope.loadDemoDataIfPrompted() {
        val loadDemo =
            device.wait(
                Until.findObject(By.desc("Load Rufus and Luna demo data")),
                DIALOG_TIMEOUT_MS,
            )
        loadDemo?.click()
        device.waitForIdle()
    }

    /**
     * Tap the Reminders bottom-nav tab. The tab label is rendered as `Text("Reminders")`
     * inside `NavigationBarItem`, so the Compose semantics tree exposes it to UiAutomator
     * via `By.text`.
     */
    private fun MacrobenchmarkScope.navigateToReminders() {
        val remindersTab =
            device.wait(
                Until.findObject(By.text(REMINDERS_TAB_LABEL)),
                UI_TIMEOUT_MS,
            ) ?: error("Reminders bottom-nav tab not found within $UI_TIMEOUT_MS ms")
        remindersTab.click()
        device.waitForIdle()
    }

    /**
     * Drive `SCROLL_GESTURES` swipeUp gestures across the LazyColumn. We use
     * `device.swipe` with explicit coordinates (rather than `UiObject2.fling`) so the
     * gesture shape stays stable even on the degenerate single-row dataset the M1 demo
     * seed produces. UiObject2 lookups for "scrollable" composables can be brittle
     * before the Compose-to-UiAutomator semantics bridge fully settles.
     *
     * Vertical anchors at 80% / 20% of display height keep the gesture clear of the
     * system status bar (top) and the bottom navigation rail / system gesture inset
     * (bottom). 30 steps gives a ~smooth swipe matching a real user's thumb.
     */
    private fun MacrobenchmarkScope.scrollReminderList() {
        val width = device.displayWidth
        val height = device.displayHeight
        val centerX = width / 2
        val startY = (height * SWIPE_START_FRACTION).toInt()
        val endY = (height * SWIPE_END_FRACTION).toInt()
        repeat(SCROLL_GESTURES) {
            device.swipe(centerX, startY, centerX, endY, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "app.toebeans.android"

        // Five iterations matches [StartupBenchmark.ITERATIONS]; bump to 10 in a perf-
        // investigation session if the CV (coefficient of variation) on frame timings
        // exceeds 15%.
        const val ITERATIONS = 5

        // Four full-viewport swipes per iteration. Enough to land 60–120 frames per
        // iteration on a 60 Hz device, which gives FrameTimingMetric a meaningful
        // percentile distribution without inflating measurement wall-clock time.
        const val SCROLL_GESTURES = 4

        // 30 swipe steps ≈ 16 ms per step on a 60 Hz pipeline, roughly matching a real
        // half-second thumb flick.
        const val SWIPE_STEPS = 30

        const val SWIPE_START_FRACTION = 0.8
        const val SWIPE_END_FRACTION = 0.2

        const val REMINDERS_TAB_LABEL = "Reminders"
        const val DIALOG_TIMEOUT_MS = 1_500L
        const val UI_TIMEOUT_MS = 5_000L
    }
}
