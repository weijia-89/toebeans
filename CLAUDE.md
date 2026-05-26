# AGENTS.md: toebeans agent host contract

This file is the **load-bearing contract** for any AI coding agent (Cursor, Claude Code, Cascade, Copilot, etc.) working on toebeans. It is also the contract a human reviewer enforces in code review.

A copy lives at `CLAUDE.md` for tools that look there first. The two files MUST stay in sync. The CI fitness-function `tests/agents_claude_parity.sh` enforces parity.

---

## Posture

- **Anti-enterprise default.** Boring tech. Modular monolith (KMP shared + Android app). No microservices, no event bus, no k8s. Adding any of these requires a forcing-constraint ADR per [`code-helper` §3](https://github.com/wei/code-helper.skill).
- **Local-only.** No network calls from v1 code. No analytics. No crash telemetry. No "phone home." A fitness function (`scripts/test_no_network.sh`) fails the build if a network library appears in the dependency graph or source tree.
- **No AI features.** No symptom checker. No diagnostic content. No treatment recommendations. This is a **vibe-impossible** refusal, not a deferred feature.

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

### Note on the test_verif component and the local-JDK17 deficit

When `test_verif` scores below the structural maximum (20) because the operator's local environment lacks the JDK toolchain to run the full Robolectric plus Kover gauntlet, name the constraint explicitly in the calibration notes (`test_verif=X because no JDK17 locally; CI is the verification gate`). This is a known environmental headwind, not a discipline failure, but it does mean CI green is the load-bearing verification rather than local green. Eight or more calibration entries through 2026-05-19 cite this exact pattern; future readers should not interpret sub-floor `test_verif` scores as a methodology gap when the named constraint applies.

Source: meta-v1 calibration recalibration report 2026-05-19, Wei directive D4.

Log every scored change to `.codeit/calibration.jsonl`. After ~50 entries, recalibrate tier thresholds based on score-vs-incident correlation.

### Entry types (added 2026-05-20)

Each row in `.codeit/calibration.jsonl` carries a `kind` field. Five values; cap at 7 long-term. New kinds require a `localonly/meta/` justification artefact AND an addendum to this section before they may appear in the ledger.

| `kind` | Use | Schema |
|---|---|---|
| `commit` | Code change that produced a git commit. | Full 9-component rubric. Default when `kind` is missing (lazy migration of pre-2026-05-20 entries). |
| `observation` | Process pattern noticed during work. No score. | Slim: `{date, kind, source_chat, note}` |
| `violation` | Iron-law breach (em-dash slip, wrap-bypass, scope-isolation breach, dispatch-manifest skip, etc.). No score. | Slim: `{date, kind, source_chat, rule, evidence}` |
| `dispatch_event` | Worker dispatched, completed, or cancelled. | Slim: `{date, kind, worker, status, parent_chat}` |
| `recalibration` | Trigger fired (50, 100, 150, ... entries). | Slim: `{date, kind, trigger, entry_count}` |

The recalibration analyzer computes score statistics on `kind == "commit"` rows only. Other kinds are queryable history (e.g. `grep '"kind":"violation"' .codeit/calibration.jsonl`).

Source: meta-v1 process-debt entry-type proposal 2026-05-19, Option C.

## Fitness functions (enforced in CI)

See `.github/workflows/ci.yml`. The six gates:

1. **No-network**: fail if any class in `commonMain/` or `androidMain/` imports a network library.
2. **No-analytics**: fail if Firebase, GA, Mixpanel, Segment, etc. appear in the Gradle classpath.
3. **Scheduler purity**: fail if any function in `shared/.../scheduler/` references a platform clock other than the injected `Clock`.
4. **Permission whitelist**: fail if AndroidManifest declares a permission not on the allowlist above.
5. **No-PII-in-crash-log**: fail if `LocalCrashLog.kt` references any domain model, repository, DAO, or persistence symbol (per ADR-0009). Enforces the no-PII claim mechanically rather than by reviewer attention.
6. **Scheduler test coverage**: fail if `shared/.../scheduler/` line coverage < 85% (Kover-enforced).

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

### When score is below 80

When a calibration entry's `score` is below 80 (any tier; in practice this triggers when vibe-safe drops below floor, e.g., methodology-failure entries), the orchestrator MUST run an adversarial brutally honest review of the plan and the just-shipped work BEFORE the next commit on the same surface. The review names what was actually verified versus claimed, which assumptions were not falsified, and the cheapest fix.

The review lands as a Markdown file under `docs/reviews/YYYY-MM-DD-<short-name>.md`. A follow-on calibration entry scores the review itself. The procedural rule is self-enforced (no pre-commit hook blocks it); the discipline is the orchestrator naming the trigger in-session and producing the review file before continuing.

The form-check skill is the recommended protocol for the adversarial review. The review is empirical-before-opinion, graded-confidence, falsifiable-per-item.

Rationale: a score below 80 indicates the change shipped with severe component-level deficits in adversarial review, hallucination defense, or bug-class coverage. Continuing past such a score without explicit adversarial reconsideration risks compounding the methodology gap. The retroactive B6 / B7 / B8 trio at scores 60 / 58 / 64 are the load-bearing precedent for this rule.

Source: meta-v1 calibration recalibration report 2026-05-19, Wei directive D3-extension.

## Operator wisdom

- The reminder-firing path is **medication-critical**. A bug here can affect a real animal's health. Treat it like you would a medication-dosing bug in a human EHR.
- Owners are not patients in the legal sense (HIPAA does not apply) but the trust posture is the same: assume the owner is in a vulnerable, stressful moment when they open this app.
- "Boring" wins. Choose the technology that has been stable for 2+ years over the technology that was announced last month.

## Cursor Cloud specific instructions

### Environment

- **JDK 17** is at `/usr/lib/jvm/java-17-openjdk-amd64`. `JAVA_HOME` and `ANDROID_HOME=/opt/android-sdk` are exported in `~/.bashrc`.
- **Android SDK** (platform 35, build-tools 35.0.0) is at `/opt/android-sdk`. `local.properties` with `sdk.dir` is auto-generated by the update script.
- **Gradle 8.10** wrapper distribution is cached at `~/.gradle/wrapper/dists/`. Downloaded from GitHub releases (not `services.gradle.org`) due to egress restrictions.

### Network/egress restrictions

The Cloud Agent VM blocks HTTPS to `plugins-artifacts.gradle.org`, `repo1.maven.org`, `maven.google.com`, and most Maven registries. The following are accessible:

| Domain | Purpose |
|--------|---------|
| `repo.maven.apache.org` | Maven Central mirror (dependencies) |
| `dl.google.com` | Google Maven (Android/AndroidX artifacts) |
| `github.com` | Source + Gradle distribution ZIP |
| `services.gradle.org` | Gradle wrapper distribution (redirects to GitHub) |
| `plugins.gradle.org` | Plugin Portal UI (artifact downloads redirect to blocked `plugins-artifacts.gradle.org`) |

Two Gradle init scripts in `~/.gradle/init.d/` handle this:

1. `mirror-repos.gradle.kts` - Redirects plugin and dependency resolution to accessible mirrors. Includes a local file repo (`~/.gradle/local-plugin-repo/`) with a ktlint-gradle stub, plus `gradlePluginPortal()` for other plugins.
2. `robolectric-repo.gradle.kts` - Points Robolectric's runtime JAR fetcher at `repo.maven.apache.org`.

### Running builds and tests

Standard commands per `CONTRIBUTING.md`. Key caveats:

- **ktlintCheck**: Not functional in Cloud Agent. The stub plugin resolves (so the build configures) but does not register tasks. The real ktlint plugin downloads from `plugins-artifacts.gradle.org` which is blocked. Use `detekt` for lint.
- **InMemoryScheduleRepositoryContractTest**: 11 contract cases run green against an in-memory fake in `commonTest` (harness verify path without JDBC). **SqlDelightScheduleRepositoryContractTest** (`jvmTest`) is the SQLDelight + FK cascade regression gate.

### Quick-start commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
./gradlew :shared:jvmTest --console=plain           # Core scheduler + backup tests
./gradlew :androidApp:testDebugUnitTest             # Robolectric Android tests
./gradlew :androidApp:assembleDebug                 # Build debug APK
./gradlew detekt                                    # Lint (detekt only in cloud)
bash scripts/test_no_network.sh .                   # Fitness functions (no Gradle needed)
```
