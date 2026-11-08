# Dependabot alert remediation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the open Dependabot alerts whose resolved jars are still inside a vulnerable range, and stop the settings pin that would downgrade Netty if a module ever requested it.

**Architecture:** Keep one resolution rule per library. The buildscript classpath (`build.gradle.kts`) and the project classpath (`settings.gradle.kts` `gradle.beforeProject`) are different classpaths. A force in settings does not change the buildscript graph. Security update pull requests already exist as a GitHub setting. `.github/dependabot.yml` adds a weekly version-update job and does not close alerts by itself.

**Tech Stack:** Gradle 8.10, version catalog `gradle/libs.versions.toml`, AGP 8.7.0, Kotlin Gradle plugin 2.0.21, GitHub Dependabot.

## Global Constraints

- Local-only. No new network library, analytics SDK, or permission.
- No AI feature, symptom checker, or treatment text.
- A Gradle dependency change is vibe-dangerous. A human reads the diff before merge. Test-as-spec: show the current resolved version, then the patched version, on the classpath that actually contains the jar.
- First-seen rule: a newly chosen version must already be on Maven Central and at least 30 days old. `com.google.guava:guava:33.8.0-jre` was published 2026-11-02. Do not use it. Use `33.7.2-jre`.
- Do not bump Kotlin to `2.4.20-Beta1`.
- Do not enable Dependabot auto-merge. `scripts/ci-vibe-dangerous-check.sh` skips the calibration check on `dependabot/*` branches. A human still merges.
- Do not add `resolutionStrategy.force` for a library that is already on a patched version on the classpath that contains it.
- Alert snapshot: `gh api --paginate "/repos/weijia-89/toebeans/dependabot/alerts?state=open&per_page=100"` on 2026-11-08. 17 open. 2 critical, 7 high, 8 medium.

## Policy conflict to settle before Tasks 3 and 4

`SECURITY.md` still says a buildscript alert is dismissed as `not_used`, and that this repo does not force plugin transitives. The tree already breaks that sentence: `build.gradle.kts` forces Netty `4.2.19.Final`, Logback `1.5.34`, and BouncyCastle `1.85`.

Tasks 1 and 2 stay inside that existing practice. Task 1 points the settings pin at the Netty version the buildscript already resolves. Task 2 stops `useVersion("6.3.0")` from defeating the `6.4.5` force already in the same file.

Tasks 3 and 4 add new forces, for Guava and FreeMarker. Do those only if you accept amending the "we do not force" paragraph in the same change. The other choice is to dismiss alerts 73 and 74 as buildscript-only and leave the jars at `32.0.1-jre` and `2.3.32`.

## Who reviews what

