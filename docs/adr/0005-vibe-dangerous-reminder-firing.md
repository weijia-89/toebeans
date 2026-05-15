# ADR-0005: Reminder-firing path classified as vibe-dangerous

Date: 2026-05-14
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

The `code-helper` skill defines four vibe-safety tiers (vibe-safe, vibe-careful, vibe-dangerous, pure-refactor). A reminder-firing bug can directly affect an animal's medication adherence; missing or duplicating doses for chronic conditions like CKD, diabetes, or seizure disorders can be harmful.

Per `code-helper` Section 1: vibe-dangerous classification is required for "auth, payments, data migrations, security-sensitive logic." Medication-critical reminder firing is the same category of impact (health/safety) and gets the same treatment.

## Decision

The following surfaces are classified **vibe-dangerous**:

- `shared/src/commonMain/kotlin/app/toebeans/core/scheduler/`
- `shared/src/commonMain/kotlin/app/toebeans/core/backup/`
- `androidApp/src/main/kotlin/app/toebeans/android/notifications/`
- `shared/src/commonMain/sqldelight/` migrations
- `androidApp/src/main/AndroidManifest.xml` permission additions
- Any Gradle dependency addition or upgrade

For every change to any of these surfaces:

1. **Test-as-spec.** Write the failing test FIRST. Commit it. Open a PR with only the test.
2. **Human review of the test signature** — the test is itself a spec.
3. **Implement** in a separate PR.
4. **Mutation testing** (pitest) is required to pass at ≥80% on the touched code.
5. **Confidence score** ≥95 per `code-helper` §5, with per-component minima: Test ≥90, Hallucination ≥90, Adversarial ≥85, Reversibility ≥90.
6. **Logged** to `.codeit/calibration.jsonl`.

## Consequences

### Positive

- A failing-test PR exists before any implementation begins. The first such PR is the one that introduces `SchedulePhaseRulesTest`.
- Human review of every scheduler/backup/notification change.
- Mutation testing surfaces lazy assertions and missed branches.

### Negative

- Slower iteration on these surfaces. Acceptable: this is the explicit posture from the start.
- Mutation testing is computationally expensive. Mitigation: it runs only on the vibe-dangerous paths, not the whole codebase.

## Verification

- AGENTS.md and CLAUDE.md both encode this contract.
- The CI workflow `scripts/agents_claude_parity.sh` ensures the two files do not drift.
- A pre-commit hook (milestone-1 work item) enforces that any commit touching a vibe-dangerous path must be accompanied by a `tests/` change or have a `Vibe-Override:` trailer signed by a human reviewer.
