# codeit engagement summary — toebeans v0.1 scaffold

**Engagement type:** new-app
**Date:** 2026-05-14 / 2026-05-15
**Operator:** Wei Jia (with Cascade)
**Headline confidence (feasibility dossier):** 87/100 (vibe-careful tier)

## What ran

1. **discovery** — greenfield, no prior code. Project name `toebeans` at `/Users/wjia/Projects/toebeans`.
2. **planning** — `code-helper plan-new-app` output → design doc + 5 ADRs.
3. **scoring** — feasibility dossier scored 87/100 (above the vibe-careful floor of 80; below vibe-dangerous floor of 95, which is appropriate for a feasibility report).
4. **adversarial** — top-3 falsifiers run; mitigations recorded.
5. **doc-pass** — README, AGENTS, CLAUDE, SECURITY, CHANGELOG, ARCHITECTURE, 5 ADRs.
6. **summary** — this file.

## What did NOT run (and why)

- **deAI-sweep** — skipped at v0.1 because the docs have not been re-edited by a generation pass yet; the voice was authored under operator guidance and stays inside the per-archetype voice rules. To be run before the v0.1.0 release tag.
- **launch-ready** — not applicable to a `new-app` engagement; runs on `harden` engagements.

## Artifacts

- `research/00-feasibility-dossier.md`
- `docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md`
- `docs/adr/0001-kmp-and-compose-multiplatform.md`
- `docs/adr/0002-alarmmanager-workmanager-hybrid.md`
- `docs/adr/0003-local-first-no-cloud.md`
- `docs/adr/0004-tapering-schedule-model.md`
- `docs/adr/0005-vibe-dangerous-reminder-firing.md`
- `AGENTS.md` / `CLAUDE.md` (kept in parity by CI fitness function)
- `SECURITY.md`
- `.github/workflows/ci.yml` with 5 fitness-function gates
- `shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt` — the failing test-as-spec

## Score detail (feasibility dossier)

See `research/00-feasibility-dossier.md` §9 for the full table. Headline 87/100.

## Next-decision review (mandatory before next agent step)

Per the design doc §13 and AGENTS.md test-as-spec rules, the **scheduler implementation cannot proceed without a human review** of:

1. `SchedulePhaseRulesTest.kt` — does the human reviewer agree this test is what taper correctness means?
2. The 5 ADRs — any disagreement with the architecture decisions?
3. The 5 fitness functions — any additional gate that should be in CI before week 2?

The next codeit phase to run is a **review** engagement on the implementation PR (when it lands), not a re-run of the new-app phases.

## Idempotency note

Re-running `codeit --engagement-type new-app` on this project should produce identical state.jsonl rows unless inputs change. If a re-run produces different rows, that is a bug in the engagement, not a feature.
