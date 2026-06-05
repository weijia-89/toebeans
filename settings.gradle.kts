// Top-level Gradle settings for the toebeans project.
//
// Modules:
//   :shared     — KMP shared module (core models, scheduler, backup, SQLDelight schema)
//   :androidApp — Android application (Compose UI + platform actuators)
//   :macrobench — AGP `com.android.test` module for ADR-0008 perf-budget enforcement
//                 (cold-start, list scroll, calculator perf). Runs against the
//                 `benchmark` build variant of :androidApp on a connected device or
//                 emulator. Not part of the default PR CI gate — see macrobench/README.md
//                 for the run-on-demand workflow.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// CVE-2026-45799 (GHSA-7xpr-hc2w-34m9): androidx.benchmark pulls wire-runtime 4.9.7 on
// :macrobench only. wire-runtime-jvm has no patched release; unify on wire-runtime 6.3.0+.
val wireRuntimeCoordinate = "com.squareup.wire:wire-runtime:6.3.0"
// CVE-2023-3635 (GHSA-w33c-445m-f8w7): transitive okio < 3.4.0 on Gradle plugin /
// macrobench classpaths. Force patched okio (+ okio-jvm) project-wide.
val okioVersion = "3.17.0"
val okioCoordinate = "com.squareup.okio:okio:$okioVersion"
val okioJvmCoordinate = "com.squareup.okio:okio-jvm:$okioVersion"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy {
            force(wireRuntimeCoordinate)
            force(okioCoordinate)
            force(okioJvmCoordinate)
            eachDependency {
                when (requested.group) {
                    "com.squareup.wire" -> {
                        when (requested.name) {
                            "wire-runtime-jvm" -> {
                                useTarget(wireRuntimeCoordinate)
                                because("CVE-2026-45799: wire-runtime-jvm discontinued")
                            }
                            "wire-runtime" -> {
                                useVersion("6.3.0")
                                because("CVE-2026-45799")
                            }
                        }
                    }
                    "com.squareup.okio" -> {
                        when (requested.name) {
                            "okio", "okio-jvm" -> {
                                useVersion(okioVersion)
                                because("CVE-2023-3635")
                            }
                        }
                    }
                }
            }
        }
    }
}

rootProject.name = "toebeans"

include(":shared")
include(":androidApp")
include(":macrobench")
