# toebeans roadmap

> A **milestone** is "what subset of users could use the app without hitting bugs."
> Not "feature richness." Not "completeness." Just "ship-ability."

This document is the canonical answer to "what's feasible now vs what's deferred." When in doubt, this file wins over chat-history claims.

Last updated: 2026-05-27.

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
| | **Today screen: in-page pet filter.** Tapping the **Today** header (when a filter is active, tap clears to all pets) and tapping a **pet name** in the "Your pets" row filters the existing due-dose list and **Logged today** retrospective on that screen to that pet only. **No navigation** to Pet Detail for filter intent; Pet Detail remains reachable from Pets tab and explicit row actions. Preserve filter state across tab switches via existing bottom-nav `saveState`/`restoreState`. Does not block M1.2 beta. | Operator product intent 2026-05-27 |
| | **Today dose row: Edit affordance + spacing.** Add an **Edit** control beside **Log dose** on due-today rows with slightly more horizontal spacing than today (reduce mis-taps; not awkwardly far). **Edit** opens medication + schedule/alert settings: prefer deep-link into **Add Medication unified modal** (M1.2 row, when landed) for med + schedule + notifications in one surface; until then route via `MedicationEditScreen` + Schedule Detail (B7) or schedule create as appropriate. Does not block M1.2 beta. | Operator product intent 2026-05-27 |
| | **Settings theme segmented control: selection border desync.** On Settings → Display, tapping Light/Dark/Auto updates the checkmark to the chosen mode but the white M3 selection outline stays on the previous segment (reported: checkmark on Dark while border remains on Light). Fix `SettingsScreen` `SingleChoiceSegmentedButtonRow` so border and `selected` state track `ThemeMode` from `ThemePreferences`; add Compose UI test or screenshot regression after ADR-0013 deps land. M1.2 polish / post–style-lab follow-up; does not block internal beta. | Operator feedback 2026-05-27 |

**Definition of done (milestone 1.2):**
- One tester has used the app for 30 consecutive days without losing trust.
- License decision is made and the LICENSE file reflects it.
- Distribution wedge is named and a M2 work item exists for it.
- Crash log export has been exercised by at least one tester (proves ADR-0009 works in the field).
- Style lab **`Chosen:`** recorded in `docs/style-lab/DECISIONS.md` (or explicitly waived with reason in that file).

---

## Milestone 1.5: Travel + DST aware

**Ship-ability:** 3–10 users including the diabetic-pet and seizure-pet cohorts.

**BLOCKED ON** ADR-0007 acceptance gates G1, G2, G3 (literature review + emulator dogfood + user research).

