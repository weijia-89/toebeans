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

rootProject.name = "toebeans"

include(":shared")
include(":androidApp")
include(":macrobench")
