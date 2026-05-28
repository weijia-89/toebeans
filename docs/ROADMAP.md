# toebeans roadmap

> A **milestone** is "what subset of users could use the app without hitting bugs."
> Not "feature richness." Not "completeness." Just "ship-ability."

This document is the canonical answer to "what's feasible now vs what's deferred." When in doubt, this file wins over chat-history claims.

Last updated: 2026-05-28.

### In-flight PRs (not merged)

| PR | Branch | Milestone | What |
|----|--------|-----------|------|
| [#75](https://github.com/weijia-89/toebeans/pull/75) | `feat/home-m12-scroll-filter-edit` | M1.2 | Today scroll; pet filter; Edit on due rows |
| [#76](https://github.com/weijia-89/toebeans/pull/76) | `chore/trainer-patch-head-port` | CI | Trainer PR `head=` patch script from buds |
| [#77](https://github.com/weijia-89/toebeans/pull/77) | `feat/reminders-add-fab-m12` | M1.2 | Reminders extended FAB + empty-state add |
| [#78](https://github.com/weijia-89/toebeans/pull/78) | `feat/pets-tab-ia-m2` | M2 IA | Pets list focus; top-bar **+** instead of extended FAB |

---

## v0.1: Scaffold (current)

**Ship-ability:** zero users. Engineering artifact only.

| Done | What |
|---|---|
| ✓ | KMP + Compose-Multiplatform Gradle scaffold (JDK 17, Kotlin 2.0.21, AGP 8.7) |
| ✓ | Domain models (`Pet`, `Medication`, `Schedule`, `SchedulePhase`, `DoseEvent`) with validation |
| ✓ | SQLDelight schema for the same |
| ✓ | `ScheduleCalculator` interface + `DefaultScheduleCalculator` full impl |
| ✓ | `SchedulePhaseRulesTest` test-as-spec, **15 cases**, all green |
| ✓ | Backup codec (`BackupCipher` PBKDF2 + AES-256-GCM, expect/actual JVM+Android) + 15 tests |
| ✓ | Notification actuator (`AndroidNotificationActuator`) + 9 Robolectric tests. Boot replay wired via `BootReceiver` (M1); notification firing UI still on ROADMAP. |
| ✓ | 6 fitness functions (no-network, no-analytics, scheduler-purity, permission-allowlist, no-PII-in-crash-log per ADR-0009, AGENTS/CLAUDE parity) |
| ✓ | GitHub Actions CI (fitness + lint + tests + Android assemble) |
| ✓ | **9 ADRs** (KMP+Compose, AlarmManager hybrid, local-first, tapering model, vibe-dangerous reminder firing, Kover deferred-pitest, timezone/travel-mode, perf-class, local-crash-log-no-telemetry) |
| ✓ | Vibe-dangerous pre-commit hook + `.codeit/calibration.jsonl` audit log |
| ✓ | Kover line-coverage gate at 85% on `scheduler/` + `backup/` (was 0 at scaffold time) |
| ✓ | DI smoke test (`AppModuleSmokeTest`) resolves every ViewModel through Koin, catching missing-binding bugs that compile cleanly but crash at app launch |
| ✓ | Pet + medication delete affordances (top-bar action + confirmation dialog) with VM-level tests |
| ✓ | `DoseEvent.medicationId` denormalization (model + SQLDelight schema + repo signatures + fake + UI plumbing). Drops the seed-only `replaceFirst("sched-","med-")` join, fixes the Logged Today retrospective for user-created medications |
| ✓ | Local crash-log capture (`LocalCrashLog` + `Settings → Export crash log`) per ADR-0009: no telemetry, user-initiated share, 7 unit tests covering write/rotate/delegation/IO-failure |
| ✓ | `ignoreFailures = true` removed from `shared/build.gradle.kts` and `androidApp/build.gradle.kts`. Test failures are now fatal at every layer (local + CI). Hand-back item 3 closed. |

---

## Milestone 1: MVP (the "one user can use this" version)

**Ship-ability:** you + 1–3 trusted testers.

| Pending | What | Source |
|---|---|---|
| ✓ | **ScheduleRepository** SqlDelight impl + contract tests | [PR #42](https://github.com/weijia-89/toebeans/pull/42) merged 2026-05-23 @ `c917228` |
| ✓ | **PetRepository** SqlDelight impl + contract tests | Prior merge (Phase 2) |
| ✓ | **AppModule DI swap** (Pet + Schedule + Med + DoseEvent on SqlDelight; ADR-0010 FK callback) | M1 step 3→4 bridge |
| ✓ | **MedicationRepository** SqlDelight impl + contract tests | M1 step 3 |
| ✓ | **DoseEventRepository** SqlDelight impl + contract tests | M1 step 3 remainder |
| ✓ | **Schedule delete affordance.** Top-bar Delete action on the new Schedule Detail screen (B7) wires through to `ScheduleRepository.delete`, with the same confirmation-dialog pattern as the pet + medication delete flows (no undo snackbar, since delete-with-phases is destructive enough to warrant a hard confirm). The dialog mirrors the error-tinted Delete button + "this can't be undone" body copy. Hard-delete now executes against SQLDelight with FK cascade per ADR-0010 (`Schedule.medication_id` ON DELETE CASCADE, `SchedulePhase.schedule_id` ON DELETE CASCADE, `DoseEvent.schedule_id` ON DELETE CASCADE). The previous draft of this entry incorrectly described a soft-delete via `endDate` plan; `endDate` is the schedule's intended last dosing day, not a deletion tombstone. Corrected during B8 self-review. | Cold review, P2 |
| ✓ | Real `DoseAlarmReceiver` DB lookup (replaces `ScheduledReminder(scheduleId = "", ...)` placeholder) | v0.1-followups #3 |
| ✓ (PR [#52](https://github.com/weijia-89/toebeans/pull/52), merged) | `BootReceiver` + 72h-horizon SQLDelight rehydration in receiver process (`ToebeansApp.loadPendingRemindersInHorizon`, `rehydrateBootAlarms`). On `main`. | v0.1-followups #4 · SDK T4 |
| ✓ | PendingIntent collision mitigation via `RequestCodeAllocator` (SharedPreferences-backed, strictly monotonic Int), replacing `reminderId.hashCode()` in `AndroidNotificationActuator`. Regression test uses the canonical Java "Aa" / "BB" hash-collision pair to prove independent scheduling. 10 unit tests on the allocator + 1 actuator regression case. | v0.1-followups #5 |
| | Compose UI: Add Medication (with anchor-mode prompt per ADR-0007). Effectively blocked on M1.5 because the underlying `Schedule.anchorMode` enum lives in that milestone (gated on ADR-0007 G1/G2/G3). The Medication-edit screen and VM are in place; the anchor-mode prompt itself layers on once the enum lands. | ADR-0007 |
| ✓ | **Reminder List screen.** New `Reminders` bottom-nav tab between Today and Pets, between-tab state preserved by androidx.navigation's saveState/restoreState. Pure projection in `ReminderListViewModel.joinToUiState` (Pet × Medication × ScheduleWithPhases → `ReminderRowUi`, sorted by pet/med name, case-insensitive). Phase summary collapses 3+ phases into `(+N more)`; end-date label branches today / tomorrow / N-days / dated / ended. Stale-row hazards funnel through `StaleEventGuard` (Tier A #4 contract). Tap target navigates to Schedule Detail (B7). LazyColumn with stable keys ready for the deferred scroll macrobench. 11 new unit tests on the ViewModel projection + helpers; `AppModuleSmokeTest` updated. |
| ✓ | **Schedule Detail screen (B7).** Reached from the Reminder List tap. Top-level route `schedule/{scheduleId}` (flat, so the detail VM resolves pet + medication from the scheduleId internally and callers don't have to thread three ids through nav args). `ScheduleDetailViewModel` drives a `flatMapLatest` over `ScheduleRepository.observeById` + `observePhases` + `MedicationRepository.observeAll` + `PetRepository.observeAll`, materializing a single immutable `ScheduleDetailUiState` per emission. Stale-detection: `schedule == null && !loading` is the explicit terminal-deletion state (the screen auto-pops via `LaunchedEffect` keyed on it); unknown med/pet renders placeholder labels rather than crashing (the Reminder List filters those upstream; defense in depth here). Body sections: header card (`pet · medication`), date-range card (`start → end` or "ongoing"), phases card with per-phase cadence / interval / duration / dose times / optional dose-amount override. Top-bar Delete action with confirmation dialog matching the pet + medication delete pattern. `ScheduleRepository.observeById` added to the contract (mirrors `PetRepository.observeById`). 5 new ViewModel tests (load, delete, no-load delete, concurrent-delete state, missing med/pet placeholder); `AppModuleSmokeTest` updated; `Destinations.scheduleDetail(id)` + `Args.SCHEDULE_ID` added. |
| ✓ | **Midnight-mode UX warning during phase creation (B9).** `PhaseDraft.nightDoseWarning` recomputes on every `updatePhase` call by scanning `doseTimes` for entries in `[00:00, 06:00)`. `ScheduleCreateViewModel.affirmNightDose(index)` is the explicit "Yes, that's intentional" action that flips `nightDoseAffirmed` to true; any subsequent edit to dose times resets it to false so the user re-confirms after changes. The spec was silent on persistence; the safer policy is the default. Banner uses `clearAndSetSemantics` + `LiveRegionMode.Polite` for TalkBack, matching the B8 formError pattern. 5 ViewModel tests in `ScheduleCreateNightDoseTest` cover boundary cases at 00:00 / 03:00 / 06:00, affirmation clearing, and the edit-resets-affirmation case. Compose snapshot test deferred pending ADR-0013 Compose UI test deps. | v0.1-followups #1 |
| ✓ | **Inline calculator error UI in Schedule Create (B8).** Save now runs a pre-flight `scheduleCalculator.computeScheduledDoses` across `[startDate 00:00, +30d)` BEFORE persisting. Any `MalformedScheduleException` subclass is mapped to a user-readable message (e.g. EventCountExceeded → "This schedule would generate N doses in 30 days, more than the safe limit (100000). Reduce…"). The message lands in a new `formError` field on `ScheduleCreateUiState` and renders as an `errorContainer`-tinted banner above the Schedule-window section. The banner declares `liveRegion = Polite` + an "Error:" content description so TalkBack announces it without focus-stealing. Any field mutation (`onStartDateChange`, `onEndDateChange`, `addPhase`, `removePhase`, `updatePhase`) clears the banner so it disappears as the user begins addressing the underlying configuration. `ScheduleCalculator` injected via Koin. 4 new tests (`ScheduleCreatePreflightTest`) covering the EventCountExceeded path, banner-clear-on-mutation, happy path persistence, and direct `runPreflight` exception-mapping for DuplicatePhaseOrder. | from D3 decision |
| ✓ | **Backup export and import UI (v1 plain JSON per ADR-0016).** SettingsScreen Data card has live Export and Import buttons that drive Storage Access Framework pickers so the file lives at whatever location the user picks. `ExportBackupViewModel` + `ImportBackupViewModel` handle the read/write paths with unit tests in `ExportBackupViewModelTest` and `ImportBackupViewModelTest`. Per ADR-0016, v1 ships plain JSON without a passphrase: the backup file contains no medical-grade or identifying information that warrants encryption today. The encrypted posture (passphrase entry + AES-GCM envelope) reactivates per ADR-0016 v2 triggers when more sensitive fields land in the schema or when v2 of the backup format is designed. Import flow includes a confirm dialog naming the merge-by-id behavior; `ImportBackupUiState.AwaitingConfirm` gates the apply step. The earlier "Settings → Export disabled with coming-soon affordance" copy is obsolete and was removed when the live buttons shipped. | ADR-0016, Cold review |
| ✓ | First macrobenchmark module: `:macrobench` Gradle module (AGP `com.android.test` + `androidx.benchmark`). Ships `StartupBenchmark` covering cold-start with `CompilationMode.None` (worst-case) and `CompilationMode.Partial` (informational baseline-profile preview). New `benchmark` build variant on `:androidApp` with a buildType-scoped `<profileable shell="true"/>` manifest overlay so production builds stay clean. Three new vibe-dangerous deps (`androidx.benchmark.macro.junit4`, `androidx.test.runner`, `androidx.test.uiautomator`) with human review granted out-of-band. List-scroll benchmark deferred until the Reminder List screen lands (M1 Tier B); calculator-perf microbenchmark deferred to `:shared` JVM (different toolchain). CI integration is manual + nightly per ADR-0008 sequencing; see `macrobench/README.md`. Permission-allowlist fitness function patched to tolerate manifests with zero permissions. | ADR-0008 |
| ✓ | Crash-on-render-of-stale-event safety net: `StaleEventGuard` wired into `HomeViewModel.joinToUiState` + `computeDueToday`. Debug builds throw `IllegalStateException` with a diagnostic message naming the site + event + missing field, surfacing future join bugs in CI. Release builds log via `Log.w` and skip the row so inter-Flow races during deletion don't crash a tester. Three legacy "skipped silently" tests rewritten to assert the new contract; one new `StaleEventGuardTest` (3 cases) pins the message format + throw behavior. AGP `buildConfig = true` enabled to access `BuildConfig.DEBUG` (feature flag, no new dep). |
| ✓ | `scripts/test_no_pii_in_crash_log.sh` fitness function: greps the crash-handler source for any reference to repository / dao / model / persistence symbols so the local-crash-log handler (ADR-0009) cannot drift toward leaking domain data into the log. Wired into CI as the 5th gate; self-test verified it catches an injected `Pet` reference. | Cold review |

### Recommended M1 sequencing

M1 is a **multi-session effort**, not a single push. Each item below is its own commit-or-PR scope. Sequencing reflects dependency order and risk:

1. **Tier A cheap independent wins** (this work block): PendingIntent collision mitigation, first-launch seed gate, stale-event safety net, macrobench module. None depend on SQLDelight; each lands as its own commit. The `ignoreFailures = true` removal that was step 1 has already shipped.
2. **Tier B Compose UI surface** (now shipped on SQLDelight-backed repos): Reminder List, Schedule Detail (+ delete affordance), inline `MalformedScheduleException` error UI, midnight-mode UX warning, Backup export/import UI (v1 plain JSON per ADR-0016).
3. **SQLDelight repositories** (`PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`). All downstream items (4, 5) depend on real persistence; the in-memory fakes return empty state in a separate-process `BroadcastReceiver`, so the `DoseAlarmReceiver` DB lookup cannot ship against them. Most expensive single item in M1; budget 2-3 days.
4. **Real `DoseAlarmReceiver` DB lookup**. Vibe-dangerous; needs the SQLDelight layer from (3). Pair with a test-as-spec for receiver-side lookup that matches a known DoseEvent ID.
5. **`BootReceiver` + 72h-horizon rehydration.** The most safety-critical work in M1; determines whether alarms survive device reboots. ADR-0005 vibe-dangerous + needs its own ADR amendment for the rehydration window choice. Restore `RECEIVE_BOOT_COMPLETED` to the manifest as part of this commit.

Each item ends with: full gate green + calibration entry + ADR amendment if it touched a vibe-dangerous surface. Do not batch.

**Definition of done (milestone 1):**
- A real pet owner can install the app, add a pet, add a medication with phases, and the alarms fire correctly.
- 30-day soak test on a non-OEM device (Pixel a-series) passes with zero missed alarms.
- All 15 `SchedulePhaseRulesTest` cases pass, plus any new DST/anchor-mode cases added in M1.5.
- All fitness functions pass with no `continue-on-error`.

---

## Track: Design system sign-off + style lab (P1, docs-only)

**Ship-ability:** none (planning artifact). Does not block M1 alarm correctness; **does** block an intentional Compose theme polish pass.

**Goal:** same workflow as buds [Track C style lab](https://github.com/weijia-89/buds/blob/main/docs/ROADMAP.md): compare variants in a browser, record **fresh** decisions in `docs/style-lab/DECISIONS.md`, then align Kotlin theme in a follow-up PR.

| Done | What |
|---|---|
| ✓ | `docs/style-lab/` static lab (`index.html`, `style.css`, `tokens-snapshot.json`, `DECISIONS.md`): Today row, settings card, three variant packs |
| ✓ | README **Design review** link + `bash scripts/manual_qa_boot.sh fresh --open-style-lab` |
| ✓ | **Wei review + sign-off:** `Chosen: terracotta-warm` (2026-05-26); Given = keep sage tertiary; Material You = no new work (toggle already shipped, default off) |
| ✓ | **Compose alignment PR:** Today Log dose → filled pill (match lab); `darkColorScheme` terracotta-warm tune; Given ✓ uses tertiary sage | 2026-05-26 |

**Gates:**

- Style lab renders offline (`open docs/style-lab/index.html`; no build step)
- No `Color.kt` / theme diff on `main` until `DECISIONS.md` records a chosen pack
- Theme PR: `./gradlew ktlintCheck detekt :shared:jvmTest` + visual pass on Today + Settings on device

---

## Milestone 1.2: Internal beta + decision gate

**Ship-ability:** the developer and 1–3 trusted testers, distributed via Play Store internal-testing track. NOT public.

This milestone sits between M1's "feature complete" and M1.5's "travel-aware" because the internal-beta loop is what tells us whether we've actually built the right thing before we burn time on the harder edge cases.

| Pending | What | Source |
|---|---|---|
| ✓ | **Style lab review + design sign-off.** `docs/style-lab/DECISIONS.md` records **terracotta-warm** + resolved open decisions (2026-05-26). | Track: Design system |
| | Play Store internal-testing track set up with 1–3 testers (developer + close circle). **Walkthrough doc shipped at `docs/play-store-internal-testing-walkthrough.md` (8 phases, ~3-5 hours wall-clock + 1-3 day ID verification wait). Execution is a human task; Cascade cannot click through Play Console.** | Cold review |
| ✓ | Written soak-test protocol for testers: 30-day run on a Pixel a-series device, daily log + weekly snapshots + day-30 structured report; alarm-fire reliability + crash-log capture + retention gate at day 14 (M1.2 definition-of-done). Shipped at `docs/soak-test-protocol.md`. | Cold review |
| | Adoption metric (read by the developer, NOT analytics): does at least one tester continue to use the app past day 14? If not, we have a retention problem upstream of feature work and M2 cannot proceed. | Cold review |
| ✓ | **License posture (M1.2 gate closed): AGPL-3.0.** Operator decision 2026-05-26; root `LICENSE` already GNU AGPL v3 (verified, no rewrite). Rationale and contributor/acquirer implications: `docs/adr/0020-license-posture-m12.md`. Feasibility dossier open question #2 resolved for M1.2. Apache-2.0 + CLA path rejected for now to preserve copyleft/moat alignment. | Cold review, feasibility dossier §11, ADR-0020 license posture |
| ✓ | **Distribution wedge (M1.2 gate closed): D1 pet-owner direct.** Operator decision 2026-05-26; M2 work item at `research/distribution-wedge-m2-d1.md`. Feasibility dossier §4.6 option 1 selected; clinic, insurer, and rescue paths deferred. | Cold review, feasibility dossier §4.6 |
| ✓ | **Beta smoke checklist (Q9 blocker):** operator runs add pet → medication → schedule on device before inviting internal testers. Shipped at `docs/beta-smoke-add-medication.md`. Anchor-mode prompt remains M1.5 / ADR-0007. | research/decisions/2026-05-26-m12-beta-gates.md § Q9 |
| ✓ | First-launch UX revisit: gated the Rufus + Luna seed behind a first-launch dialog (`FirstLaunchDialogHost`). Stores now start empty; `loadDemoData()` populates the demo on the user's tap of "Load demo data". `FirstLaunchPreferences` persists the seen-flag in SharedPreferences (same pattern as `ThemePreferences`). 11 new unit tests (5 prefs + 6 demo-loader, including a user-created-entries-preserved case). | Cold review |
| | **Add Medication unified modal (create flow).** Single surface for medication + initial schedule window + dose times + notification/alert settings. Required fields: medication **name** and **dose amount** only; pet selection, notes, phase overrides, and full medication metadata optional when needed. Replaces the post-beta friction of save medication → navigate → schedule create. Does **not** block M1.2 beta (Q9 uses the current multi-step path in `docs/beta-smoke-add-medication.md`). Anchor-mode prompt remains M1.5 / ADR-0007. | Operator product intent 2026-05-27, research/decisions/2026-05-26-m12-beta-gates.md § Q9 |
| ✓ | **Today screen: in-page pet filter + scroll + Edit** ([#75](https://github.com/weijia-89/toebeans/pull/75)). Pet chips filter due + logged sections; Today header clears filter; due rows get **Edit** beside **Log dose** (routes to `MedicationEditScreen`). **Medication edit context card** (pet name, med + dose, schedule hint) on edit/add screens (follow-up PR). | Operator product intent 2026-05-27 |
