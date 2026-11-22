# ADR-0022: FOSS library adoption, inventory and costed options

> Note (2026-11-22): The agent contract files were removed from this public repository. References in this document to those files mean the local working copies kept in a contributor's checkout.

Date: 2026-11-22
Status: Proposed (operator decision pending)
Deciders: Wei Jia (pending)
Tier: vibe-careful (ADR). Any dependency addition this ADR would authorise is vibe-dangerous and needs one ≥95 human-reviewed change per AGENTS.md.

## Scope and method

Research only. No dependency was added, no build file changed, no code changed. One question is answered: does adding free and open-source software (FOSS) libraries buy this repo anything that its current dependencies and bespoke code do not?

Pre-registered prediction: the repo already gets most available FOSS value; every navigation candidate is net-negative at this scale; the correct rank puts do-nothing first. Confidence before retrieval: 0.75. What would change my mind: a candidate with a stable release, a permissive license, drop-in compatibility with compileSdk 35 / Android Gradle Plugin (AGP) 9.5.1 / Kotlin 2.4.21, and no rework of medication-critical tests.

Evidence key: `[S1]` to `[S13]` resolve to the sources section, which records retrieval date and read depth. Local facts cite repo paths, not web sources. This is a single-analyst conclusion; at this stakes level it understates uncertainty. External version and date claims are tagged by source id. Claims about this repo come from files read in full this session.

## Context

The operator asked whether to use FOSS libraries instead of designing everything locally, with pros, cons, and a credible migration estimate. The framing contains a false premise worth naming: toebeans is already a FOSS dependency stack with a small bespoke core. The real choice is not "FOSS versus local". It is "which additional library earns a vibe-dangerous dependency gate and a medication-critical test rewrite".

The relevant posture is fixed by AGENTS.md, CONTRIBUTING.md, and docs/ARCHITECTURE.md:

- Local-only, no network, no analytics, no crash telemetry; CI fitness functions enforce this.
- No AI features; symptom checking, diagnostic content, and treatment advice are vibe-impossible.
- Anti-enterprise, boring tech, modular monolith; the refusal list bans microservices, service mesh, event bus, CQRS, a framework off the ADR-0001 stack list, and more than three abstraction layers at project start.
- The reminder-firing path is medication-critical; a navigation or UI regression can strand an owner who is trying to log a dose.
- Dependency additions require human review at confidence ≥95, Maven Central or Google Maven verification, a ≥30-day first-seen check, a `--refresh-dependencies` compile step, and a calibration entry.
- AGPL-3.0 is the core license (ADR-0020); inbound dependencies must be distribution-compatible with it.

The build was just migrated to Kotlin 2.4.21 and AGP 9.5.1. Four schema and timezone slices merged after it. `settings.gradle.kts` pins several AndroidX artifacts below their catalog versions because newer releases demand a higher compileSdk or AGP. That pin set is the main reason a new library is not a small change here.

## Decision

Adopt nothing new now. Do nothing is the recommended option, ranked first on both cost and risk. The other ranked options and their prices are in § Recommendation. This ADR records the research so a later operator decision does not need re-discovery.

## 1. Inventory: what is already FOSS and what is bespoke

### Already FOSS (all in `gradle/libs.versions.toml`)

