# ADR-0017: Lessons from the ADR-0016 CI iteration. Reorder the local gauntlet and detect duplicate test-private class names.

Date: 2026-05-18
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

ADR-0016 landed in commit `4ee14a2` (`feat: ADR-0016 backup/import (plain JSON, merge-by-id) [vibe-dangerous]`). The implementing PR shipped clean to a local pre-commit hook that gated on `.codeit/calibration.jsonl` discipline but did not run the full CI gauntlet locally. The host that produced the commit has no JDK17 at the time of writing, so `ktlintCheck`, `detekt`, `compileTestKotlinJvm`, and `jvmTest` are all CI-gated for this work. The first CI run, `26037820204`, failed.

The cleanup that followed required four CI iterations before all five jobs went green. The iterations were:

- **`4ee14a2` to `45abc64` (lint fallout cleanup).** CI run `26037820204` failed at the Lint stage with 12 detekt-weighted findings plus roughly 30 ktlint findings. No tests ran because lint failed first. The cleanup commit:
    - Added `@Suppress("TooGenericExceptionCaught")` with a rationale comment on four catch sites in `ExportBackupViewModel` and `ImportBackupViewModel` (the sites fan in IO from a caller-supplied lambda and from internal pipelines).
    - Switched `Throwable` to `Exception` at the same catch sites so `OutOfMemoryError` and `StackOverflowError` continue to bubble per detekt's guidance.
    - Added `@Suppress("CyclomaticComplexMethod")` with a rationale comment on the `SettingsScreen` composable (the composable's branch count comes from rendering fan-out across visible cards).
    - Reformatted lambda bodies in `ExportBackupViewModelTest`, `ImportBackupViewModelTest`, and `BackupImporterTest` to satisfy ktlint defaults.
    - Removed one unused `kotlinx.datetime.LocalTime` import.
- **`45abc64` to `c369cbe` (residual detekt finding).** CI run `26039077567` surfaced one finding that the first 12 weighted findings had masked: `SettingsScreen.kt:121 UseCheckOrError`, in the Storage Access Framework launcher's null-fallback. Fixed by swapping `throw IllegalStateException(msg)` to `error(msg)`, a one-line change with the same exception type and the idiomatic stdlib form.
- **`c369cbe` to `fa61a40` (K2 compile collision).** CI run `26039386098` reached the Gradle compile step for the first time in this iteration and surfaced 24 `Cannot access ... private in file` errors in `BackupImporterTest.kt`. Root cause: both `BackupImporterTest.kt` and `BackupAggregatorTest.kt` (both landed in `4ee14a2`) declared four top-level `private class` fakes with identical names (`InMemoryPetRepo` and three siblings for the medication, schedule, and dose-event repositories) in the same package `app.toebeans.core.backup`. The K2 compiler rejects the duplicate-private-class-name pattern even though file-level `private` is documented as file-local. Fixed by renaming the four fakes in `BackupImporterTest.kt` to `ImporterPetRepo`, `ImporterMedRepo`, `ImporterScheduleRepo`, and `ImporterDoseEventRepo`. The aggregator-test set kept its `InMemory*Repo` naming because it was the first occupant of the namespace.
- **`fa61a40`.** CI run `26039809143` went green across all five jobs.

Commit `841b60e` (ignore `.worktrees/` for parallel-agent work) is unrelated tooling and is the base for this ADR's branch.

Two distinct lessons surfaced. The handoff document for the previous session inferred that once the Lint stage passed in CI, the rest of the gauntlet would go green. That inference was speculative. The actual gauntlet took three more iterations after lint started passing, and each iteration burned a CI cycle (roughly 3 to 5 minutes per run plus context-switch time). A single local cycle of `ktlintCheck + detekt + compileTestKotlinJvm + jvmTest` would have been longer in wall-clock time than any one CI run but would have caught all three downstream failures in a single iteration.

## Decision

This ADR records two lessons from the ADR-0016 CI iteration and proposes one remediation per lesson. **Neither remediation is implemented in this commit.** Both are proposals scoped to follow-on work, sized so that a future session (or a human reviewer) can decide whether to act on them.

### Lesson 1: Lint short-circuits Gradle compile

When the Lint stage fails in CI, the Gradle compile step never runs, so latent compile errors stay invisible until lint passes. The ADR-0016 iteration demonstrated this directly: the K2 file-private collision was latent in `4ee14a2`, but did not surface in CI until `c369cbe` cleared the last weighted detekt finding two commits later.

The corollary is a posture rule about handoff documents. A handoff that says "lint passed; CI will go green next" is making an inference from one stage to all later stages. That inference is unsafe whenever the diff touches surfaces the local environment has not actually compiled. For toebeans specifically, the local environment lacks JDK17, so all compile-test verification flows through CI and is gated behind lint.

**Proposed remediation (NOT implemented here):** when JDK17 becomes available locally, the pre-push gauntlet should run lint and compile-tests as **separate, both-mandatory** steps. Failure in lint should not skip compile-tests; a single push that fails one gate should still surface the other gate's status so the next iteration starts from the full picture. The CI workflow itself is harder to reorder because the Lint stage is currently the cheapest gate and the GitHub Actions runner has a finite parallelism budget; reordering CI would trade per-run cost for fewer-iteration runs without changing the local handoff story. The cheaper place to apply this lesson is in the local gauntlet.

Concretely, the change belongs in `CONTRIBUTING.md` (a new file) or as an addendum to `AGENTS.md § Test-as-spec rules`. The shape of the addition:

> When JDK17 is available locally, the pre-push gauntlet runs lint and compile-tests as independent gates. Failure in lint does not skip compile-tests; a single push that fails one gate still surfaces the other gate's status so the next iteration starts from the full picture. Until JDK17 is available, CI is the only compile-tests gate, and a push that fails at the Lint stage makes no claim about whether compile-tests pass.

Neither `CONTRIBUTING.md` nor `AGENTS.md` is touched by this ADR. Both edits live in the Followups section below as proposals for a future session.

### Lesson 2: Top-level `private class` with shared names in the same package collides under K2

The Kotlin language reference describes file-level `private` visibility as file-local. The K2 compiler nevertheless emits `Cannot access ... private in file` on every usage site when two files in the same package declare top-level private classes with identical names. The error fires regardless of whether the usage site is in the declaring file or another file in the same package. Under K2, the visibility rule appears to operate on the class name at resolution time, where the documentation describes a file-based access-time model.

Two files exhibited the pattern in `4ee14a2`:

- `shared/src/commonTest/kotlin/app/toebeans/core/backup/BackupAggregatorTest.kt` declared `InMemoryPetRepo`, `InMemoryMedRepo`, `InMemoryScheduleRepo`, and `InMemoryDoseEventRepo` as top-level `private class`.
- `shared/src/commonTest/kotlin/app/toebeans/core/backup/BackupImporterTest.kt` declared the same four class names with the same `private class` modifier in the same package.

Each file used its own fakes; no cross-file usage was intended or written. K2 nevertheless reported all 24 usage sites in `BackupImporterTest.kt` as "Cannot access ... private in file." The fix in `fa61a40` renamed the four fakes in `BackupImporterTest.kt` to the `Importer*Repo` prefix.

This class of error is mechanically detectable. Collect every top-level `private class` declaration across the test source roots (the three that exist today are `shared/src/commonTest/`, `shared/src/jvmTest/`, and `androidApp/src/test/`), group by Kotlin package, and fail if any class name appears in more than one file within the same package.

**Proposed remediation (NOT implemented here):** a new fitness function at `scripts/test_no_duplicate_private_test_class_names.sh`, modeled on the existing `scripts/test_no_pii_in_crash_log.sh` shape (`set -euo pipefail`, per-violation error message with file paths, exit 1 on any violation). The sketch:

```bash
#!/usr/bin/env bash
# Fitness function (proposed). No two test files in the same Kotlin package
# may declare top-level `private class <Name>` with the same <Name>. Under K2,
# the pattern emits "Cannot access ... private in file" on every usage site
# even though file-level `private` is documented as file-local.
#
# Empirical reference: ADR-0017 § Lesson 2; commits 4ee14a2 and fa61a40;
# CI run 26039386098.

set -euo pipefail
ROOT="${1:-.}"

TEST_ROOTS=(
    "$ROOT/shared/src/commonTest"
    "$ROOT/shared/src/jvmTest"
    "$ROOT/androidApp/src/test"
)

# Implementation sketch (this script is NOT authored in ADR-0017):
#
#   1. find *.kt under each TEST_ROOTS entry.
#   2. extract the `package <fqn>` line from each file.
#   3. grep `^private class \w+` and capture <classname>.
#   4. emit one line per hit as "<fqn>\t<classname>\t<path>".
#   5. sort and detect duplicate (fqn, classname) tuples.
#   6. on any duplicate, print all participating file paths and exit 1.
#   7. include the AGENTS.md § Followups link in the failure message so the
#      reader knows how to resolve (rename to <TestName><Role>... convention,
#      or restructure the duplicate declarations).
```

The script above is a sketch. `scripts/test_*.sh` is in the project's vibe-careful-by-coordination set: its output gates CI, so a bug here would either be theater that passes broken builds or a false-positive that blocks legitimate work. Authoring the full script belongs in its own session with negative test cases (`scripts/test_pre_commit_hook.sh`-style fixtures that verify the script fires on the `4ee14a2` shape and passes on the `fa61a40` shape).

**Alternative remediation (lower-cost):** adopt a naming convention for test-internal fakes. Name each top-level `private class` in a test file with a prefix tied to the containing test file, in the shape `<TestName><Role>Repo`. The aggregator-test fakes would become `Aggregator*Repo` under this convention; the importer-test fakes are already `Importer*Repo` per the `fa61a40` fix. The convention is honor-system and relies on every future test author remembering it. A reviewer reads the fakes' names and applies the convention; CI does not enforce it.

The fitness function and the naming convention are not mutually exclusive. The convention can land first as a single-bullet addition to `AGENTS.md § Test-as-spec rules` at zero CI cost and zero script-maintenance overhead. The fitness function can land later if and when the pattern recurs despite the convention.

## Consequences

### Positive

- The lessons are captured in-tree where a future agent (or a future Wei) can find them by searching for "Cannot access ... private in file" or for ADR-0016. Session memory does not survive across agents or across long pauses; this ADR is the persistence surface.
- The K2-private-collision finding is documented as the actual compiler behavior, with a falsifiable claim (the error fires for top-level private classes sharing a name in the same package, regardless of intended usage). Anyone hitting the same error has a working hypothesis to test.
- The proposed remediations are sized small enough to defer. Neither requires a same-session implementation. Both have a clear next-step owner (Wei or the next agent to pick up the work).
- The Lesson 2 alternative (naming convention) is itself sized for a single-line `AGENTS.md` change, which is cheap enough that it can land in the next session that touches `AGENTS.md` for any other reason.

### Negative

- Two proposed remediations that are not yet implemented count as carried-over work. If neither lands, the next ADR-0016-class iteration burns the same CI cycles. Mitigation: the Followups section below names the artifacts to create so they are discoverable in a single grep.
- The fitness-function sketch in Lesson 2 is not byte-for-byte runnable. A reader who copies it expecting a drop-in script will be disappointed. The sketch is deliberately incomplete because authoring the full script in this commit would violate the scope constraint that this ADR not touch `scripts/`.
- The K2-behavior claim ("the rule appears to be name-based at resolution time") is empirical, derived from one collision incident. It is not a Kotlin-language-team statement. A future K2 release may relax the behavior. If that happens, the fitness function becomes redundant and the convention becomes optional. The ADR's Revisit conditions cover this.

### Rejected alternatives

- **Implement both remediations in this commit.** Rejected as out-of-scope. The worktree this ADR is authored in is scoped to `docs/adr/`. The proposed remediations would require edits to `AGENTS.md`, a new `CONTRIBUTING.md`, and a new fitness function under `scripts/`. Coordinating those edits with the ADR mixes vibe-careful documentation work with vibe-careful-by-coordination fitness-function authorship; the two belong in separate sessions with separate review surfaces.
- **Author the lessons as an Amendment block inside ADR-0016.** Rejected: ADR-0016 records the encryption-deferred decision. CI-process lessons learned from its implementation belong in their own ADR; folding them into ADR-0016 muddies that ADR's scope and forces every future reader of the encryption decision to scroll past CI-iteration history.
- **Skip the ADR; rely on session memory.** Rejected: the next K2-private-collision incident may be six months out, by which time session memory has rotated through countless other tasks and has no reliable anchor for the diagnosis.
- **A single combined fitness function that checks both lint-short-circuit and private-collision patterns.** Rejected: the two lessons are independent. Conflating them creates a script with two unrelated failure modes that share an exit code, which works against the "one fitness function, one property" shape that the existing `scripts/test_*.sh` set follows.

## Followups

These artifacts are proposed by this ADR but NOT created here. Each one belongs in its own session.

- **`CONTRIBUTING.md` (new file) or `AGENTS.md § Test-as-spec rules` addendum.** The local-gauntlet ordering paragraph in Lesson 1 above. Suggested home: `CONTRIBUTING.md § Pre-push gauntlet` if `CONTRIBUTING.md` is created, or a new sub-bullet under `AGENTS.md § Test-as-spec rules`.
- **`scripts/test_no_duplicate_private_test_class_names.sh`.** The fitness function sketched in Lesson 2, plus the `scripts/test_pre_commit_hook.sh`-style negative test cases that verify it fires on the `4ee14a2` shape and passes on the `fa61a40` shape. The CI workflow `.github/workflows/ci.yml` would also need a job entry to invoke the new script; that integration is part of the same follow-on session.
- **Naming-convention note** (lower-cost alternative). A one-bullet addition to `AGENTS.md § Test-as-spec rules` stating that top-level `private class` declarations in test files are named with a prefix tied to the containing test file, in the shape `<TestName><Role>...`, to avoid the K2 collision class.

## Revisit conditions

This ADR moves to **Superseded** if any of the following:

1. A future K2 release relaxes the duplicate-top-level-private-class-name behavior. The fitness function (if landed) becomes redundant; the naming convention (if landed) becomes optional. Verify by reproducing the `4ee14a2` shape against the new compiler and confirming the error no longer fires.
2. The fitness function in Lesson 2 lands and the naming convention is dropped (or vice versa). The ADR is amended to reference the actual landed remediation.
3. The local gauntlet ordering in Lesson 1 becomes enforceable in CI itself (for example, GitHub Actions adds a fan-out option that runs lint and compile-tests as independent gates in the same workflow without the current parallelism-budget cost). The Lesson 1 remediation moves from local-only to CI-wide.

## References

- ADR-0016 (`docs/adr/0016-plain-json-backup-encryption-deferred.md`). The work whose CI iteration produced these lessons.
- ADR-0014 (`docs/adr/0014-pre-commit-hook-score-floor-enforcement.md`). Sibling ADR on pre-commit-vs-CI gating discipline.
- ADR-0009 (`docs/adr/0009-local-crash-log-no-telemetry.md`). Reference for the fitness-function script shape.
- Commit `4ee14a2`. `feat: ADR-0016 backup/import (plain JSON, merge-by-id) [vibe-dangerous]`. The implementing PR that landed with the lint and K2 issues.
- Commit `45abc64`. `fix(lint): ADR-0016 fallout, detekt and ktlint cleanup [vibe-careful]`. First cleanup pass.
- Commit `c369cbe`. `fix(lint): use error() instead of throw IllegalStateException [vibe-safe]`. The residual `UseCheckOrError` fix.
- Commit `fa61a40`. `fix(test): rename BackupImporterTest fakes to disambiguate [vibe-careful]`. The K2 collision fix.
- CI runs `26037820204`, `26039077567`, `26039386098`, and `26039809143`. The four gauntlet iterations.
- `scripts/test_no_pii_in_crash_log.sh`. Reference shape for the proposed fitness function in Lesson 2.
- `AGENTS.md § Fitness functions (enforced in CI)`. Where the proposed Lesson 2 fitness function would be enumerated if it lands.
