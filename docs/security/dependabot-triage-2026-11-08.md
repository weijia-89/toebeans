# Dependabot alert 68

**Date:** 2026-11-08
**Alert:** 68
**Advisory:** GHSA-r937-wjx7-w2jp
**Resolved coordinate:** `org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21`
**Patched version:** `2.4.20-Beta1`

## Advisory

Unsafe deserialization of Kotlin build-cache metadata.

## Decision

2026-11-08: do not take `2.4.20-Beta1`. A beta compiler is the wrong trade for a bug that needs a poisoned build cache.

2026-11-09: stable `2.4.20` (published 2026-09-07) is the pin. `kotlin = "2.4.20"` in `gradle/libs.versions.toml`. Kover moves to `0.9.11` because `0.8.3` cannot read the Kotlin 2.4 compilation model. Compose Multiplatform stays `1.7.3`. The app and shared JVM tests compiled and passed on that pair.
