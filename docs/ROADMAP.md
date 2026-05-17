# toebeans roadmap

> A **milestone** is "what subset of users could use the app without hitting bugs."
> Not "feature richness." Not "completeness." Just "ship-ability."

This document is the canonical answer to "what's feasible now vs what's deferred." When in doubt, this file wins over chat-history claims.

Last updated: 2026-05-16.

---

## v0.1 — Scaffold (current)

**Ship-ability:** zero users. Engineering artifact only.

| Done | What |
|---|---|
| ✓ | KMP + Compose-Multiplatform Gradle scaffold (JDK 17, Kotlin 2.0.21, AGP 8.7) |
| ✓ | Domain models (`Pet`, `Medication`, `Schedule`, `SchedulePhase`, `DoseEvent`) with validation |
| ✓ | SQLDelight schema for the same |
| ✓ | `ScheduleCalculator` interface + `DefaultScheduleCalculator` full impl |
| ✓ | `SchedulePhaseRulesTest` test-as-spec, **15 cases**, all green |
| ✓ | Backup codec (`BackupCipher` PBKDF2 + AES-256-GCM, expect/actual JVM+Android) + 15 tests |
| ✓ | Notification actuator (`AndroidNotificationActuator`) + 9 Robolectric tests. **Boot receiver not yet wired — see M1.** |
| ✓ | 6 fitness functions (no-network, no-analytics, scheduler-purity, permission-allowlist, no-PII-in-crash-log per ADR-0009, AGENTS/CLAUDE parity) |
| ✓ | GitHub Actions CI (fitness + lint + tests + Android assemble) |
| ✓ | **9 ADRs** (KMP+Compose, AlarmManager hybrid, local-first, tapering model, vibe-dangerous reminder firing, Kover deferred-pitest, timezone/travel-mode, perf-class, local-crash-log-no-telemetry) |
| ✓ | Vibe-dangerous pre-commit hook + `.codeit/calibration.jsonl` audit log |
| ✓ | Kover line-coverage gate at 85% on `scheduler/` + `backup/` (was 0 at scaffold time) |
| ✓ | DI smoke test (`AppModuleSmokeTest`) resolves every ViewModel through Koin — catches missing-binding bugs that compile cleanly but crash at app launch |
| ✓ | Pet + medication delete affordances (top-bar action + confirmation dialog) with VM-level tests |
| ✓ | `DoseEvent.medicationId` denormalization (model + SQLDelight schema + repo signatures + fake + UI plumbing) — drops the seed-only `replaceFirst("sched-","med-")` join, fixes the Logged Today retrospective for user-created medications |
| ✓ | Local crash-log capture (`LocalCrashLog` + `Settings → Export crash log`) per ADR-0009 — no telemetry, user-initiated share, 7 unit tests covering write/rotate/delegation/IO-failure |
| ✓ | `ignoreFailures = true` removed from `shared/build.gradle.kts` and `androidApp/build.gradle.kts`. Test failures are now fatal at every layer (local + CI). Hand-back item 3 closed. |

---

## Milestone 1 — MVP (the "one user can use this" version)

**Ship-ability:** you + 1–3 trusted testers.

| Pending | What | Source |
|---|---|---|
| | SQLDelight repositories (`PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`) | Persistence layer |
| | **Schedule delete affordance.** Pet + medication delete shipped with confirmation dialogs (top-bar action on the edit screens, hard-delete via fake repo today, soft-delete via `archivedAt`/`discontinuedAt` once SQLDelight lands). Schedule delete requires a schedule detail/edit screen that does not yet exist; building it is its own work item. | Cold review, P2 (partial: pet + med done) |
| | Real `DoseAlarmReceiver` DB lookup (replaces `ScheduledReminder(scheduleId = "", ...)` placeholder) | v0.1-followups #3 |
| | `BootReceiver` declared in manifest + rehydrate 72h-horizon alarms in `ToebeansApp` boot path. Until this lands, the `RECEIVE_BOOT_COMPLETED` permission is consumer-less. | v0.1-followups #4 |
| ✓ | PendingIntent collision mitigation — `RequestCodeAllocator` (SharedPreferences-backed, strictly monotonic Int) replaces `reminderId.hashCode()` in `AndroidNotificationActuator`. Regression test uses the canonical Java "Aa" / "BB" hash-collision pair to prove independent scheduling. 10 unit tests on the allocator + 1 actuator regression case. | v0.1-followups #5 |
| | Compose UI: Add Medication (with anchor-mode prompt per ADR-0007), Reminder List, Schedule Detail |
| | Midnight-mode UX warning during phase creation | v0.1-followups #1 |
| | Inline error UI when calculator throws `MalformedScheduleException` | from D3 decision |
| | Backup export UI (with passphrase entry) + import flow. Until this lands, the Settings → Export-data button is disabled with a "coming soon" affordance — DO NOT re-enable the toast version. | Cold review, P2 |
| | First macrobenchmark module (cold-start, list scroll, calculator perf) | ADR-0008 |
| ✓ | Crash-on-render-of-stale-event safety net — `StaleEventGuard` wired into `HomeViewModel.joinToUiState` + `computeDueToday`. Debug builds throw `IllegalStateException` with a diagnostic message naming the site + event + missing field, surfacing future join bugs in CI. Release builds log via `Log.w` and skip the row so inter-Flow races during deletion don't crash a tester. Three legacy "skipped silently" tests rewritten to assert the new contract; one new `StaleEventGuardTest` (3 cases) pins the message format + throw behavior. AGP `buildConfig = true` enabled to access `BuildConfig.DEBUG` — feature flag, no new dep. |
| ✓ | `scripts/test_no_pii_in_crash_log.sh` fitness function: greps the crash-handler source for any reference to repository / dao / model / persistence symbols so the local-crash-log handler (ADR-0009) cannot drift toward leaking domain data into the log. Wired into CI as the 5th gate; self-test verified it catches an injected `Pet` reference. | Cold review |