- Human reads in full before merge: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`.
- The weekly config in `.github/dependabot.yml` is already written. Glance at it before opening a PR. It is reversible by deleting the file.

## Evidence (this session)

| Claim | Tag | Where |
|---|---|---|
| 17 alerts open. Security updates already enabled (`{"enabled":true,"paused":false}`). No `.github/dependabot.yml` on `origin/main`. | verified | GitHub API, 2026-11-08 |
| `:androidApp:releaseRuntimeClasspath` and `:shared:jvmRuntimeClasspath` contain no `io.netty`, no `com.google.guava:guava`, no `org.freemarker:freemarker`. | verified | `/tmp/toebeans-insights/android-*.txt`, `shared-*.txt` |
| Buildscript Netty resolves `4.1.93.Final -> 4.2.19.Final`. Every open Netty advisory's 4.2 range stops at 4.2.16 or 4.2.17. | verified | `/tmp/toebeans-insights/build-env.txt` line 240. Advisory ranges in the alert JSON |
| `settings.gradle.kts` still sets `nettyVersion = "4.1.135.Final"`, which is inside every open 4.1 range (patched line is 4.1.136 or 4.1.137). Project classpaths do not request Netty today. The rule would downgrade a future request. | verified | `settings.gradle.kts` lines 46 and 86-88 |
| `:macrobench` `benchmarkRuntimeClasspath` resolves `com.squareup.wire:wire-runtime:6.3.0`. `useVersion("6.3.0")` wins over `force(...:6.4.5)`. Alert 72 is patched at 6.4.5. Wire is absent from the app and shared runtime classpaths. | verified | `settings.gradle.kts` lines 36 and 73-74. `/tmp/toebeans-insights` macrobench wire insight from the earlier combined run, and the first insight file |
| Buildscript Guava is `com.google.guava:guava:32.0.1-jre` via `com.android.tools:common` under AGP 8.7.0. Alert 74 range is `<= 33.7.1`. Maven Central has `33.7.2-jre` and does not publish a plain `33.7.2`. | verified | `build-env.txt` lines 60-76. `repo1.maven.org` metadata |
| Buildscript FreeMarker is `org.freemarker:freemarker:2.3.32` under the Kover plugin's `intellij-coverage-reporter`. Alert 73 is patched at 2.3.35. That version is on Maven Central (`lastUpdated` 2026-08-27). | verified | `build-env.txt` lines 455-460. Maven metadata |
| Alert 68, Kotlin Gradle plugin 2.0.21, is patched only at `2.4.20-Beta1`. | verified | alert JSON `first_patched_version` |
| `SECURITY.md` said `dependencyCheckAnalyze` runs in CI. `ci.yml` does not. The pull-request gate is `dependency-review`. | verified | `SECURITY.md` before this change. `.github/workflows/ci.yml` lines 157-170 |

Classpath proof commands, from the repo root, with `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`:

```bash
./gradlew :androidApp:dependencyInsight --dependency io.netty --configuration releaseRuntimeClasspath --console=plain
./gradlew :shared:dependencyInsight --dependency io.netty --configuration jvmRuntimeClasspath --console=plain
./gradlew :macrobench:dependencyInsight --dependency wire-runtime --configuration benchmarkRuntimeClasspath --console=plain
./gradlew buildEnvironment --console=plain
```

Run each `dependencyInsight` in its own Gradle invocation. One invocation keeps a single `--dependency` value for every task.

## Findings

DA-1 · Align the Netty pin to the version the buildscript already resolves · SEC · stops a future project dependency from being forced onto 4.1.135, which is inside the open advisory ranges · one-line version change · S1·S2·S3·S4·S5·S6·S7 = 1·1·1·1·1·0.5·1 = 6.5/7 → 95% · falsifier: after the edit, `buildEnvironment` still shows `4.2.19.Final` and a project `dependencyInsight` for `io.netty` stays empty · reads: `settings.gradle.kts:46`, `build.gradle.kts:9-18`, `build-env.txt:240`

DA-2 · Stop the Wire downgrade to 6.3.0 · SEC · macrobench currently resolves a version alert 72 marks vulnerable · version string plus catalog comment · same score shape, S7 from the wire insight showing `6.3.0` · reads: `settings.gradle.kts:36-74`, wire insight

DA-3 · Force buildscript Guava to `33.7.2-jre` · SEC · alert 74 is a real jar on the AGP classpath · force line plus a debug assemble · 6.5/7 → 95% · falsifier: `buildEnvironment` no longer contains `guava:32.0.1-jre` · reads: `build-env.txt:76`

DA-4 · Force buildscript FreeMarker to 2.3.35 · SEC · alert 73 is a real jar on the Kover plugin classpath · force line plus one Kover task · 6.5/7 → 95% · falsifier: `buildEnvironment` shows `freemarker:2.3.35` · reads: `build-env.txt:460`

DA-5 · Leave Kotlin alert 68 open · SEC · the only patched release is a beta · no code · decision, not a version bump · reads: alert 68 `first_patched_version`

Dropped: forcing Netty upward on the buildscript. It is already `4.2.19.Final`.

Dropped: dismissing all 17 as `not_used` the way the 2026-05-18 batch did. Wire, Guava, and FreeMarker resolve inside a vulnerable range. Netty's settings pin is a downgrade trap even though today's project classpaths do not request it.

Deferred: pinning GitHub Actions to commit SHAs. Workflows use floating major tags (`actions/checkout@v4` and the other `uses:` lines in `.github/workflows/`). That is a separate supply-chain change. The new config will open version-update pull requests for those tags after the 30-day cooldown.

Deferred: adding the OWASP `dependencyCheck` plugin. `SECURITY.md` now names the check that actually runs.

---

### Task 1: Netty pin

**Files:**
- Modify: `settings.gradle.kts` (the `nettyVersion` value)

**Interfaces:**
- Consumes: buildscript forces in `build.gradle.kts` already request `4.2.19.Final`
- Produces: `val nettyVersion = "4.2.19.Final"` so `eachDependency` cannot select `4.1.135.Final`

- [ ] **Step 1: Record the current project classpath**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :androidApp:dependencyInsight --dependency io.netty --configuration releaseRuntimeClasspath --console=plain
```

