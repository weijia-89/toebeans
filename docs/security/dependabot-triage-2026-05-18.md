# Dependabot triage — 2026-05-18

Branch: `chore/dependabot-triage`
Worktree: `.worktrees/dependabot-triage/`
Baseline HEAD: `dd15680`
Alert source: `gh api /repos/weijia-89/toebeans/dependabot/alerts?state=open`
Snapshot: `/tmp/toebeans-alerts.json` (13.9 KB)

## Headline

**43 open alerts on `weijia-89/toebeans`** at the time of this triage.
Severity mix: 14 high, 24 medium, 5 low.

Every alert maps to a transitive dependency of the **Android Gradle Plugin**
(AGP `8.7.0`) and its grpc/netty/protobuf/bouncycastle build-tool stack.

**Zero of the 43 reach the app runtime classpath.** Confirmed by direct
classpath inspection on this baseline:

```
:shared:jvmRuntimeClasspath              matches: 0
:androidApp:releaseRuntimeClasspath      matches: 0
buildscript classpath (gradle plugins)   matches: 43
```

Search command and full output: `/tmp/toebeans-classpath.log`.

## Threat-model interpretation

The toebeans threat model (`SECURITY.md`) covers user-facing risks: data on
the device, the app's behavior against an active or malicious user. Buildscript
transitives are not in that threat surface. They are a developer-machine
supply-chain risk:

- Could a malicious or compromised Maven artifact attack the developer's
  machine during build? Same risk as every other Gradle project.
- Could one of these vulnerabilities reach an end-user device? No. The
  build-tool classpath is consumed by Gradle on the developer's machine and
  by GitHub Actions runners. Nothing from this classpath is packaged into
  the APK.

For a personal Android project on a maintainer-controlled build machine,
GitHub Actions runner, and a curated set of Maven repos (Google + Maven
Central, pinned in `settings.gradle.kts`), the realized risk from these
specific advisories is below the bar that justifies forced upgrades.

## Per-package summary

| Package | # alerts | Worst | Patched ≥ | Source |
|---|---:|---|---|---|
| `com.google.protobuf:protobuf-java` | 1 | high | 3.25.5 | AGP grpc-netty |
| `commons-io:commons-io` | 1 | high | 2.14.0 | AGP lint |
| `io.netty:netty-codec` | 2 | high | 4.1.125/4.1.133 | AGP grpc-netty |
| `io.netty:netty-codec-http` | 10 | high | 4.1.108/4.1.125 | AGP grpc-netty |
| `io.netty:netty-codec-http2` | 4 | high | 4.1.100/4.1.124 | AGP grpc-netty |
| `io.netty:netty-handler` | 2 | high | 4.1.118 | AGP grpc-netty |
| `org.bitbucket.b_c:jose4j` | 1 | high | 0.9.6 | AGP signing |
| `org.bouncycastle:bcprov-jdk18on` | 7 | high | 1.78/1.84 | AGP signing |
| `org.jdom:jdom2` | 1 | high | 2.0.6.1 | AGP lint |
| `ch.qos.logback:logback-core` | 4 | medium | 1.3.15+ | AGP build logging |
| `com.google.guava:guava` | 2 | medium | 32.0.0-android | AGP / Kotlin |
| `com.squareup.wire:wire-runtime` | 1 | medium | 5.2.0 | AGP protobuf |
| `io.netty:netty-common` | 2 | medium | 4.1.115/4.1.118 | AGP grpc-netty |
| `org.apache.commons:commons-compress` | 2 | medium | 1.26.0 | AGP bundler |
| `org.bouncycastle:bcpkix-jdk18on` | 2 | medium | 1.79/1.84 | AGP signing |
| `io.netty:netty-handler-proxy` | 1 | low | 4.1.133.Final | AGP grpc-netty |

## Three paths forward

### Path A: Dismiss with documented rationale

Use the GitHub Dependabot API to dismiss each alert with reason
`not_used` (accurate: app runtime does not use these) and a short
comment pointing at this triage doc and the buildscript-only finding.

