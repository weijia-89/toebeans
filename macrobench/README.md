# :macrobench

ADR-0008 performance-budget enforcement. Today this module ships:

- **`StartupBenchmark.startupCompilationNone`** — cold-start of `MainActivity` with no
  AOT compilation (the worst-case path a fresh-install user hits). Budget: **< 2,000 ms
  on Nokia C12 class** (the ADR-0008 lowest-supported device).
- **`StartupBenchmark.startupCompilationBaselineProfile`** — cold-start with AGP's
  Baseline Profile compilation. Today this is informational only; the actual Baseline
  Profile lands in M1.5.

Pending (tracked in `docs/ROADMAP.md`):

- Reminder-list scroll fps benchmark. Ships alongside the Reminder List screen (M1
  Tier B).
- `computeScheduledDoses` JVM microbenchmark — ADR-0008 sets a < 50 ms budget for 72h ×
  4 doses × 2 phases. Lives in `:shared` (JVM) rather than here, because Macrobench
  cannot measure pure-Kotlin performance — only on-device frame/start metrics.

## Running locally

```bash
# 1. Boot an API 29+ emulator with hardware acceleration, OR connect a physical device.
adb devices

# 2. Run the benchmark suite. Output lands under
#    macrobench/build/outputs/connected_android_test_additional_output/.
./gradlew :macrobench:connectedBenchmarkAndroidTest
```

Inspect the resulting JSON (`*-benchmarkData.json`) for `timeToInitialDisplayMs`.
Median, min, max are all reported. Useful sanity checks:

- Median > 2,000 ms → ADR-0008 budget breach. Investigate.
- Median > 1,500 ms → in the danger zone; profile before next release.
- CV (stddev / median) > 15% → noisy measurement; bump `ITERATIONS` in
  `StartupBenchmark.kt` and rerun.

## Why this is not in PR CI (yet)

The GitHub Actions hosted runners do not expose KVM, so Macrobench cannot get a
profiler attached on an emulator. `reactivecircus/android-emulator-runner` can do it on
self-hosted runners but adds ~5 minutes of wall-clock time per PR and a non-trivial
flake rate. Decision: **manual + nightly trigger** rather than per-PR.

Per the ADR-0008 sequencing:
- **M1.1** — module exists, manual run on a developer machine before each release.
- **M1.2** — nightly GitHub Actions workflow on a self-hosted runner; results posted
  to the Issues tab if budget is breached.
- **M1.5+** — multi-device CI matrix (Nokia C12, Moto G Play, A14, Pixel 7a).

## Why a separate Gradle module

The AGP `com.android.test` plugin is incompatible with `com.android.application`. The
macrobench deps (`androidx.benchmark.macro`, `androidx.test.runner`, `uiautomator`)
should not bloat the production APK classpath. Separate module keeps them isolated and
gives us a `benchmark` variant of `:androidApp` that the macrobench module targets.

## Files

- `build.gradle.kts` — applies the `com.android.test` + `androidx.benchmark` plugins,
  pins min SDK to 29 (needed for `<profileable>`), points at `:androidApp` as the
  target project, disables non-benchmark variants for build-time savings.
- `src/main/AndroidManifest.xml` — empty placeholder; the test runner is configured in
  `build.gradle.kts`.
- `src/main/kotlin/app/toebeans/android/macrobench/StartupBenchmark.kt` — the cold-start
  benchmark itself.

## Vibe-tier

`vibe-careful` for normal edits. The original module + dep adds were vibe-dangerous per
AGENTS.md; human review was granted out-of-band when this module was created. Adding
new benchmarks is vibe-careful. Bumping the `androidx.benchmark` major version is
vibe-dangerous.