Expected: `No dependencies matching given input were found in configuration ':androidApp:releaseRuntimeClasspath'`

- [ ] **Step 2: Change the pin**

In `settings.gradle.kts`, replace the Netty comment and version with:

```kotlin
// Buildscript classpath already resolves 4.2.19.Final (build.gradle.kts).
// Open 4.2 advisories are patched at 4.2.17.Final or earlier.
// 4.1.135.Final is inside the open 4.1 ranges (patched at 4.1.136 / 4.1.137).
val nettyVersion = "4.2.19.Final"
```

Leave the `build.gradle.kts` force list as it is.

- [ ] **Step 3: Confirm the buildscript did not move**

Run `./gradlew buildEnvironment --console=plain` and search the output for `io.netty:netty-handler:`.

Expected: `4.2.19.Final`. No `4.1.135.Final`.

- [ ] **Step 4: Assemble**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :shared:jvmTest :androidApp:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`

---

### Task 2: Wire downgrade

**Files:**
- Modify: `settings.gradle.kts` (`useVersion("6.3.0")` on `wire-runtime`)
- Modify: `gradle/libs.versions.toml` (`wire = "6.3.0"`)

**Interfaces:**
- Consumes: `val wireRuntimeCoordinate = "com.squareup.wire:wire-runtime:6.4.5"`
- Produces: resolved `com.squareup.wire:wire-runtime:6.4.5` on `:macrobench` `benchmarkRuntimeClasspath`

- [ ] **Step 1: Record the failing resolution**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :macrobench:dependencyInsight --dependency wire-runtime --configuration benchmarkRuntimeClasspath --console=plain
```

Expected today: `com.squareup.wire:wire-runtime:6.3.0` and `Selected by rule`.

- [ ] **Step 2: Make the rule match the force**

In the `com.squareup.wire` branch, replace:

```kotlin
"wire-runtime" -> {
    useVersion("6.3.0")
    because("CVE-2026-45799")
}
```

with:

```kotlin
"wire-runtime" -> {
    useVersion("6.4.5")
    because("CVE-2026-45799 and CVE-2026-63126")
}
```

In `gradle/libs.versions.toml`, set `wire = "6.4.5"`. Keep the comment that the coordinate is forced from `settings.gradle.kts`.

- [ ] **Step 3: Re-run the insight**

Same command as Step 1.

Expected: `com.squareup.wire:wire-runtime:6.4.5`. The app release classpath still has no `wire-runtime`.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :androidApp:dependencyInsight --dependency wire-runtime --configuration releaseRuntimeClasspath --console=plain
```

Expected: no matching dependencies.

---

### Task 3: Guava on the buildscript classpath

**Files:**
- Modify: `build.gradle.kts` (inside the existing `buildscript` `resolutionStrategy.force` list)

**Interfaces:**
- Consumes: AGP 8.7.0 pulling `com.google.guava:guava:32.0.1-jre`
- Produces: `com.google.guava:guava:33.7.2-jre` on `buildEnvironment`

`33.7.2` with no suffix is not on Maven Central. The JRE line that matches the current jar is `33.7.2-jre`.

- [ ] **Step 1: Add the force next to the existing BouncyCastle forces**

```kotlin
force("com.google.guava:guava:33.7.2-jre")
```

- [ ] **Step 2: Confirm the graph**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew buildEnvironment --console=plain
```