### Recommended M1 sequencing

M1 is a **multi-session effort**, not a single push. Each item below is its own commit-or-PR scope. Sequencing reflects dependency order and risk:

1. **Tier A cheap independent wins** (this work block): PendingIntent collision mitigation, first-launch seed gate, stale-event safety net, macrobench module. None depend on SQLDelight; each lands as its own commit. The `ignoreFailures = true` removal that was step 1 has already shipped.
2. **Tier B Compose UI surface** against the existing in-memory fakes: Reminder List, Schedule Detail (+ delete affordance), inline `MalformedScheduleException` error UI, midnight-mode UX warning, Backup export UI with passphrase entry. The DI swap from fakes → SQLDelight in step 3 is then a single Koin module edit.
3. **SQLDelight repositories** (`PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`). All downstream items (4, 5) depend on real persistence — the in-memory fakes return empty state in a separate-process `BroadcastReceiver`, so the `DoseAlarmReceiver` DB lookup cannot ship against them. Most expensive single item in M1; budget 2-3 days.
4. **Real `DoseAlarmReceiver` DB lookup**. Vibe-dangerous; needs the SQLDelight layer from (3). Pair with a test-as-spec for receiver-side lookup that matches a known DoseEvent ID.
5. **`BootReceiver` + 72h-horizon rehydration.** The most safety-critical work in M1 — determines whether alarms survive device reboots. ADR-0005 vibe-dangerous + needs its own ADR amendment for the rehydration window choice. Restore `RECEIVE_BOOT_COMPLETED` to the manifest as part of this commit.

Each item ends with: full gate green + calibration entry + ADR amendment if it touched a vibe-dangerous surface. Do not batch.

**Definition of done (milestone 1):**
- A real pet owner can install the app, add a pet, add a medication with phases, and the alarms fire correctly.
- 30-day soak test on a non-OEM device (Pixel a-series) passes with zero missed alarms.
- All 15 `SchedulePhaseRulesTest` cases pass, plus any new DST/anchor-mode cases added in M1.5.
- All fitness functions pass with no `continue-on-error`.

---

## Milestone 1.2 — Internal beta + decision gate

**Ship-ability:** the developer and 1–3 trusted testers, distributed via Play Store internal-testing track. NOT public.

This milestone sits between M1's "feature complete" and M1.5's "travel-aware" because the internal-beta loop is what tells us whether we've actually built the right thing before we burn time on the harder edge cases.

| Pending | What | Source |
|---|---|---|
| | Play Store internal-testing track set up with 1–3 testers (developer + close circle). **Walkthrough doc shipped at `docs/play-store-internal-testing-walkthrough.md` (8 phases, ~3-5 hours wall-clock + 1-3 day ID verification wait). Execution is a human task — Cascade cannot click through Play Console.** | Cold review |
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

## Milestone 1.5 — Travel + DST aware

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

## Milestone 2 — Expansion

**Ship-ability:** public release (closed beta or open Play Store).

| Pending | What |
|---|---|
| | Argon2id KDF replacing PBKDF2 in backup codec (v0.1-followups #7) |
| | "Show next 30 days" schedule view |
| | Import-from-vet-record flow (manual paste; OCR deferred to milestone 4) |
| | Caregiver share — read-only invite via QR + E2EE handoff (not cloud sync) |
| | History view with adherence stats (local computation only; no analytics) |
| | Macrobenchmark CI matrix expanded to 4 reference devices |
| | SLSA L1 provenance (GitHub Actions OIDC) |

---

## Milestone 3 — Hardening

**Ship-ability:** scale to 100s of users; reduced support burden.

| Pending | What |
|---|---|
| | Pitest mutation testing wired in (deferred from v0.1 per ADR-0006) |
| | License audit gate |
| | Translatable strings; 3 initial locales (EN, ES, JA) |
| | Accessibility audit (TalkBack, font scaling, contrast) |
| | iOS placeholder build target validated (no UI yet) |

---

## Milestone 4 — Smarter

**Ship-ability:** product differentiation tier.

| Pending | What |
|---|---|
| | On-device OCR for prescription labels (ML Kit; ADR-0001 reference) |
| | Drug-interaction warnings — STRICTLY rule-based, vet-curated, NO LLM (forbidden by AGENTS.md vibe-impossible) |
| | Vaccination & visit reminders alongside medications |

---

## Milestone 5 — iOS

**Ship-ability:** cross-platform.

| Pending | What |
|---|---|
| | iOS source sets unlocked (`toebeans.enableIosTargets=true`) |
| | iOS notification actuator (UserNotifications framework) |
| | iOS backup codec (`SecKey` / `CommonCrypto`) |
| | App Store Connect setup |

---

## Milestone 6 — Optional cloud sync (Plus tier)

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

## Vibe-impossible (will not ship in any milestone)

Per `AGENTS.md`:

- AI symptom checker / dose recommender / interaction warner from an LLM.
- Pre-generating DoseEvents for an entire phase at schedule creation (write storm).
- Removing the AlarmManager fallback in favor of WorkManager only.
- Adding a network library or analytics SDK to v1.
- Any permission outside the AndroidManifest allowlist.
- Plaintext-PII cloud paths (per Wei's anti-surveillance rule).

---

## Cross-reference

- ADRs: `docs/adr/`
- v0.1 follow-ups (granular issue tracker): `docs/issues/v0.1-followups.md`
- Test-as-spec contract: `shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt`
- Vibe-tier table: `AGENTS.md` (and `CLAUDE.md`, kept in parity)