| Area | Dependency and pinned version | Source |
|---|---|---|
| Language and UI | Kotlin 2.4.21, Compose Multiplatform 1.12.2, Compose compiler, Material 3 via `compose.material3`, AndroidX core, activity, icons | `gradle/libs.versions.toml:11,18-24`; `androidApp/build.gradle.kts:113-126` |
| Navigation and background work | AndroidX Navigation Compose (catalog 2.10.2, forced 2.9.6), WorkManager 2.12.0, Lifecycle (forced 2.8.6) | `gradle/libs.versions.toml:24-25`; `settings.gradle.kts:63-65,131-140` |
| Persistence and DI | SQLDelight 2.4.1 (runtime, coroutines, Android/JVM/native drivers), Koin 4.2.2 | `gradle/libs.versions.toml:31-33,77-90` |
| KotlinX | coroutines 1.11.0, datetime (catalog 0.8.0, forced 0.6.2), serialization 1.11.0 | `gradle/libs.versions.toml:27-29`; `settings.gradle.kts:66,141-145` |
| Crypto | BouncyCastle `bcprov-jdk18on` 1.86, forced project-wide, first-seen recorded in the catalog as 2021-04-22 | `gradle/libs.versions.toml:59-66`; `settings.gradle.kts:54-56,106-112` |
| Test and measure | JUnit 4.13.2, Robolectric 4.17, kotlinx-coroutines-test, Turbine 1.2.1, Compose ui-test (AndroidX, test-only), macrobenchmark, UIAutomator | `gradle/libs.versions.toml:40-47,57-58,102-117`; `shared/build.gradle.kts:77-83`; `androidApp/build.gradle.kts:142-154` |
| Build quality | Kover 0.9.11, ktlint plugin 14.2.0 with ktlint engine 1.3.1, detekt 2.0.0-alpha.6 | `gradle/libs.versions.toml:35-38`; `androidApp/build.gradle.kts:157-160` |

Turbine deserves explicit mention because it is the operator's example of a small test helper: it is already declared and consumed in `shared/commonTest` (`shared/build.gradle.kts:81`). Adopting Turbine is not a future option; it is a shipped baseline.

### Bespoke (and why)

| Surface | What is local | Why it stays local |
|---|---|---|
| Scheduler and timezone policy | `shared/.../core/scheduler/`: ScheduleCalculator, ReminderRescheduler, phase arithmetic, DST and travel-mode policy, 72-hour lazy materialization | Medication-critical algorithm; ADR-0004, ADR-0007; vibe-dangerous with test-as-spec and 85% Kover gate. No FOSS library encodes this product rule. |
| Backup codec | `shared/.../core/backup/`: export/import format, Argon2id wiring through BouncyCastle | Format and key-derivation decisions are product decisions (ADR-0016, ADR-0018). The crypto primitive is already FOSS; the surrounding contract is not. |
| Notifications and boot | `androidApp/.../notifications/`, AlarmReceiver, BootReceiver, notification channels | AlarmManager + WorkManager hybrid is deliberate (ADR-0002, ADR-0011, ADR-0012). Removing the AlarmManager fallback is vibe-impossible. |
| Navigation shell | `ToebeansAppShell.kt` (336 lines, 5 tabs, 8 stacked destinations), `ui/nav/Destinations.kt`, `PetDetailMedicationDestination.kt` | This is the one surface where the candidates below would land. It is bespoke route wiring over an already-FOSS library (Navigation 2), not a hand-rolled navigator. |
| UI | 48 Kotlin files under `androidApp/.../ui/`: screens, view models, two hand-authored vector icons, theme, shared components | Compose and Material 3 already supply the widgets. Custom code is the owner's task flow and the one-handed, bottom-up layout rule. |
| Process and gates | Fitness functions, manual QA catalog (68 cases), ADR set, calibration ledger | These are the controls that make the medication-critical claim checkable. They are not library-shaped. |

Conclusion for the inventory: the project already uses open-source libraries for every layer where a general-purpose library is a good fit. The bespoke remainder is either medication-critical logic or product-specific UI. That is the intended shape, not technical debt.

## 2. Three candidate categories

Compatibility notes use the repo's actual toolchain: Kotlin 2.4.21, AGP 9.5.1, compileSdk 35, Compose Multiplatform 1.12.2 forced to AndroidX Compose 1.11.4, Lifecycle forced to 2.8.6. The Kotlin Multiplatform compatibility guide lists KGP 2.4.20 to 2.4.21 as tested against AGP 8.5.2 to 9.3.1 `[S6]`. The repo's AGP 9.5.1 is above that tested ceiling. No source retrieved for this ADR validates any candidate on the exact combination of AGP 9.5.1, Kotlin 2.4.21, and compose 1.11.4. That inherited evidence gap applies to all three categories and is not a reason to single any one out.

### Candidate A: first-party adaptive navigation (Navigation 3 plus Material 3 adaptive)

