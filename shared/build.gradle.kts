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
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

val enableIosTargets: String by project

kotlin {
    explicitApi()

    // AGP 9 Android-KMP library plugin: all Android config lives inside
    // kotlin { android { } }. No top-level android extension exists. Android
    // resources are not enabled: :shared has no src/androidMain/res directory,
    // and the new plugin leaves resource processing off by default.
    android {
        namespace = "app.toebeans.core"
        compileSdk =
            libs.versions.android.compile.sdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()

        // Host (Robolectric) tests are off by default under the new plugin.
        withHostTest {
            isIncludeAndroidResources = true
        }

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
                // ADR-0023: Decompose types appear in the public navigation API, so this is
                // `api` not `implementation` - :androidApp must resolve ChildStack and Value.
                api(libs.decompose)
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
                // BouncyCastle pure-Java provider for Argon2id key derivation in
                // BackupCipherV2 per ADR-0018 § Decision § Library choice. Mirrored in
                // jvmMain because KMP sibling source sets do not share classpaths.
                implementation(libs.bouncycastle.bcprov)
            }
        }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }

        val jvmMain by getting {
            dependencies {
                // SQLDelight's JDBC SQLite driver backs the in-memory database used by
                // DatabaseFactory.jvm.kt and by :shared:jvmTest. The Android target uses
                // AndroidSqliteDriver via libs.sqldelight.driver.android; this driver
                // exists strictly for the JVM smoke / unit test surface.
                implementation(libs.sqldelight.driver.jvm)
                // BouncyCastle pure-Java provider for Argon2id key derivation in
                // BackupCipherV2 per ADR-0018 § Decision § Library choice. Mirrored in
                // androidMain because KMP sibling source sets do not share classpaths.
                implementation(libs.bouncycastle.bcprov)
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

sqldelight {
    databases {
        create("ToebeansDatabase") {
            packageName.set("app.toebeans.core.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Build-time schema drift gate: committed 1.db is migrated through
            // 1.sqm..4.sqm and compared to the current .sq schema by
            // verifySqlDelightMigration (wired into check). SQLDelight issue #4759
            // can hang verification with foreign keys — see daily log for timing.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
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

// detekt 2: the extension is lazy (Property setters) and maxIssues was replaced
// by failOnSeverity. The repo's configuration-cache.problems=fail setting stays;
// detekt itself is invoked with --no-configuration-cache (see CI + verify script).
detekt {
    config.setFrom(files("$rootDir/.detekt.yml"))
    buildUponDefaultConfig.set(true)
    allRules.set(false)
}

// detekt 2 makes the umbrella `:shared:detekt` task NO-SOURCE: only the per-compilation
// and per-source-set tasks below actually analyse shared Kotlin. Collect them so `check`,
// CI and pre-push cannot silently skip shared lint. Run with --no-configuration-cache
// (detekt 2.0.0-alpha.6 is not configuration-cache safe).
val detektSharedTaskNames =
    listOf(
        "detektMainAndroid",
        "detektMainJvm",
        "detektHostTestAndroid",
        "detektTestJvm",
        "detektCommonMainSourceSet",
        "detektCommonTestSourceSet",
        "detektAndroidMainSourceSet",
        "detektJvmMainSourceSet",
        "detektJvmTestSourceSet",
    )

val detektShared =
    tasks.register("detektShared") {
        group = "verification"
        description = "Runs every detekt task that analyses :shared sources under detekt 2."
        dependsOn(detektSharedTaskNames.map { tasks.named(it) })
    }

tasks.named("check") {
    dependsOn(detektShared)
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
                // Android-platform expect/actual implementations. Tested by androidUnitTest
                // in :androidApp via Robolectric, not by :shared:jvmTest. Including them
                // here double-counts uncovered lines because :shared can never exercise
                // them.
                classes("app.toebeans.core.backup.AndroidBackupCipher")
                classes("app.toebeans.core.backup.AndroidBackupCipherV2")
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
