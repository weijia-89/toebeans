package app.toebeans.android.macrobench

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start macrobenchmark for the toebeans Android app.
 *
 * **Budget (ADR-0008):** App cold-start (Activity onCreate → first frame) < 2,000 ms on
 * Nokia C12 class hardware. This benchmark does not enforce the budget at the test
 * level (Macrobenchmark does not yet support hard-fail thresholds in this version);
 * instead it produces a `timeToInitialDisplayMs` metric that the perf-tracking workflow
 * compares against the ADR-0008 ceiling.
 *
 * ## Why two variants
 *
 * `startupCompilationNone` measures the worst-case (interpreted) launch — what the
 * first user gets after a fresh install before ART has compiled anything. This is the
 * load-bearing budget number; if it exceeds 2,000 ms on the target device we have a
 * real problem.
 *
 * `startupCompilationBaselineProfile` measures the same path with AGP's BaselineProfile
 * compilation applied. It tells us how much the Baseline Profile (M1.5 work) will buy
 * us. Until that profile lives in the app, this variant is the upper bound of "what AOT
 * compilation alone gets us" — useful as a perf-investigation tool.
 *
 * ## How to run locally
 *
 * 1. Connect a physical device or boot an API 29+ emulator with hardware acceleration.
 * 2. `./gradlew :macrobench:connectedBenchmarkAndroidTest` — produces JSON + a trace
 *    file under `macrobench/build/outputs/connected_android_test_additional_output/`.
 * 3. Inspect `timeToInitialDisplayMs` (median, min, max). The current ADR-0008 ceiling
 *    is 2,000 ms; flag anything > 1,500 ms as a perf regression worth investigating.
 *
 * ## Why this is not in PR CI
 *
 * Macrobenchmark requires a real device or an emulator with hardware acceleration and
 * `<profileable>` support. GitHub Actions hosted runners do not expose hardware
 * acceleration; running on `reactivecircus/android-emulator-runner` adds ~5 minutes to
 * every PR which we are not willing to pay yet. Plan: nightly workflow + manual
 * trigger; see `macrobench/README.md`.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
public class StartupBenchmark {
    @get:Rule
    public val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

    @Test
    public fun startupCompilationNone(): Unit = startup(CompilationMode.None())

    @Test
    public fun startupCompilationBaselineProfile(): Unit = startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "app.toebeans.android"

        // Five iterations is the Macrobenchmark sample-default and gives a usable median
        // without burning device time. The CV (coefficient of variation) for cold-start
        // is typically < 10% over 5 iterations on a warm emulator. Bump to 10 in a
        // perf-investigation session if the variance is high.
        const val ITERATIONS = 5
    }
}
