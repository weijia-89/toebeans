# toebeans roadmap

> A **milestone** is "what subset of users could use the app without hitting bugs."
> Not "feature richness." Not "completeness." Just "ship-ability."

This document is the canonical answer to "what's feasible now vs what's deferred." When in doubt, this file wins over chat-history claims.

Last updated: 2026-05-19.

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
| ✓ | Notification actuator (`AndroidNotificationActuator`) + 9 Robolectric tests. **Boot receiver not yet wired; see M1.** |
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
| | SQLDelight repositories (`PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`) | Persistence layer |
| ✓ | **Schedule delete affordance.** Top-bar Delete action on the new Schedule Detail screen (B7) wires through to `ScheduleRepository.delete`, with the same confirmation-dialog pattern as the pet + medication delete flows (no undo snackbar, since delete-with-phases is destructive enough to warrant a hard confirm). The dialog mirrors the error-tinted Delete button + "this can't be undone" body copy. Hard-delete via fake repo today; **hard-delete with FK cascade once SQLDelight lands**, per ADR-0010 (`Schedule.medication_id` ON DELETE CASCADE, `SchedulePhase.schedule_id` ON DELETE CASCADE, `DoseEvent.schedule_id` ON DELETE CASCADE). The previous draft of this entry incorrectly described a soft-delete via `endDate` plan; `endDate` is the schedule's intended last dosing day, not a deletion tombstone. Corrected during B8 self-review. | Cold review, P2 |
| | Real `DoseAlarmReceiver` DB lookup (replaces `ScheduledReminder(scheduleId = "", ...)` placeholder) | v0.1-followups #3 |
| | `BootReceiver` declared in manifest + rehydrate 72h-horizon alarms in `ToebeansApp` boot path. Until this lands, the `RECEIVE_BOOT_COMPLETED` permission is consumer-less. | v0.1-followups #4 |
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
2. **Tier B Compose UI surface** against the existing in-memory fakes: Reminder List, Schedule Detail (+ delete affordance), inline `MalformedScheduleException` error UI, midnight-mode UX warning, Backup export UI with passphrase entry. The DI swap from fakes → SQLDelight in step 3 is then a single Koin module edit.
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

## Milestone 1.2: Internal beta + decision gate

**Ship-ability:** the developer and 1–3 trusted testers, distributed via Play Store internal-testing track. NOT public.

This milestone sits between M1's "feature complete" and M1.5's "travel-aware" because the internal-beta loop is what tells us whether we've actually built the right thing before we burn time on the harder edge cases.

| Pending | What | Source |
|---|---|---|
| | Play Store internal-testing track set up with 1–3 testers (developer + close circle). **Walkthrough doc shipped at `docs/play-store-internal-testing-walkthrough.md` (8 phases, ~3-5 hours wall-clock + 1-3 day ID verification wait). Execution is a human task; Cascade cannot click through Play Console.** | Cold review |
| ✓ | Written soak-test protocol for testers: 30-day run on a Pixel a-series device, daily log + weekly snapshots + day-30 structured report; alarm-fire reliability + crash-log capture + retention gate at day 14 (M1.2 definition-of-done). Shipped at `docs/soak-test-protocol.md`. | Cold review |
| | Adoption metric (read by the developer, NOT analytics): does at least one tester continue to use the app past day 14? If not, we have a retention problem upstream of feature work and M2 cannot proceed. | Cold review |
| | **Decision gate: AGPL-3.0 vs Apache-2.0 license posture.** This MUST be decided before M2 ships publicly. AGPL preserves the open-core moat but closes off most strategic-acquirer interest (Covetrus and IDEXX will not take AGPL into a closed product line); Apache+CLA preserves both options at the cost of weaker moat preservation. Feasibility dossier open question #2. Decision lives at the repo root LICENSE plus a short ADR-0010. | Cold review, feasibility dossier §11 |
| | **Decision gate: distribution wedge.** The dossier names four candidates (direct App Store growth, clinic partnerships, insurer co-marketing, rescue/foster licensing); pick ONE to invest in for M2. CAC for pet apps is $15–60; we cannot afford all four. | Cold review, feasibility dossier §4.6 |
| ✓ | First-launch UX revisit: gated the Rufus + Luna seed behind a first-launch dialog (`FirstLaunchDialogHost`). Stores now start empty; `loadDemoData()` populates the demo on the user's tap of "Load demo data". `FirstLaunchPreferences` persists the seen-flag in SharedPreferences (same pattern as `ThemePreferences`). 11 new unit tests (5 prefs + 6 demo-loader, including a user-created-entries-preserved case). | Cold review |

**Definition of done (milestone 1.2):**
- One tester has used the app for 30 consecutive days without losing trust.
- License decision is made and the LICENSE file reflects it.
- Distribution wedge is named and a M2 work item exists for it.
- Crash log export has been exercised by at least one tester (proves ADR-0009 works in the field).

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

---

## Milestone 2: Expansion

**Ship-ability:** public release (closed beta or open Play Store).

| Pending | What |
|---|---|
| | Argon2id KDF replacing PBKDF2 in backup codec (v0.1-followups #7) |
| | "Show next 30 days" schedule view |
| | Import-from-vet-record flow (manual paste; OCR deferred to milestone 4) |
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
| | On-device OCR for prescription labels (ML Kit; ADR-0001 reference) |
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

---

## Cross-reference

- ADRs: `docs/adr/`
- ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`): the framework all vet-recommended content in M2 (vet visits, AAHA referral, document timeline) and M4 (chronic-condition overlays, drug interactions, vaccination rule packs) derives from. Followups F1-F5 are gating dependencies for those rows.
- Feasibility dossier (`research/00-feasibility-dossier.md`): the broader product vision (six-slice build order in § 8.2, moat candidates in § 3.3). When this ROADMAP and the dossier diverge, ROADMAP wins for short-horizon planning; the dossier wins for long-horizon strategic posture.
- v0.1 follow-ups (granular issue tracker): `docs/issues/v0.1-followups.md`
- Test-as-spec contract: `shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt`
- Vibe-tier table: `AGENTS.md` (and `CLAUDE.md`, kept in parity)
