// :macrobench — ADR-0008 perf-budget enforcement module.
//
// **Scope and rationale**
//
// This module exists to make the ADR-0008 performance budgets falsifiable. Today it
// ships the cold-start budget (< 2,000 ms on Nokia C12 class). Reminder-list scroll
// (60 fps median, ≤ 5% frames > 32ms) will land alongside the Reminder List screen
// itself in M1 Tier B; the scaffolding here is set up so adding that benchmark is a
// single-file edit.
//
// **Why a separate module**
//
// The AGP `com.android.test` plugin is incompatible with `com.android.application` and
// `com.android.library`. Macrobenchmark also pulls in androidx.benchmark, androidx.test
// runner, and uiautomator — none of which should bloat the production app's classpath.
// Putting the benchmarks in their own module gives them their own variant ("benchmark"
// of :androidApp) and their own dependency graph.
//
// **Vibe tier**
//
// `vibe-careful` per AGENTS.md. New module + new deps was vibe-dangerous per the table;
// human review was granted out-of-band before this module was created. Subsequent edits
// (adding benchmarks, tuning iterations) are scaffolding work and remain vibe-careful.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    // Intentionally NOT applying `androidx.benchmark`: that plugin is for microbenchmark
    // *library* modules (com.android.library + benchmark-junit4), not for macrobenchmark
    // *test* modules. The plugin's own error message says so verbatim:
    //   "The androidx.benchmark plugin currently supports only android library modules.
    //    Note that to run macrobenchmarks, this plugin is not required."
    // The macrobench runtime deps (benchmark-macro-junit4 + uiautomator) are pulled in
    // via the dependencies{} block below, which is all that's needed.
}

android {
    namespace = "app.toebeans.android.macrobench"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        // Macrobenchmark requires API 23+ on the test device; we use API 29+ in practice
        // because that's where <profileable> takes effect (see the benchmark-variant
        // manifest in :androidApp).
        minSdk = 29
        targetSdk =
            libs.versions.android.target.sdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    buildTypes {
        // Only the benchmark variant exists for a com.android.test module; it points at
        // the matching variant of the app under measurement.
        create("benchmark") {
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    // Point the benchmark module at the matching variant of :androidApp.
    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Disable any variants not named "benchmark" — saves Gradle from configuring
        // release/debug variants we never run.
        it.enable = it.buildType == "benchmark"
    }
}
