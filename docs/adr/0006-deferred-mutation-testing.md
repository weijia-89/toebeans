# ADR-0006: Defer pitest mutation testing; use Kover coverage at v0.1

Date: 2026-05-15
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

`AGENTS.md` requires mutation testing for any change to a vibe-dangerous surface, and ADR-0005 specifies pitest as the tool. At v0.1 scaffolding time, two facts collided:

1. The standard pitest Gradle plugin (`info.solidsoft.pitest`) does not integrate cleanly with Kotlin Multiplatform source sets. Pitest expects Java-style `sourceSets` (the legacy accessor), and the KMP plugin's source-set layout is not directly addressable from that accessor. Reproducing the integration manually (custom `mainSourceSets`, `additionalTestSourceSets`) compiles in some Gradle versions and silently produces empty mutation reports in others.
2. The scheduler is currently a stub (`DefaultScheduleCalculator` throws `NotImplementedError`). Any mutation report against an unimplemented function is uninformative. Every mutant survives or every mutant fails identically, depending on how the test catches the throw.

ArcMutate has a commercial extension that handles Kotlin/KMP better, but it is paid and out of scope for a v0.1 OSS scaffold. Stryker-Mutator has experimental Kotlin support but is unstable.

## Decision

For v0.1:

- Apply **Kover** (JetBrains' KMP-native coverage tool) to the `:shared` module.
- Filter coverage reports to `app.toebeans.core.scheduler.*` and `app.toebeans.core.backup.*`. Exclude generated SQLDelight code.
- Set a **`koverVerify` rule with `minValue = 0`** at v0.1. The rule exists so the wiring is testable, not because coverage is meaningful yet.
- Register a `mutationTest` placeholder task that prints a pointer to this ADR.

Once any vibe-dangerous surface has a real implementation (the scheduler will be the first), we raise the verify threshold to 85% line coverage **as a first proxy** for test quality.

True mutation testing returns when one of these happens:

- ArcMutate Cloud's free tier covers our project (re-evaluate quarterly).
- We extract the scheduler into a JVM-only `:scheduler-jvm` subproject that depends on the KMP common code; pitest's standard plugin works fine there.
- Stryker-Kotlin reaches v1.0 stable with KMP support.

## Consequences

### Positive

- Coverage gating is wired in CI from v0.1.
- The `mutationTest` task is discoverable (`./gradlew tasks --group verification`) and self-documents the deferral.
- No silent regression: AGENTS.md and ADR-0005 still demand mutation testing for vibe-dangerous changes; this ADR is the formal record of the deferral and the trigger conditions for revisiting.

### Negative

- We do not have actual mutation testing at v0.1. Line coverage is a strictly weaker signal. It catches "this code never runs" but not "this assertion is too weak."
- A reviewer of a future vibe-dangerous change must consciously evaluate whether the line-coverage threshold is enough, or whether they require an ArcMutate run as a one-off.

### Rejected alternatives

- **Hand-roll a Gradle task that invokes pitest's CLI with explicit classpath.** Rejected as overkill for v0.1; would also produce noisy reports on the stub.
- **Wait until pitest+KMP works natively.** Rejected: no firm date; we would have no coverage gate at all in the meantime.
- **Skip both.** Rejected: AGENTS.md explicitly forbids skipping coverage gates on vibe-dangerous surfaces.

## Verification

- `./gradlew :shared:koverHtmlReport` produces a report at `shared/build/reports/kover/html/`.
- `./gradlew :shared:koverVerify` passes at v0.1 (threshold is 0).
- `./gradlew :shared:mutationTest` prints the placeholder warning and points at this ADR.
- A revisit checklist for this ADR is added to the CHANGELOG under "[Unreleased] > Deferred items."