Expected: `guava:32.0.1-jre -> 33.7.2-jre` or a direct `33.7.2-jre`. No remaining `32.0.1-jre` as the selected version.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :androidApp:dependencyInsight --dependency guava --configuration releaseRuntimeClasspath --console=plain
```

Expected: still no `com.google.guava:guava` on the release classpath. `listenablefuture` may still appear. That artifact is not alert 74.

- [ ] **Step 3: Assemble**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :androidApp:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`. If resolution fails, stop. Do not widen the force to `33.8.0-jre`.

---

### Task 4: FreeMarker on the Kover plugin classpath

**Files:**
- Modify: `build.gradle.kts` (same `buildscript` `resolutionStrategy.force` list)

**Interfaces:**
- Consumes: `org.jetbrains.kotlinx:kover-gradle-plugin:0.8.3` → `org.freemarker:freemarker:2.3.32`
- Produces: `org.freemarker:freemarker:2.3.35` on `buildEnvironment`

- [ ] **Step 1: Add the force**

```kotlin
force("org.freemarker:freemarker:2.3.35")
```

- [ ] **Step 2: Confirm the graph and run Kover**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew buildEnvironment --console=plain
```

Expected: `freemarker:2.3.32 -> 2.3.35`.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :shared:koverVerify --console=plain
```

Expected: `BUILD SUCCESSFUL`. If the coverage HTML renderer fails on 2.3.35, stop and report the stack trace. Do not dismiss alert 73 to get past that failure.

---

### Task 5: Kotlin alert 68

**Files:**
- Modify: `docs/security/dependabot-triage-2026-11-08.md` (new, short)

**Interfaces:**
- Consumes: alert 68, GHSA-r937-wjx7-w2jp, patched version `2.4.20-Beta1`
- Produces: a written decision a later session can find

- [ ] **Step 1: Write the decision**

Create `docs/security/dependabot-triage-2026-11-08.md` with the alert id, the resolved coordinate `org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21`, the advisory summary (unsafe deserialization of Kotlin build-cache metadata), and this decision: wait for a stable release at or above 2.4.20. A beta compiler is the wrong trade for a bug that needs a poisoned build cache on the maintainer machine.

Leave the GitHub alert open.

- [ ] **Step 2: Do not edit `gradle/libs.versions.toml` `kotlin`**

The catalog stays `kotlin = "2.0.21"`.

---

### Task 6: Re-query alerts

- [ ] **Step 1: After Tasks 1-4 are merged, list open alerts again**

```bash
gh api --paginate "/repos/weijia-89/toebeans/dependabot/alerts?state=open&per_page=100"
```

Expected direction: alerts 58-67 and 69-74 can close on the next Dependabot scan once the manifest versions it reads sit outside the ranges. Alert 68 stays open.

If a Netty, Wire, Guava, or FreeMarker alert stays open after a scan and `buildEnvironment` shows the patched coordinate, dismiss that one alert with `gh api -X PATCH` using `dismissed_reason=tolerable_risk` and a comment that points at this plan and the resolved coordinate. Do that one alert at a time. Do not dismiss alert 68 that way. It stays open on purpose.

---

### Task 7: Weekly config (already in the tree)

**Files:**
- Create: `.github/dependabot.yml` (done in the session that wrote this plan)
- Modify: `SECURITY.md` (done in that same session)

The file schedules Gradle and GitHub Actions weekly, waits 30 days before a version-update pull request, caps open version-update pull requests at 5, and groups minor and patch updates. Major updates stay as their own pull requests because they do not match the group.

This does not patch the 17 open alerts. Those need Tasks 1-5. Security update pull requests were already enabled before this file existed (merged PRs 92, 101, 102, 104).

- [ ] **Step 1: Parse the YAML**

```bash
ruby -ryaml -e 'YAML.load_file(".github/dependabot.yml"); puts "ok"'
```

Expected: `ok`