Real projects and versions:

- `androidx.navigation3:navigation3-runtime` / `navigation3-ui` 1.2.2 stable, released 2026-11-18; 1.3.0-alpha05 is the current alpha `[S1]`.
- `androidx.compose.material3:material3-adaptive-navigation-suite`: stable 1.5.0 released 2026-11-18; 1.6.0-alpha01 is the current alpha `[S7]`. `NavigationSuiteScaffold` switches between navigation bar, rail, and drawer on window size class `[S4]`.
- `androidx.compose.material3.adaptive:adaptive-navigation3`: 1.4.0-alpha03 released 2026-10-07, still alpha `[S3]`.

License and maintenance: Apache 2.0, authored by the Android Open Source Project; release notes show a steady alpha, beta, RC, stable cadence across 2026 `[S1] [S3] [S7]`. First-party and actively maintained.

Compatibility: the Navigation 3 setup guide requires compileSdk 36 or later; the repo is on 35 `[S2]`. The `navigation3-runtime` 1.2.2 POM declares Compose runtime 1.9.5 and kotlin-stdlib 2.1.20 `[S8]`. Version 1.2.1 lowered its own compileSdk from 37 to 36 `[S1]`. Material 3 1.6.0-alpha01 states compileSdk 37.1 for Compose libraries `[S7]`. `adaptive-navigation3` 1.4.0-alpha03 requires Lifecycle runtime 2.10.0 for AGP 9.5.0-alpha04 or later `[S3]`, while `settings.gradle.kts:131-134` forces Lifecycle 2.8.6. That is a direct pin conflict, not a vague risk. Verdict: compileSdk bump and a forced-version decision are prerequisites.

### Candidate B: Kotlin Multiplatform navigation (Decompose or Voyager)

Real projects and versions:

- Decompose 3.6.2, released 2026-11-10, Apache 2.0, first seen on Maven Central 2021-03-28, repository pushed 2026-11-22 with 3 open issues `[S9] [S10] [S11] [S12]`. Release notes name Kotlin 2.3.21 and Compose Multiplatform 1.9.3; the POM names Essenty 2.7.1 and kotlinx-serialization 1.6.3 `[S9] [S12]`.
- Voyager 2.2.21-1.10.3 on Maven Central, metadata last updated 2026-06-08, MIT, first seen 2021-07-22, repository pushed 2026-06-06 with 196 open issues `[S10] [S11] [S12] [S13]`. Its GitHub "latest release" marker still points at 1.0.1 published 2024-12-19 `[S11]`. The POM declares Kotlin 2.2.21 and Compose runtime 1.10.3 `[S13]`.

License and maintenance: both are permissive and AGPL-compatible. Decompose shows stronger maintenance signals (very recent release, few open issues). Voyager shows a versioning and signalling mismatch between Central and GitHub that a reviewer would have to resolve before trusting it.

Compatibility: neither source set was published against Kotlin 2.4.21 or Compose Multiplatform 1.12.2. A newer consumer can often resolve an older library's stdlib upward, but no source retrieved here demonstrates that combination for either library on AGP 9.5.1. Decompose also adds a component tree and lifecycle layer over the existing ViewModel plus repository plus SQLDelight stack, which pushes toward the refusal-list item on more than three abstraction layers. The KMP benefit only appears if navigation moves into `commonMain`; today all navigation code is in `androidApp`, so keeping Decompose there buys a rewrite with no sharing payoff.

### Candidate C: small test helpers (Turbine)

Real project and version: `app.cash.turbine:turbine` 1.2.1, released 2025-06-11, Apache 2.0, first seen on Maven Central 2020-08-03, repository pushed 2026-11-21 with 18 open issues `[S9] [S10] [S11] [S12]`.

Compatibility: the POM declares kotlinx-coroutines 1.10.2 and kotlin-stdlib 2.1.21 `[S12]`; the repo uses coroutines 1.11.0 and Kotlin 2.4.21. This combination already runs in the repo's test suites, as recorded in the 2026-11-22 daily log (single-agent record, not re-run in this research session). The README warns that Turbine depends on the still-unstable `UnconfinedTestDispatcher` API from kotlinx-coroutines-test `[S9]`. ADR-0013 already rejected Paparazzi and Roborazzi for this repo; that decision is not reopened here.

