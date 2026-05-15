// Top-level Gradle settings for the toebeans project.
//
// Modules:
//   :shared     — KMP shared module (core models, scheduler, backup, SQLDelight schema)
//   :androidApp — Android application (Compose UI + platform actuators)

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
