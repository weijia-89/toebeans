# CLAUDE.md

This file MUST stay in sync with [`AGENTS.md`](AGENTS.md). The CI fitness-function `scripts/agents_claude_parity.sh` enforces parity by SHA-256 hash of the canonicalized content of `AGENTS.md` (whitespace-collapsed) against the canonicalized content of this file.

The reason both files exist: different AI coding tools look in different default locations. Cursor and Codex CLI look for `AGENTS.md`. Claude Code looks for `CLAUDE.md`. We do not want to choose, so we duplicate intentionally and CI-enforce parity.

---

The full contract begins on the next line; everything below this divider is a verbatim copy of `AGENTS.md` from the line after its own divider. Update both files in the same commit.

---

## Posture

- **Anti-enterprise default.** Boring tech. Modular monolith (KMP shared + Android app). No microservices, no event bus, no k8s. Adding any of these requires a forcing-constraint ADR per [`code-helper` §3](https://github.com/wei/code-helper.skill).
- **Local-only.** No network calls from v1 code. No analytics. No crash telemetry. No "phone home." A fitness function (`scripts/test_no_network.sh`) fails the build if a network library appears in the dependency graph or source tree.
- **No AI features.** No symptom checker. No diagnostic content. No treatment recommendations. This is a **vibe-impossible** refusal — not a deferred feature.

## Vibe-safety tiers (per [`code-helper` §5](https://github.com/wei/code-helper.skill))

| Surface | Tier | Confidence floor | Human review required? |
|---|---|---|---|
| `shared/src/commonMain/kotlin/app/toebeans/core/scheduler/` | **vibe-dangerous** | ≥95 | YES, every change |
| `shared/src/commonMain/kotlin/app/toebeans/core/backup/` | **vibe-dangerous** | ≥95 | YES, every change |
| `androidApp/src/main/kotlin/app/toebeans/android/notifications/` | **vibe-dangerous** | ≥95 | YES, every change |
| SQLDelight schema (`shared/.../sqldelight/`) migrations | **vibe-dangerous** | ≥95 | YES, every change |
| `androidApp/src/main/AndroidManifest.xml` permission additions | **vibe-dangerous** | ≥95 | YES, every change |
| Gradle dependency additions (any `implementation`/`api`/`testImplementation`) | **vibe-dangerous** | ≥95 | YES, every change |
| `androidApp/src/main/kotlin/app/toebeans/android/ui/` Compose screens (no scheduler logic) | **vibe-safe** | ≥80 | no (CI gates only) |
| String resources, themes, icons | **vibe-safe** | ≥80 | no |
| ADRs in `docs/adr/` | **vibe-careful** | ≥90 | yes at merge |
| README / ARCHITECTURE / CHANGELOG updates | **vibe-careful** | ≥90 | yes at merge |

**Vibe-impossible** (refuse and escalate):

- Adding any AI symptom checker, AI-generated diagnostic content, AI-derived drug interaction warning, AI-derived treatment recommendation, or "is this dose safe?" feature.
- Adding any LLM-generated or LLM-paraphrased vet-recommended content (the carve-out boundary defined by [ADR-0019](docs/adr/0019-vet-recommended-content-framework.md)). Vet-curated, owner-entered, or vet-record-imported content delivered rule-based under ADR-0019's four conjunctive constraints (source, delivery, framing, refusal scope) is permitted and is NOT vibe-impossible.
- Pre-generating DoseEvents for an entire phase (write-storm anti-pattern; the design materializes lazily at 72h horizon).
- Removing the AlarmManager fallback in favor of WorkManager-only.
- Adding any network library or analytics SDK in v1.
- Adding any permission outside the AndroidManifest allowlist (see below).

## AndroidManifest permission allowlist (v1)

Only these permissions may appear. Any addition requires an ADR and a human reviewer.

- `android.permission.POST_NOTIFICATIONS`
- `android.permission.SCHEDULE_EXACT_ALARM`
- `android.permission.USE_EXACT_ALARM`
- `android.permission.RECEIVE_BOOT_COMPLETED`

## Test-as-spec rules

For any change to a **vibe-dangerous** surface:

1. Write the failing test FIRST. Commit it. Open a PR with only the test.
2. Get human review of the test signature + assertions.
3. THEN implement.
4. Re-run. Confirm it passes. Run mutation tests on the touched code (`pitest`).
5. Score the change per [`code-helper` §5](https://github.com/wei/code-helper.skill). Log to `.codeit/calibration.jsonl`.

The first failing test (taper correctness for the scheduler) lives at `shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt`.

## Review gates (require human approval)

- Any change to a vibe-dangerous surface (see table).
- Any change that adds, removes, or upgrades a Gradle dependency.
- Any change to `.github/workflows/`.
- Any change to `AGENTS.md` or `CLAUDE.md` themselves.

## Confidence-score rule

Apply [`code-helper` §5](https://github.com/wei/code-helper.skill) to every change. Components:

| # | Component | Weight | Notes for toebeans |
|---|---|---|---|
| 1 | Code-read depth | 15 | Every changed file end-to-end + every direct caller |
| 2 | Test verification | 20 | Tests run + assertion density met + mutation score ≥ tier-target |
| 3 | Hallucination check | 15 | Every dep verified against Maven Central + first-seen ≥30d |
| 4 | Bug-class coverage | 12 | CWE Top-25 + applicable OWASP Top 10 (especially A05 for permission misuse) |
| 5 | Adversarial pass | 10 | ≥3 weakest assumptions resolved with falsifiers |
| 6 | Reversibility | 8 | Irreversible ops gated (DB migration, permission add, manifest change) |
| 7 | Doc accuracy | 8 | AGENTS.md / README / ADRs / fitness functions match new state |
| 8 | Blast radius | 7 | Scoped via `grep` over `import` graph |
| 9 | Threat model | 5 | STRIDE applied to changed surface |

Log every scored change to `.codeit/calibration.jsonl`. After ~50 entries, recalibrate tier thresholds based on score-vs-incident correlation.

## Fitness functions (enforced in CI)

See `.github/workflows/ci.yml`. The six gates:

1. **No-network** — fail if any class in `commonMain/` or `androidMain/` imports a network library.
2. **No-analytics** — fail if Firebase, GA, Mixpanel, Segment, etc. appear in the Gradle classpath.
3. **Scheduler purity** — fail if any function in `shared/.../scheduler/` references a platform clock other than the injected `Clock`.
4. **Permission whitelist** — fail if AndroidManifest declares a permission not on the allowlist above.
5. **No-PII-in-crash-log** — fail if `LocalCrashLog.kt` references any domain model, repository, DAO, or persistence symbol (per ADR-0009). Enforces the no-PII claim mechanically rather than by reviewer attention.
6. **Scheduler test coverage** — fail if `shared/.../scheduler/` line coverage < 85% (Kover-enforced).

## Hallucination vigilance

Per [`code-helper` §1](https://github.com/wei/code-helper.skill) and Spracklen et al. (USENIX 2025), LLMs hallucinate package names at 5–22% rates. Before adding any import:

- Verify the package exists in Maven Central or Google's Maven repo.
- Verify the author/group is the expected one (not a typosquat).
- Verify first-seen date is ≥30 days ago (defends against slopsquatting).

## Refusal list (will not produce without justification)

- Microservices, k8s, service mesh, event bus, CQRS.
- A framework off the explicit stack list in [`docs/adr/0001-kmp-and-compose-multiplatform.md`](docs/adr/0001-kmp-and-compose-multiplatform.md).
- More than 3 layers of abstraction at project start.
- Banned-vocab: "enterprise", "scalable", "robust", "leverage", "synergy", "world-class". State the concrete property instead (e.g. "p99 < 200ms at 1k RPS", "supports 10,000 pets/user").

## When confidence < tier-floor

1. Re-investigate the lowest-scoring component.
2. Update the plan.
3. Re-score.
4. **No score-bumping without new evidence.**
5. After 2 iterations without crossing the floor, escalate with a gap report.

## Operator wisdom

- The reminder-firing path is **medication-critical**. A bug here can affect a real animal's health. Treat it like you would a medication-dosing bug in a human EHR.
- Owners are not patients in the legal sense (HIPAA does not apply) but the trust posture is the same: assume the owner is in a vulnerable, stressful moment when they open this app.
- "Boring" wins. Choose the technology that has been stable for 2+ years over the technology that was announced last month.