Status: already adopted. The only remaining decision is whether to expand its use to a specific untested Flow contract, which needs no new dependency.

## 3. Pros and cons tied to this repo's posture

| Consideration | Pro | Con | Weighing for toebeans |
|---|---|---|---|
| Local-only and no-network | All candidates are inert libraries; none calls the network or analytics services. The no-network and no-analytics fitness functions still pass. | A larger dependency graph increases the chance that a future transitive version pulls in something the gate rejects. | Neutral to mildly negative. Re-run both gates on adoption. |
| No AI features | None of the candidates is an inference or content library. | First-party Material 3 now ships `material3-a2ui`, an agent-to-UI renderer `[S7]`. Depending on the Material 3 artifact family does not import it, but the name is a review flag. | No veto; verify the resolved graph contains no A2UI or model artifact. |
| Medication-critical reminder path | Material 3 adaptive and Navigation 3 improve reach on large screens and support predictive back. | A navigation regression can hide the dose-logging route or tab strip. There are currently no automated navigation tests in `androidApp/src/test`. | Highest-weight negative. New nav code needs failing tests first and full manual re-run. |
| Anti-enterprise, boring tech | All candidates are established open source with permissive licenses. Decompose and Turbine are mature by first-seen date. | Navigation 3 plus adaptive plus serialization plus lifecycle-viewmodel is four new coordinates for a problem the current shell already solves. Voyager's release signalling is inconsistent. | Negative for A and B; neutral for C. |
| Refusal list | No candidate is a microservice, service mesh, event bus, or CQRS framework. A and C stay inside the ADR-0001 stack family. | Decompose adds a component-lifecycle layer over ViewModels, repositories, and persistence; it tests the "more than three layers" refusal item. Voyager is a third-party UI framework outside the explicit stack. | A: allowed in principle. B: needs a forcing-constraint ADR. C: allowed and already in. |
| Vibe-safety gates | Research is written down; no build file was touched. | Any adoption is a vibe-dangerous dependency addition with a ≥95 human review, a first test commit, and a calibration entry. | Cost is process as well as code. |
| AGPL-3.0 license posture (ADR-0020) | Apache 2.0 and MIT are permissive and distribution-compatible with the AGPL core; no copyleft conflict. | Redistribution must preserve notices and license text. MIT has no patent grant equivalent to Apache 2.0 section 3. | No blocker; record notices at adoption. |
| Toolchain drift | Navigation 3 and Material 3 are first-party and move quickly. | compileSdk 35 versus 36 or 37.1; forced Lifecycle 2.8.6 versus alpha requirement 2.10.0; AGP 9.5.1 above the KGP-tested 9.3.1 ceiling; Compose forced to 1.11.4. | Decisive blocker for A and B until resolved; inherited for all. |
| Test and QA burden | Turbine expansion adds assertions without new classpath risk. | A and B require new navigation tests, re-run of rendered UI tests under Robolectric, and re-run of roughly a dozen device cases. | Drives the day estimates in § 4. |
| iOS path (ADR-0001, milestone 5) | Navigation 3 and Decompose both have KMP stories; Decompose is explicitly multiplatform. | iOS target is disabled; paying for shared navigation now is speculation. ADR-0001 accepts Compose-iOS risk at milestone 5. | Defer. Revisit Decompose only if shared navigation becomes a real requirement. |

Steelman for adoption: an adherent would argue that a five-tab shell with eight stacked routes is already a navigation system, and that maintaining custom route strings, argument lookups, and back-stack save/restore by hand is precisely the boilerplate a maintained library should own. They would point to Navigation 3's stable 1.2.2 release, its result API, predictive back support, and first-party maintenance, and note that the repo's current route constants even carry a comment promising a type-safe migration once the API stabilized (`ui/nav/Destinations.kt:4-9`). That argument changes the evidence requirement: it needs a toolchain-compatible release and a navigation test harness before it can be taken up. Neither exists today.

