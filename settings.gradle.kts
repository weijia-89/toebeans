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

// CVE-2026-45799 (GHSA-7xpr-hc2w-34m9) and CVE-2026-63126: androidx.benchmark pulls
// wire-runtime 4.9.7 on :macrobench only. wire-runtime-jvm has no patched release;
// pin wire-runtime 6.4.5.
val wireRuntimeCoordinate = "com.squareup.wire:wire-runtime:6.4.5"
// CVE-2023-3635 (GHSA-w33c-445m-f8w7): transitive okio < 3.4.0 on Gradle plugin /
// macrobench classpaths. Force patched okio (+ okio-jvm) project-wide.
val okioVersion = "3.18.2"
val okioCoordinate = "com.squareup.okio:okio:$okioVersion"
val okioJvmCoordinate = "com.squareup.okio:okio-jvm:$okioVersion"

// Buildscript classpath already resolves 4.2.19.Final (build.gradle.kts).
// Open 4.2 advisories are patched at 4.2.17.Final or earlier.
// 4.1.135.Final is inside the open 4.1 ranges (patched at 4.1.136 / 4.1.137).
val nettyVersion = "4.2.19.Final"

// CVE-2026-10532 / CVE-2026-9828: logback-core < 1.5.34
val logbackVersion = "1.6.5"
val logbackCoreCoordinate = "ch.qos.logback:logback-core:$logbackVersion"

// CVE-2026-XXXX: bouncycastle < 1.85 (GOST 28147 CTR mode keystream reuse)
val bouncycastleVersion = "1.86"
val bouncycastleCoordinate = "org.bouncycastle:bcprov-jdk18on:$bouncycastleVersion"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy {
            force(wireRuntimeCoordinate)
            force(okioCoordinate)
            force(okioJvmCoordinate)
            force(logbackCoreCoordinate)
            force(bouncycastleCoordinate)
            eachDependency {
                when (requested.group) {
                    "com.squareup.wire" -> {
                        when (requested.name) {
                            "wire-runtime-jvm" -> {
                                useTarget(wireRuntimeCoordinate)
                                because("CVE-2026-45799: wire-runtime-jvm discontinued")
                            }
                            "wire-runtime" -> {
                                useVersion("6.4.5")
                                because("CVE-2026-45799 and CVE-2026-63126")
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
                    "io.netty" -> {
                        useVersion(nettyVersion)
                        because("CVE-2025-14813 / CVE-2026-50010 / CVE-2026-45416 / CVE-2026-44249 / CVE-2026-50560 / CVE-2026-48043 / CVE-2026-47244 / CVE-2026-50020")
                    }
                    "ch.qos.logback" -> {
                        when (requested.name) {
                            "logback-core", "logback-classic" -> {
                                useVersion(logbackVersion)
                                because("CVE-2026-10532 / CVE-2026-9828")
                            }
                        }
                    }
                    "org.bouncycastle" -> {
                        when (requested.name) {
                            "bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on" -> {
                                useVersion(bouncycastleVersion)
                                because("CVE-2026-XXXX: GOST 28147 CTR mode keystream reuse")
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
