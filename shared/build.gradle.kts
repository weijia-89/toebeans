// :shared — Kotlin Multiplatform module
//
// commonMain holds the core domain models, scheduler logic, backup codec, and
// SQLDelight schema. androidMain holds the Android driver glue. iosMain is
// scaffolded but disabled at milestone 1 (see gradle.properties: toebeans.enableIosTargets).
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
        // Milestone 5 — disabled at milestone 1 to keep build times sharp.
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

// Test failures are now fatal. The v0.1 `ignoreFailures = true` block has been removed
// because DefaultScheduleCalculator is implemented and all SchedulePhaseRulesTest cases
// pass. Any future test regression should fail the build at every layer (local, CI).

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
                // Android-platform expect/actual implementation. Tested by androidUnitTest in
                // :androidApp via Robolectric, not by :shared:jvmTest. Including it here
                // double-counts uncovered lines because :shared can never exercise it.
                classes("app.toebeans.core.backup.AndroidBackupCipher")
            }
        }
        verify {
            // Per ADR-0006: line coverage is the v0.1 proxy for mutation testing (pitest is
            // deferred because of KMP integration gaps). Threshold raised from 0 to 85 once
            // DefaultScheduleCalculator and the backup codec landed with their tests.
            rule {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                bound {
                    minValue = 85
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