## 4. Costed migration estimates

Planning-grade ranges, not measured commitment. Basis: 48 UI files, a 336-line app shell, five tabs, eight stacked destinations, three navigation arguments, zero navigation tests today, 68 manual QA cases, forced Compose 1.11.4, and compileSdk 35. Range width reflects the unresolved toolchain prerequisites. Each range assumes one engineer already familiar with the repo and excludes the human review wait time.

| Option | Named file groups touched | Engineer-days | Tests and QA surfaces that must be redone |
|---|---|---|---|
| 0. Do nothing | None | 0 | None. Keep the current gates green. |
| C. Expand Turbine use (no new dependency) | `shared/src/commonTest/` Flow tests; any ViewModel test that exposes a Flow. No build file change. | 0.5 to 1 | `:shared:jvmTest`, `:androidApp:testDebugUnitTest`, `bash scripts/verify_before_push.sh` (no `--fast`). No device cases. |
| A1. Material 3 adaptive navigation suite only, keep Navigation 2 | `gradle/libs.versions.toml` (compileSdk and catalog pin), `settings.gradle.kts` (forced pins), `androidApp/build.gradle.kts`, `ui/ToebeansAppShell.kt` (replace `Scaffold` + `NavigationBar` with `NavigationSuiteScaffold`), `ui/nav/Destinations.kt` (unchanged routes, new selection state), `res/values/strings.xml` | 1.5 to 3 | New Compose ui-test for tab selection and bar-to-rail switch; `:androidApp:testDebugUnitTest`; full gauntlet; device cases `fresh-install-core-nav`, `today-tab-loads`, `today-edit-reaches-medication-and-schedule`, `pets-list-avatar-and-fab`, plus a tablet or wide-window reach check (AGENTS.md one-handed, bottom-up rule). |
| A2. Navigation 3 plus adaptive, full replacement | All of A1 plus new `NavKey` and entry-provider files, `ui/nav/Destinations.kt` rewritten or deleted, `ui/ToebeansAppShell.kt` rewritten, callbacks in the 9 screen files that currently reference `onBack` or `navController.navigate`, Koin wiring if entry-scoped ViewModels are used, `gradle/libs.versions.toml` (serialization and lifecycle add-ons) | 6 to 10 | New navigation contract tests for back stack, argument parsing, and tab state restoration (none exist); all rendered UI tests from ADR-0013; `:shared:jvmTest` unaffected but re-run as a gate; `bash scripts/verify_before_push.sh`; macrobench cold-start re-baseline per ADR-0008; device cases `fresh-install-core-nav`, `warm-launch-keeps-prior-session`, `today-edit-reaches-medication-and-schedule`, `pet-detail-log-dose-now`, `today-fab-blank-add-medication`, `reminders-save-then-add-schedule`, `schedule-edit-keeps-history`, `schedule-detail-shows-override-dose`, `notification-at-scheduled-time`, `log-dose-persists-after-restart`, `reboot-reschedules-pending-alarms`, `dark-theme-log-dose-pill`. |
| B1. Decompose in `androidApp` only | New component and child-stack classes, all ViewModel call sites in `ui/`, `AppModule.kt` Koin factories, `ui/ToebeansAppShell.kt`, `ui/nav/Destinations.kt`, `androidApp/build.gradle.kts`. No `shared` change, so no KMP sharing benefit. | 8 to 14 | Same as A2 plus component lifecycle and state-preservation tests, back-handler tests, and an adversarial check against the "more than three layers" refusal item. |
| B2. Decompose with navigation in `shared/commonMain` | B1 plus `shared/build.gradle.kts`, new `commonMain` navigation API, `expect`/`actual` back handling, both source sets, iOS target scaffolding (still disabled) | 13 to 20 | B1 plus `:shared:jvmTest` contract suite, `:shared:testDebugUnitTest`, and a milestone-5 iOS spike. Highest risk; do not start without an operator requirement for shared navigation. |
| B3. Voyager in `androidApp` | Similar footprint to A2: new Screen classes, `Navigator` host, `ui/nav/Destinations.kt`, shell, callbacks across screens, `androidApp/build.gradle.kts`. Extra time to resolve the Central-versus-GitHub release mismatch and the Compose 1.10.3 versus 1.12.2 skew. | 7 to 12 | Same as A2 plus a resolved supply-chain note. Not recommended: the maintenance signal is weaker than Decompose and the toolchain skew is wider. |