| Pending | What |
|---|---|
| | `Pet.homeTimezone` column + migration |
| | `Schedule.anchorMode` enum field + migration |
| | `ELAPSED_INTERVAL` math in `DefaultScheduleCalculator` |
| | DST-aware materialization (spring-forward shift, fall-back dedupe) |
| | `TimezoneChangeReceiver` manifest entry + `ACTION_TIMEZONE_CHANGED` handler |
| | Onboarding flow asking "does your pet travel with you?" + "time-sensitive medication?" |
| | `SchedulePhaseDstRulesTest` (separate from the main test-as-spec) |
| | ADR-0007 G1 citation completion (Plumb's 9th ed.) |
| | UI integration test for TZ-shift mid-day |
| | **Prescription label capture (M1.5+ pointer; OCR delivery M4).** Owner may photograph or attach a prescription label for reference alongside manual entry. **No on-device text extraction in M1.5**; pre-fill of medication name, dose, etc. ships under the M4 OCR gate (ML Kit, local-only per ADR-0003). Do not block ADR-0007 travel/DST work. | Operator product intent 2026-05-27, ROADMAP M4, feasibility dossier Slice 4, ADR-0001 |

---

## Milestone 2: Expansion

**Ship-ability:** public release (closed beta or open Play Store).

| Pending | What |
|---|---|
| | Argon2id KDF replacing PBKDF2 in backup codec (v0.1-followups #7) |
| ✓ | **Bottom nav order: Pets after Today.** Reorder `BottomNavItem` from Today → Reminders → Pets → Settings to **Today → Pets → Reminders → Settings**. Document primary pathways in this row: **Today** = dose logging + filter; **Pets** = pet hub (detail, meds, schedules); **Reminders** = schedule catalog (B6/B7); **Settings** = data/backup/theme. Reminders stays a tab (no code deletion); position change only. | Operator product intent 2026-05-27 |
| | **Pets tab IA (manage, not acquire).** De-emphasize or remove the prominent **Add pet** extended FAB for the common case (adding pets is rare). Center the tab on **existing pets**: richer list rows (species/age, active med count or next-dose hint), tap → Pet Detail; secondary paths to view schedule, edit pet, add medication, add schedule. **Add pet** moves to overflow / empty-state / Settings, not the primary visual anchor. Proposed layout: hero list → per-pet card → Pet Detail as hub; optional compact "+" in top bar for add. Full visual design deferred; this row captures IA intent only. | Operator product intent 2026-05-27 |
| | **Reminders tab: product audit (decision row).** Before M2 public polish, audit what **Reminders** shows vs **Today** (due doses + logged today) and **Pets** (per-pet med/schedule hub). Capture operator decision in `research/decisions/` or daily log: **(A)** merge unique Reminders value into Today/Pets and demote tab, **(B)** keep tab but repurpose copy/scope, or **(C)** keep as schedule catalog with clearer differentiation. **Do not remove the Reminders route in code** from this row alone; implementation follows the chosen option. | Operator product intent 2026-05-27 |
| | "Show next 30 days" schedule view |
| | Import-from-vet-record flow (manual paste; on-device OCR pre-fill deferred to milestone 4; see M4 OCR row and M1.5 label-capture pointer) |
| | **Add Medication unified modal (M2 polish if not landed in M1.2).** Same scope as the M1.2 row: one modal for med + schedule + alerts; name + dose amount required only. If the unified flow ships during internal beta, strike this duplicate; otherwise treat as pre–public-release UX gate. | Operator product intent 2026-05-27, M1.2 unified-modal row |
| | Caregiver share: read-only invite via QR + E2EE handoff (not cloud sync) |
| | **Document timeline** (Slice 3 per dossier § 8.2). Owner attaches photos and scans of vet invoices, lab reports, discharge instructions, and prescription labels to the pet profile. Plain storage with manual entry of structured fields where the owner wants them; no OCR yet (OCR is M4). Per ADR-0019, imported vet-written content surfaces verbatim with attribution to the source document; no app paraphrasing. |
| | **Vet visit and appointment reminders** as first-class domain (new `VetVisit` model; vaccine reminders are a subtype). Owner-entered scheduled followups ("recheck in 6 weeks," "annual rabies booster"). Per ADR-0019: owner-entered or vet-record-imported source, scheduled-reminder delivery, attribution framing. Domain model lands under ADR-0019 Followup F5; UI layers on after. |
| | **AAHA-2025-aligned referral packet builder** (Moat #3 per dossier § 3.3). Owner-controlled packet (records + reminders + diagnostics + recent dose history) shareable to a specialty clinic via deep-link export; no server, aligns to ADR-0003 local-first. AAHA 2025 § 4 shared-portal model fits the owner-controlled vet-curated posture of ADR-0019. |
| | History view with adherence stats (local computation only; no analytics) |
| | Macrobenchmark CI matrix expanded to 4 reference devices |
| | SLSA L1 provenance (GitHub Actions OIDC) |

---

## Milestone 3: Hardening

**Ship-ability:** scale to 100s of users; reduced support burden.

| Pending | What |
|---|---|
| | Pitest mutation testing wired in (deferred from v0.1 per ADR-0006) |
| | License audit gate |
| | Translatable strings; 3 initial locales (EN, ES, JA) |
| | Accessibility audit (TalkBack, font scaling, contrast) |
| | iOS placeholder build target validated (no UI yet) |

---

## Milestone 4: Smarter

**Ship-ability:** product differentiation tier.

| Pending | What |
|---|---|
| | **On-device OCR for prescription labels (ML Kit; ADR-0001 reference).** Read prescription label text on-device (no cloud) to pre-fill medication name, dose amount, and optional structured fields; owner confirms before persist. **Decision gate:** feasibility dossier Slice 4 pilot on real labels; if extraction quality is unacceptable, defer rather than add a cloud harness (ADR-0003). Supersedes manual-only label attach from M1.5 pointer row. |
| | Drug-interaction warnings: STRICTLY rule-based, vet-curated, NO LLM (forbidden by AGENTS.md vibe-impossible). Now derives from ADR-0019 § Specific application; vet-advisory-board-curated rule pack source. |
| | Vaccination & visit reminders alongside medications. Note: M2 lands the `VetVisit` domain model and owner-entered/vet-record-imported reminders. M4 work here is the **rule-pack delivery** layer (e.g., regional-regulatory rabies-booster rules per ADR-0019 Followup F3 rule-pack storage). |
| | **Chronic-condition admin overlays** (Moat #4 per dossier § 3.3; framework per ADR-0019). Per-condition rule-based reminder packs (CKD, diabetes, seizure, atopic dermatitis) curated by a registered veterinary advisory board. Gated on advisory-board ADR (ADR-0019 Followup F2) and rule-pack storage ADR (ADR-0019 Followup F3). |

---

## Milestone 5: iOS

**Ship-ability:** cross-platform.

| Pending | What |
|---|---|
| | iOS source sets unlocked (`toebeans.enableIosTargets=true`) |
| | iOS notification actuator (UserNotifications framework) |
| | iOS backup codec (`SecKey` / `CommonCrypto`) |
| | App Store Connect setup |

---

## Milestone 6: Optional cloud sync (Plus tier)

**Ship-ability:** revenue tier.

**HARD GATES per Wei's anti-surveillance global rule (2026-05-15):**

- Client-side E2EE only. Keys NEVER leave the device.
- Server holds opaque ciphertext blobs. No server-side graph queries possible.
- Threat model documented in a dedicated ADR. User-facing eyes-open opt-in.
- No third-party analytics SDKs on the cloud path.
- Forbidden: plaintext PII in cloud DBs, server-side relationship graphs (even tokenized), cloud backups without client-side encryption.

| Pending | What |
|---|---|
| | New ADR for the E2EE protocol (likely double-ratchet or simpler symmetric envelope) |
| | Per-device key derivation (no cross-device key sharing without user action) |
| | Server is a dumb blob store; no business logic |
| | Subscriber-side: monthly fee covers storage + ops, NOT data monetization |

---

## Milestone 7: Revenue tier (claim packets + subscription)

**Ship-ability:** revenue-generating users.

Slice 6 from the feasibility dossier § 8.2. Not gated on M6 cloud sync; claim packets are local-generation-then-shared-by-owner, which fits ADR-0003 local-first. Most likely follows M2 (caregiver share + document timeline) and M4 (chronic-condition overlays) rather than M6.

| Pending | What |
|---|---|
| | **Claim packet builder** for top reimbursement insurers (Lemonade Pet, Embrace per dossier § 8.2). Owner-controlled packet generation from `Pet` + `Medication` + `Schedule` + `DoseEvent` + imported vet records (Slice 3); export as PDF or insurer-required format. Per ADR-0019: vet-written content surfaces verbatim from imported documents. |
| | **Subscription tier (Plus).** Unlocks claim-packet-builder, multi-recipient caregiver-share, multi-device sync (if M6 ships). Free tier retains medication reminders + single-pet + JSON backup + document-timeline storage. |
| | **Per-insurer adapter map** (Moat #1 per dossier § 3.3). Initial adapters for Lemonade + Embrace; expansion to Healthy Paws, ASPCA, Nationwide as separate sub-tasks. Adapter coverage is the moat; per-insurer reverse-engineering of submission formats is real ongoing work. |
| | **Denial-recovery playbook content** (Moat #1 supporting). Vet-advisory-board-curated rule packs (per ADR-0019 § 3 source pathway) for common denial reasons + recommended owner responses. Gated on ADR-0019 Followup F2 (advisory board) and F3 (rule-pack storage). |

**Definition of done (milestone 7):**
- At least one tester has used the claim-packet builder to file a real reimbursement claim and the claim was paid.
- Subscription tier ships through Play Console with the free-tier feature carve-out enforced.
- Two insurer adapters (Lemonade + Embrace) are functional; the framework for adding more is documented.

---

## Vibe-impossible (will not ship in any milestone)

Per `AGENTS.md`:

- AI symptom checker / dose recommender / interaction warner from an LLM.
- Pre-generating DoseEvents for an entire phase at schedule creation (write storm).
- Removing the AlarmManager fallback in favor of WorkManager only.
- Adding a network library or analytics SDK to v1.
- Any permission outside the AndroidManifest allowlist.
- Plaintext-PII cloud paths (per Wei's anti-surveillance rule).

---

## Watch items (track but don't act yet)

These are scheduled chores that don't fit a milestone but must not get lost. Each carries an explicit "act when" trigger.

| Item | Act when | Source |
|---|---|---|
| **Calibration tier recalibration.** AGENTS.md § Confidence-score rule mandates recalibration of tier thresholds based on score-vs-incident correlation. | **TRIGGER FIRED 2026-05-19**: calibration log at 51+ entries. Routed to meta v1 chat (process-of-the-week review). Review the sub-floor vibe-dangerous entries to date, correlate against any field/CI incidents, decide whether tier floors should be adjusted up or down. Recalibration outcome lands as an ADR amendment. | `AGENTS.md` § Confidence-score rule |
| **ADR-0014 hook-tightening decision.** Whether to mechanical-block sub-floor commits at the pre-commit hook. | M1.2 day-14 retention gate. Use 30-day soak signal to decide. | `docs/adr/0014-pre-commit-hook-score-floor-enforcement.md` |
| **ADR-0015 KGP/AGP suppression revisit.** Re-check whether the Kotlin/AGP advisory warning suppression is still safe. | (a) AGP advances to 9.x, OR (b) KGP version-matrix catches up to AGP 8.7+, OR (c) any KMP/CMP-specific CI failure surfaces. | `docs/adr/0015-kotlin-agp-compatibility-warning-suppression.md` |
| **ADR-0013 Compose UI test deps.** Currently Proposed pending human review of 4 AndroidX deps. | When first Compose-surface regression is shipped (or when v0.1-followups #1 night-dose-banner snapshot test is needed). | `docs/adr/0013-compose-ui-test-adoption.md`, v0.1-followups #1 |
| **Calibration tier recalibration, round 2.** Round 1 (2026-05-19) recalibrated thresholds against the 51-entry baseline and produced D1 to D8 directives plus the D5 watch item this row records. Round 2 compares against round 1 to detect tier-floor drift and decides whether to expand the rubric to 10 components. | Calibration log reaches 100 entries (currently at 55 as of 2026-05-19). Route to a fresh meta chat for process-of-the-week review. Compare round-2 distributions against round-1 (see `localonly/meta/2026-05-19-calibration-recalibration-report.md`) to detect tier-floor drift. If the D1 em-dash pre-commit gate has shipped, evaluate post-gate slip rate against the round-1 baseline of approximately 3 to 4 em-dash slips per 6-PR day; if the rate is near zero, defer rubric expansion permanently; if the rate is still non-zero, propose the 10th rubric component (writing-style hygiene) at that recalibration. | `localonly/meta/2026-05-19-calibration-recalibration-report.md` (meta-v1 round-1 recalibration), Wei directive D5 |

---

## Cross-reference

- ADRs: `docs/adr/`
- ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`): the framework all vet-recommended content in M2 (vet visits, AAHA referral, document timeline) and M4 (chronic-condition overlays, drug interactions, vaccination rule packs) derives from. Followups F1-F5 are gating dependencies for those rows.
- Feasibility dossier (`research/00-feasibility-dossier.md`): the broader product vision (six-slice build order in § 8.2, moat candidates in § 3.3). When this ROADMAP and the dossier diverge, ROADMAP wins for short-horizon planning; the dossier wins for long-horizon strategic posture.
- v0.1 follow-ups (granular issue tracker): `docs/issues/v0.1-followups.md`
- Test-as-spec contract: `shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt`
- Vibe-tier table: `AGENTS.md` (and `CLAUDE.md`, kept in parity)
