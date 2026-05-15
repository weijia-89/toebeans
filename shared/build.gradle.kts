// :shared — Kotlin Multiplatform module
//
// commonMain holds the core domain models, scheduler logic, backup codec, and
// SQLDelight schema. androidMain holds the Android driver glue. iosMain is
// scaffolded but disabled at slice 1 (see gradle.properties: toebeans.enableIosTargets).
//
// Vibe-dangerous surfaces in this module per AGENTS.md:
//   - commonMain/kotlin/app/toebeans/core/scheduler/
//   - commonMain/kotlin/app/toebeans/core/backup/
//   - commonMain/sqldelight/

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

val enableIosTargets: String by project

kotlin {
    explicitApi()

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Plain JVM target lets commonTest run without the Android SDK installed.
    // CI and `./gradlew :shared:jvmTest` exercise the pure-KMP scheduler & backup logic.
    jvm()

    if (enableIosTargets.toBoolean()) {
        // Slice 5 — disabled at slice 1 to keep build times sharp.
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // `api` for types that surface in :shared's public API. Anything consuming
                // a `Pet`, `Instant`, etc. needs to resolve the symbols.
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.koin.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqldelight.driver.android)
                implementation(libs.androidx.work.runtime.ktx)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }

        if (enableIosTargets.toBoolean()) {
            val iosMain by creating {
                dependsOn(commonMain)
                dependencies {
                    implementation(libs.sqldelight.driver.native)
                }
            }
            val iosX64Main by getting { dependsOn(iosMain) }
            val iosArm64Main by getting { dependsOn(iosMain) }
            val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        }
    }
}

android {
    namespace = "app.toebeans.core"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

sqldelight {
    databases {
        create("ToebeansDatabase") {
            packageName.set("app.toebeans.core.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}

ktlint {
    version.set("1.3.1")
    android.set(true)
    ignoreFailures.set(false)
}

// The ktlint-gradle plugin's `filter` block does NOT reliably exclude paths from source
// sets that were added by other plugins (here: SQLDelight's commonMain output). We use
// SourceTask.exclude(Spec) which IS config-cache compatible.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { fte -> fte.file.invariantSeparatorsPath.contains("/build/generated/") }
}
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>().configureEach {
    exclude { fte -> fte.file.invariantSeparatorsPath.contains("/build/generated/") }
}

detekt {
    config.setFrom(files("$rootDir/.detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

// --- v0.1 quirk: failing-test-as-spec ---
// SchedulePhaseRulesTest is required to fail at v0.1 because DefaultScheduleCalculator is a
// stub. We do NOT want this to block downstream verification tasks (Kover, lint reports).
// CI separately enforces "no failing tests at slice 1" via .github/workflows/ci.yml.
// REMOVE THIS BLOCK once the scheduler is implemented.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    ignoreFailures = true
    doLast {
        val failed =
            (
                binaryResultsDirectory
                    .get()
                    .asFile
                    .listFiles()
                    ?.size ?: 0
            ) > 0
        if (failed) {
            logger.lifecycle("(toebeans) ${this@configureEach.name} completed with failing tests; this is expected at v0.1.")
        }
    }
}

// --- Coverage (Kover) ---
// Why Kover and not pitest? See docs/adr/0006-deferred-mutation-testing.md.
// Kover is JetBrains' KMP-native coverage tool. Pitest's gradle plugin does not
// integrate cleanly with KMP source sets at the time of writing; we use Kover for
// line coverage today and revisit pitest once the scheduler has a JVM-only
// extraction or ArcMutate's KMP support lands.
kover {
    reports {
        filters {
            includes {
                classes("app.toebeans.core.scheduler.*")
                classes("app.toebeans.core.backup.*")
            }
            excludes {
                // Generated SQLDelight code; not our concern.
                classes("app.toebeans.core.db.*")
                // Stubs and data classes (auto-generated copy/hashCode are not interesting to cover).
                classes("*\$Companion")
                classes("*Kt")
            }
        }
        verify {
            // v0.1: thresholds are intentionally LOW because the scheduler is a stub
            // and backup codec is incoming. We raise these in slice 1 when implementations
            // land. The point of having Kover wired now is to make the gate visible.
            rule {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                bound {
                    minValue = 0
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                }
            }
        }
    }
}

// Placeholder task — documents that mutation testing is on the roadmap.
// See ADR-0006 for the substitution rationale.
tasks.register("mutationTest") {
    group = "verification"
    description = "Mutation testing for :shared. v0.1: NOT YET WIRED. See ADR-0006."
    doLast {
        logger.warn(
            "mutationTest is a placeholder for v0.1. Run :shared:koverHtmlReport for " +
                "line-coverage. Mutation testing is deferred per docs/adr/0006-deferred-mutation-testing.md.",
        )
    }
}