All options that touch a build file also incur the non-code steps in § 6. The device cases are a subset of the 68-case catalog; the full catalog should still be re-run before any release, not only the navigation subset.

## 5. Recommendation, ordered by cost and risk

1. **Do nothing (0 days, lowest risk). Recommended now.** The repo already uses FOSS where FOSS fits. No dependency, no toolchain change, no medication-critical test rewrite. Revisit when one of the triggers in § 7 fires.
2. **Expand Turbine coverage (0.5 to 1 day, no new dependency). Optional.** Only do this when a named Flow contract is untested. It improves test density without touching the release classpath.
3. **Material 3 adaptive navigation suite alone (1.5 to 3 days, moderate risk). Conditional.** Take it only if tablet or foldable navigation becomes a stated product requirement. Keep Navigation 2. Resolve compileSdk first, and stay on the stable 1.5.0 line rather than 1.6.0-alpha01.
4. **Navigation 3 (6 to 10 days, higher risk). Defer.** Revisit when compileSdk is 36 or higher, a navigation test harness exists, and the Lifecycle forced pin is reconciled. The stable 1.2.2 release makes this a real future option, not a fad.
5. **Voyager (7 to 12 days, higher risk). Not recommended.** Weaker maintenance signals, inconsistent release metadata, and a Compose version skew.
6. **Decompose (8 to 20 days, highest risk and highest abstraction cost). Defer to milestone 5 if at all.** It only pays off if navigation logic is genuinely shared with iOS. Today it would replace a working shell with a new component tree and no sharing payoff.

The pre-registered prediction survives: no candidate beat do-nothing on the combination of this repo's toolchain, medication-critical risk, and process cost. Confidence after retrieval: 0.85. Main residual uncertainty is whether the operator plans a tablet form factor or shared iOS navigation in the near term; that is a product fact, not a research finding.

## 6. Dependency-vetting steps required before any adoption

These are gates, not a checklist to skim. Each one needs a recorded artifact.

1. **State the forcing constraint in a reviewable ADR or ADR update.** Identify the user-visible problem that the current bespoke code cannot solve cheaply. Get human review at confidence ≥95 per AGENTS.md.
2. **Verify the exact coordinate and publisher** in Maven Central or Google Maven. Confirm group and artifact spelling against typosquats. Record the coordinate verbatim.
3. **Verify first-seen ≥30 days** from Maven Central publish metadata and re-check at adoption time. Baseline dates found this session: Decompose 2021-03-28, Voyager 2021-07-22, Turbine 2020-08-03 `[S10]`. Google Maven does not expose per-version first-seen in its metadata; use the official release-notes date for AndroidX artifacts.
4. **Verify the license text**, not just the badge. Apache 2.0 and MIT are compatible with the AGPL core; record notice and attribution obligations and the absence of a patent grant where relevant.
5. **Check the resolved graph against the refusal posture.** Re-run `bash scripts/test_no_network.sh .`, the no-analytics gate, and any AI-artifact check. Reject any coordinate that pulls a network, analytics, or inference artifact. Material 3 adoption specifically needs a check that no `material3-a2ui` artifact resolves.
6. **Reconcile the toolchain matrix.** Confirm compileSdk, the AGP 9.5.1 versus KGP-tested-9.3.1 gap, and every conflicting `force(...)` in `settings.gradle.kts`. If compileSdk changes, record that decision separately.
7. **Write the failing test first.** Commit it alone, get human review of the signature and assertions, then implement. This is the test-as-spec rule for vibe-dangerous surfaces.
8. **Refresh and compile before the gauntlet.** Run `./gradlew --refresh-dependencies :shared:compileTestKotlinJvm :androidApp:compileDebugUnitTestKotlin` per CONTRIBUTING.md, then `bash scripts/verify_before_push.sh` without `--fast`, plus `:shared:testDebugUnitTest`, `:shared:koverVerify`, and `:androidApp:assembleDebug`.
9. **Log the calibration entry** with the hallucination component showing the Maven Central and first-seen checks. No score bump without new evidence.
10. **Re-run the affected manual QA cases on the device** with the full cold-start block from the AGENTS.md workspace memory. Leave each unchecked box for the operator.

