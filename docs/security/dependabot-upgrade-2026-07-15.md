# Dependabot Security Upgrade -- 2026-07-15 (alerts #47-#57)

**Date:** 2026-07-15
**Branch:** `chore/dependabot-security-upgrade-2026-07-15`

## Alerts

| # | Package | CVE | GHSA | Severity | Patched >= | Status |
|---|---|---|---|---|---|---|
| 54 | `org.bouncycastle:bcprov-jdk18on` | CVE-2025-14813 | GHSA-574f-3g2m-x479 | **critical** | 1.80.2 | **fixed** |
| 47 | `io.netty:netty-handler` | CVE-2026-44249 | GHSA-3qp7-7mw8-wx86 | high | 4.1.135.Final | **fixed** |
| 48 | `io.netty:netty-handler` | CVE-2026-45416 | GHSA-x4gw-5cx5-pgmh | high | 4.1.135.Final | **fixed** |
| 51 | `io.netty:netty-handler` | CVE-2026-50010 | GHSA-c653-97m9-rcg9 | high | 4.1.135.Final | **fixed** |
| 52 | `io.netty:netty-codec-http` | CVE-2026-50020 | GHSA-hvcg-qmg6-jm4c | medium | 4.1.135.Final | **fixed** |
| 49 | `io.netty:netty-codec-http2` | CVE-2026-47244 | GHSA-5x3r-wrvg-rp6q | medium | 4.1.135.Final | **fixed** |
| 50 | `io.netty:netty-codec-http2` | CVE-2026-48043 | GHSA-c2gf-v879-257j | medium | 4.1.135.Final | **fixed** |
| 53 | `io.netty:netty-codec-http2` | CVE-2026-50560 | GHSA-563q-j3cm-6jxm | medium | 4.1.135.Final | **fixed** |
| 55 | `ch.qos.logback:logback-core` | CVE-2026-9828 | GHSA-p47f-322f-whfh | low | 1.5.33 | **fixed** |
| 57 | `ch.qos.logback:logback-core` | CVE-2026-10532 | GHSA-jhq6-gfmj-v8fx | low | 1.5.34 | **fixed** |

## Source

- `bcprov-jdk18on` -- direct runtime dependency in `:shared` backup module (ADR-0018 Argon2id KDF). Consumed on `:shared:jvmRuntimeClasspath` and `:androidApp:releaseRuntimeClasspath`.
- `io.netty:*` -- buildscript-only transitives of AGP 8.7.0 -> grpc-netty 1.57.0. Not present on any app runtime classpath.
- `logback-core` -- buildscript-only transitive of AGP 8.7.0 build logging. Not present on any app runtime classpath.

## Fix

### Runtime dependency (critical)

`gradle/libs.versions.toml`:
- `bouncycastle = "1.78.1"` -> `"1.80.2"`

Test-as-spec: `shared/src/jvmTest/kotlin/app/toebeans/core/backup/BouncyCastleVersionTest.kt` asserts resolved version >= 1.80.2.

### Buildscript transitives

`build.gradle.kts` -- `buildscript` block with `resolutionStrategy.force()`:
- `io.netty:netty-handler:4.1.135.Final`
- `io.netty:netty-codec-http:4.1.135.Final`
- `io.netty:netty-codec-http2:4.1.135.Final`
- `ch.qos.logback:logback-core:1.5.34`

`settings.gradle.kts` -- defense-in-depth via `gradle.beforeProject` `force()` and `eachDependency` substitution rules (mirrors existing wire/okio pattern).

## Verify

```bash
# BouncyCastle runtime version
./gradlew :shared:jvmTest --tests "app.toebeans.core.backup.BouncyCastleVersionTest"

# Netty buildscript resolution
./gradlew buildEnvironment | grep -E "io.netty:netty-(handler|codec-http|codec-http2)"
# Expect 4.1.135.Final (with -> arrows showing upgrade from older transitives)

# Full fitness functions
bash scripts/test_no_network.sh .
bash scripts/test_no_analytics.sh .
bash scripts/test_permission_allowlist.sh .
bash scripts/test_scheduler_purity.sh .
bash scripts/test_no_pii_in_crash_log.sh .

# Full build
./gradlew :shared:jvmTest :androidApp:assembleDebug
```

## Threat model

Same split as [dependabot-triage-2026-05-18.md](dependabot-triage-2026-05-18.md):

- `bcprov-jdk18on` bump is a **runtime hardening improvement**. The backup module uses BC for Argon2id KDF; a critical CVE in the provider is a real risk to backup integrity.
- Netty/logback fixes are **developer-machine supply-chain hardening**. They run on the buildscript classpath (Gradle plugin resolution) and on CI runners. Nothing from this classpath is packaged into the APK.