Pros: fast, accurate, leaves a permanent record on the GitHub side.
Cons: future alerts on the same transitives will reopen until the
underlying AGP version changes; dismissal needs to be redone or
automated via `.github/dependabot.yml` ignores.

Command shape (one alert at a time; loopable):

```bash
gh api -X PATCH /repos/weijia-89/toebeans/dependabot/alerts/<N> \
  -f state=dismissed \
  -f dismissed_reason=not_used \
  -f dismissed_comment="Buildscript-only transitive dep (AGP 8.7.0 \
plugin classpath). Not in app runtime classpath. See \
docs/dependabot-triage-2026-05-18.md."
```

### Path B: Bump AGP to a newer patch

`com.android.tools.build:gradle` 8.7.0 (Oct 2024) → 8.7.3 (latest 8.7
patch) or 8.8.x. Newer AGP usually pulls newer transitives, sometimes
all the way to patched versions, sometimes only partway.

Vibe-dangerous per `AGENTS.md`. Requires the full ≥95-confidence
discipline: read the diff, verify Kotlin 2.0.21 compatibility, run
shared:jvmTest and the five fitness functions, build the release
variant, run macrobench. Risk surface: AGP minor bumps can change
manifest merger behavior, R8 / D8 codegen, lint rule defaults.

Likely outcome: reduces the alert count but does not eliminate it.
Would still need Path A for the residual.

### Path C: Force transitive versions via dependency constraints

Add to `settings.gradle.kts` under `dependencyResolutionManagement`:

```kotlin
dependencyResolutionManagement {
    // ... existing repos ...
}

// NEW: force-upgrade vulnerable transitives in the buildscript classpath.
// These do not affect the app runtime (release APK does not include them);
// the constraints exist to satisfy Dependabot and to harden the developer
// build environment against the listed CVEs.
buildscript {
    configurations.all {
        resolutionStrategy {
            force(
                "io.netty:netty-codec:4.1.133.Final",
                "io.netty:netty-codec-http:4.1.133.Final",
                "io.netty:netty-codec-http2:4.1.133.Final",
                "io.netty:netty-handler:4.1.118.Final",
                "io.netty:netty-common:4.1.118.Final",
                "org.bouncycastle:bcprov-jdk18on:1.84",
                "org.bouncycastle:bcpkix-jdk18on:1.84",
                // ... etc
            )
        }
    }
}
```

Vibe-dangerous and brittle: AGP plugin internals may break if a
forced transitive is API-incompatible with what AGP expects. Each
forced version needs verification against AGP's source.

## Recommendation

**Path A first**, on the basis that the realized risk is buildscript-only
and the dismissal reason `not_used` is factually accurate. Pair it with a
`.github/dependabot.yml` config that filters the buildscript ecosystem
or this specific set of packages, so the same alerts do not reappear
until the underlying AGP version is bumped.

Reserve Path B for the next AGP bump that lands for unrelated reasons
(e.g. a Kotlin 2.1 upgrade or a new Compose Multiplatform release).
Treat it as a chore that piggybacks on a real motivation, not as a
standalone security fix.

Skip Path C: forcing AGP's internal transitives is high-risk for a
non-runtime gain.

## Verification baseline (run on `dd15680` before any change)

* `shared:jvmTest` → BUILD SUCCESSFUL
* `test_no_network.sh` → PASS
* `test_no_analytics.sh` → PASS
* `test_permission_allowlist.sh` → PASS
* `test_scheduler_purity.sh` → PASS
* `test_no_pii_in_crash_log.sh` → PASS

Full log: `/tmp/toebeans-baseline.log`.

## Files referenced (this worktree)

* `gradle/libs.versions.toml` — version catalog (no listed package
  matches a vulnerable transitive; toebeans does not directly depend
  on any of them)
* `settings.gradle.kts` — Dependabot's reported "manifest" for every
  alert; contains no dependency declarations, only plugin and repo
  configuration
* `SECURITY.md` — threat model (does not currently address
  buildscript-transitive supply-chain risk; could be amended)
* `AGENTS.md` — vibe-safety classification for Gradle dep changes