## 7. Decisions awaiting the operator

| ID | Decision | Effect if unanswered |
|---|---|---|
| D1 | Approve a compileSdk bump from 35 to 36 (or 37) for first-party navigation work? | Options A1, A2, and B stay blocked. Do-nothing stands. |
| D2 | Is tablet, foldable, or navigation-rail support a product requirement now? | Without it, A1 and A2 buy no user-visible value. |
| D3 | Will navigation logic be shared with iOS at milestone 5, or will each platform own navigation? | Without a sharing requirement, Decompose has no payoff over Navigation 2. |
| D4 | Authorise any new dependency at all, and if so which single coordinate? | No build file changes. All ranked options above 2 stay proposed. |
| D5 | Accept AGP 9.5.1 above the KGP-tested 9.3.1 ceiling as inherited risk, or schedule a toolchain reconciliation? | Every adoption estimate keeps a toolchain uncertainty band. |
| D6 | Name the human reviewer for a dependency addition and for its first failing test commit. | Vibe-dangerous gate cannot be satisfied. |

No decision in this ADR changes AGENTS.md, CLAUDE.md, the version catalog, or any build file. ADR-0021 on the on-device veterinary model remains a separate, unselected gate and is unaffected.

## 8. Primary sources

All web sources were retrieved on 2026-11-22. Read depth follows palamedes: `read:full` means the whole retrieved page or artifact was inspected; `read:body` means relevant sections were read end to end with output truncation elsewhere. Where output was truncated, only the sections actually read are used.

