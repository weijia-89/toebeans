# ADR-0015: Suppress the Kotlin/AGP compatibility warning for AGP 8.7

Date: 2026-05-17
Status: **Accepted**
Deciders: Wei Jia

## Context

Every gradle invocation prints:

```
w: Kotlin Multiplatform <-> Android Gradle Plugin compatibility issue:
The applied Android Gradle Plugin version (8.7.0) is higher than the maximum
known to the Kotlin Gradle Plugin.
Tooling stability in such configuration isn't tested, please report
encountered issues to https://kotl.in/issue

Minimum supported Android Gradle Plugin version: 7.1
Maximum tested Android Gradle Plugin version: 8.5

To suppress this message add 'kotlin.mpp.androidGradlePluginCompatibility.nowarn=true'
to your gradle.properties
```

The warning is **advisory**, not a hard incompatibility. It reflects a release-cadence lag: the Kotlin Gradle Plugin (KGP) version pinned in `libs.versions.toml` was tested against AGP versions up through 8.5; AGP 8.7 (current stable, released August 2024) is two minor versions past that test boundary.

The noise of a warning that fires on every build masks actual warnings that might matter later. Either the warning needs to go away (via suppression or AGP downgrade), or we need to accept the long-term cost of a polluted build output that desensitizes us to real warnings.

## Decision

**Suppress** by adding `kotlin.mpp.androidGradlePluginCompatibility.nowarn=true` to `gradle.properties`.

**Why suppress instead of downgrade AGP to 8.5**:

1. AGP 8.6 and 8.7 ship measurable Compose performance improvements (recomposition profiling, lower baseline-profile build time, faster R8 dexing) that this project benefits from.
2. AGP 8.7 contains security fixes vs 8.5 (dependency CVE patches in the build tooling itself).
3. The "max tested" boundary is the Kotlin team's conservative assertion that they have not run their full test matrix against later AGPs. It is NOT an assertion of known incompatibility.
4. The actual incompatibility surface (KMP-specific source-set wiring, Compose Multiplatform integration) is exercised by every build of this project and every test run. Empirical evidence: 33 commits across the project, no KMP/AGP-related test failure has surfaced.

**Why suppress instead of waiting**:

The KGP-vs-AGP version matrix updates on KGP release cadence (~6-8 weeks). Waiting could mean tolerating the noise for months. Suppression is reversible in one line if a real incompatibility surfaces.

## Empirical evidence supporting "no real incompatibility"

| Signal | Status |
|---|---|
| Full CI gauntlet (ktlintCheck + detekt + jvmTest + testDebugUnitTest + koverVerify + assembleDebug) | ✓ PASS on `6e82e4c` (2026-05-17) |
| KMP-specific tasks (`:shared:jvmTest`, source-set generation, expect/actual wiring) | ✓ All green throughout this session |
| Compose recomposition / R8 / dexing | ✓ Builds successfully, no crash class observed |
| Macrobenchmark module (`:macrobench`) | ✓ Configuration green (separate `com.android.test` module type) |
| AGP changelog 8.6, 8.7 grep for KMP-related breaking changes | None found |

If any of these signals turn red post-suppression, the immediate fix is one-line: delete the suppression property and reopen this ADR.

## Consequences

### Positive

- Build output is clean. Real warnings (deprecations, accessibility issues, etc.) are no longer competing with this noisy banner.
- No version pinning friction. AGP can advance with security patches without revisiting this ADR.
- Reversible in one line.

### Negative

- If a Kotlin-team-discovered incompatibility surfaces *after* a future KGP/AGP release, the suppression silences the warning that would have flagged it. Mitigation: this ADR is cross-referenced in `docs/ROADMAP.md` § "Watch items for M1.2"; the post-M1.2 ADR review revisits it.
- Sets a small precedent for "suppress noisy warnings." Project policy must not extend this beyond well-evidenced cases. Each future suppression requires its own ADR with empirical evidence section like above.

### Rejected alternatives

- **Downgrade AGP to 8.5.** Loses Compose perf improvements and security patches. The "downgrade to clear a noisy warning" pattern is worse than the warning itself.
- **Live with the warning.** Multi-month timeline. Desensitization risk. Real warnings would be missed in the noise.
- **Pin KGP to a version that tests against AGP 8.7+.** Available KGP versions that test against AGP 8.7 are still in preview as of writing; pinning to preview would introduce different stability risks.

## Implementation

Single line added to `gradle.properties`:

```properties
# Suppresses the KGP-vs-AGP-version-matrix warning. See ADR-0015 for rationale +
# empirical evidence + revisit conditions. Delete this line if the post-M1.2 ADR
# review (or any KMP-specific test failure) suggests the underlying incompatibility
# is real rather than the KGP team's conservative "not-yet-tested" disclaimer.
kotlin.mpp.androidGradlePluginCompatibility.nowarn=true
```

Comment placement: under the `# --- Kotlin ---` section to keep gradle.properties organized by surface.

## Revisit conditions

This ADR moves to **Superseded** if any of the following:

1. A real (non-advisory) Kotlin/AGP incompatibility surfaces in CI or local dev. The suppression is removed and the actual incompat is addressed.
2. AGP advances to 9.x or beyond. AGP major-version transitions historically include real KMP plumbing changes; re-evaluate with fresh evidence at that point.
3. KGP advances to a version where the tested-AGP-version-matrix catches up to AGP 8.7+. Suppression becomes redundant; deletion is a no-op.
4. Post-M1.2 ADR review concludes the suppression-precedent risk outweighs the benefit. ADR is reverted with explanation.

## Cross-references

- `gradle.properties` (where the suppression line lives)
- `gradle/libs.versions.toml:13` (`agp = "8.7.0"`)
- 2026-05-17 session calibration entry (under "fix(build, kotlin): suppress KGP/AGP advisory warning per ADR-0015")
- ADR-0008 (`performance-class-target.md`): benefits of AGP 8.6/8.7 Compose perf improvements
