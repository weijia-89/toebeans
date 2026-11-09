# Dependabot alert 68

**Date:** 2026-11-08
**Alert:** 68
**Advisory:** GHSA-r937-wjx7-w2jp
**Resolved coordinate:** `org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21`
**Patched version:** `2.4.20-Beta1`

## Advisory

Unsafe deserialization of Kotlin build-cache metadata.

## Decision

Wait for a stable release at or above 2.4.20.

A beta compiler is the wrong trade for a bug that needs a poisoned build cache on the maintainer machine.

Leave the GitHub alert open.

`gradle/libs.versions.toml` stays `kotlin = "2.0.21"`.