- `[S1]` Android Developers, "navigation3 | Jetpack", release notes, https://developer.android.com/jetpack/androidx/releases/navigation3 , retrieved 2026-11-22, `read:body` (top 619 of 876 lines returned; 1.2.0 to 1.3.0-alpha05 sections read in full). Stable 1.2.2 and alpha 1.3.0-alpha05 both dated 2026-11-18; 1.2.1 lowered compileSdk from 37 to 36.
- `[S2]` Android Developers, "Get started with Navigation 3", https://developer.android.com/guide/navigation/navigation-3/get-started , retrieved 2026-11-22, `read:full`. compileSdk 36 or later; artifact list.
- `[S3]` Android Developers, "Compose Material 3 Adaptive | Jetpack", release notes, https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive , retrieved 2026-11-22, `read:body` (top 695 of 828 lines returned). Adaptive 1.3.0 stable dated 2026-08-12; adaptive-navigation3 1.4.0-alpha03 dated 2026-10-07 with the Lifecycle 2.10.0 / AGP 9.5.0-alpha04 note.
- `[S4]` Android Developers, "Build adaptive navigation", https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation , retrieved 2026-11-22, `read:full`. `NavigationSuiteScaffold` behavior and dependency.
- `[S5]` Google Maven, `androidx/navigation3/navigation3-runtime` metadata, https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-runtime/maven-metadata.xml , retrieved 2026-11-22, `read:full`.
- `[S6]` Kotlin documentation, "Compatibility guide for Kotlin Multiplatform", https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html , retrieved 2026-11-22, `read:full`. KGP 2.4.20 to 2.4.21 tested with Gradle 7.6.3 to 9.7.0 and AGP 8.5.2 to 9.3.1.
- `[S7]` Android Developers, "Compose Material 3 | Jetpack", release notes, https://developer.android.com/jetpack/androidx/releases/compose-material3 , retrieved 2026-11-22, `read:body` (top 510 of 3254 lines returned; Material 3 1.5.0, 1.6.0-alpha01, A2UI, and navigation-suite versioning sections read). Material 3 stable 1.5.0 dated 2026-11-18; 1.6.0-alpha01 states Compose compileSdk 37.1.
- `[S8]` Google Maven, `navigation3-runtime` 1.2.2 POM, https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-runtime/1.2.2/navigation3-runtime-1.2.2.pom , retrieved 2026-11-22, `read:full`. Apache 2.0; Compose runtime 1.9.5; kotlin-stdlib 2.1.20.
- `[S9]` Cash App, Turbine README and 1.2.1 release, https://github.com/cashapp/turbine , https://github.com/cashapp/turbine/releases/tag/1.2.1 , retrieved 2026-11-22, `read:full` (README head and release JSON). Apache 2.0; release published 2025-06-11; unstable `UnconfinedTestDispatcher` warning.
- `[S10]` Maven Central search API, first-seen timestamps for `com.arkivanov.decompose:decompose`, `cafe.adriel.voyager:voyager-navigator`, and `app.cash.turbine:turbine`, https://search.maven.org/solrsearch/select , retrieved 2026-11-22, `read:full` (JSON). Earliest versions: 0.2.0 at 2021-03-28, 1.0.0-beta01 at 2021-07-22, 0.1.0 at 2020-08-03. Note: the Decompose Solr index lagged the artifact metadata, so it is used only for first-seen, never for latest version.
- `[S11]` GitHub REST API, `releases/latest`, repository metadata, and license endpoints for Decompose, Voyager, and Turbine, https://api.github.com/repos/arkivanov/Decompose , https://api.github.com/repos/adrielcafe/voyager , https://api.github.com/repos/cashapp/turbine , retrieved 2026-11-22, `read:full` (JSON). Decompose 3.6.2 published 2026-11-10; Voyager latest tag 1.0.1 published 2024-12-19; Turbine 1.2.1 published 2025-06-11. Push dates and open-issue counts used as maintenance signals.
- `[S12]` Maven Central POMs: Decompose 3.6.2, Voyager navigator 2.2.21-1.10.3, Turbine 1.2.1, https://repo.maven.apache.org/maven2/ , retrieved 2026-11-22, `read:full` (XML). Licenses and declared dependency versions.
- `[S13]` Maven Central metadata: `cafe/adriel/voyager/voyager-navigator/maven-metadata.xml` and `com/arkivanov/decompose/decompose/maven-metadata.xml`, https://repo.maven.apache.org/maven2/ , retrieved 2026-11-22, `read:full` (XML). Voyager latest 2.2.21-1.10.3, last updated 2026-06-08; Decompose latest 3.6.2, last updated 2026-11-10.
- Google Maven metadata for `material3-adaptive-navigation-suite` and its 1.5.0 POM, https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-adaptive-navigation-suite/ , retrieved 2026-11-22, `read:full`: stable 1.5.0, Apache 2.0, kotlin-stdlib 2.2.20. Used only where cited alongside `[S7]`.

Local grounding files read in full this session: `AGENTS.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, `docs/adr/0001-kmp-and-compose-multiplatform.md`, `docs/adr/0006-deferred-mutation-testing.md`, `docs/adr/0013-compose-ui-test-adoption.md`, `docs/adr/0015-kotlin-agp-compatibility-warning-suppression.md`, `docs/adr/0020-license-posture-m12.md`, `docs/adr/0021-on-device-vet-assist-posture.md`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `macrobench/build.gradle.kts`, `androidApp/src/main/kotlin/app/toebeans/android/ui/ToebeansAppShell.kt`, and `androidApp/src/main/kotlin/app/toebeans/android/ui/nav/Destinations.kt`.

Evidence limits: GitHub API metadata is current as of retrieval but is a maintenance proxy, not a quality rating. Maven Central Solr timestamps are publish times, not an independent security audit. No source retrieved validates any candidate on AGP 9.5.1 with Kotlin 2.4.21, so every compatibility statement above is a documented evidence gap rather than a claim of failure. Local test-pass counts quoted from the daily log are single-agent records, not re-run in this research session.
