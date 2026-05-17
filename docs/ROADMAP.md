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
| ✓ | 5 fitness functions (no-network, no-analytics, scheduler-purity, permission-allowlist, AGENTS/CLAUDE parity) |
| ✓ | GitHub Actions CI (fitness + lint + tests + Android assemble) |
| ✓ | **8 ADRs** (KMP+Compose, AlarmManager hybrid, local-first, tapering model, vibe-dangerous reminder firing, Kover deferred-pitest, timezone/travel-mode, perf-class) |
| ✓ | Vibe-dangerous pre-commit hook + `.codeit/calibration.jsonl` audit log |
| ✓ | Kover line-coverage gate at 85% on `scheduler/` + `backup/` (was 0 at scaffold time) |
| ✓ | DI smoke test (`AppModuleSmokeTest`) resolves every ViewModel through Koin — catches missing-binding bugs that compile cleanly but crash at app launch |

---

## Milestone 1 — MVP (the "one user can use this" version)

**Ship-ability:** you + 1–3 trusted testers.

| Pending | What | Source |
|---|---|---|
| | Remove `ignoreFailures = true` in `shared/build.gradle.kts` | Hand-back item 3 |
| | SQLDelight repositories (`PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`) | Persistence layer |
| | `DoseEvent.medicationId` schema column + repo contract change. Drops the `replaceFirst("sched-", "med-")` string-munge join in `HomeViewModel.joinToUiState`, which currently only resolves for the seeded Luna pair and silently drops every user-created medication's dose from the Logged Today card. | Cold review, P1 |
| | **Schedule delete affordance.** Pet + medication delete shipped with confirmation dialogs (top-bar action on the edit screens, hard-delete via fake repo today, soft-delete via `archivedAt`/`discontinuedAt` once SQLDelight lands). Schedule delete requires a schedule detail/edit screen that does not yet exist; building it is its own work item. | Cold review, P2 (partial: pet + med done) |
| | Real `DoseAlarmReceiver` DB lookup (replaces `ScheduledReminder(scheduleId = "", ...)` placeholder) | v0.1-followups #3 |
| | `BootReceiver` declared in manifest + rehydrate 72h-horizon alarms in `ToebeansApp` boot path. Until this lands, the `RECEIVE_BOOT_COMPLETED` permission is consumer-less. | v0.1-followups #4 |
| | PendingIntent collision mitigation (monotonic int counter) | v0.1-followups #5 |
| | Compose UI: Add Medication (with anchor-mode prompt per ADR-0007), Reminder List, Schedule Detail |
| | Midnight-mode UX warning during phase creation | v0.1-followups #1 |
| | Inline error UI when calculator throws `MalformedScheduleException` | from D3 decision |
| | Backup export UI (with passphrase entry) + import flow. Until this lands, the Settings → Export-data button is disabled with a "coming soon" affordance — DO NOT re-enable the toast version. | Cold review, P2 |
| | First macrobenchmark module (cold-start, list scroll, calculator perf) | ADR-0008 |
| | Crash-on-render-of-stale-event safety net (defensive against bug-leak) |
| | **Local crash log** captured via `Thread.setDefaultUncaughtExceptionHandler` to app-private `filesDir`, exposed via Settings → Export logs. Required because local-first + no-analytics means production exceptions are otherwise silent. New ADR. | Cold review |
| | **Internal-testing track on Play Store** with 1–3 testers + a written soak-test protocol (30 days, Pixel a-series). Definition-of-done for the rest of M1 hangs off this loop. | Cold review |

**Definition of done (milestone 1):**
- A real pet owner can install the app, add a pet, add a medication with phases, and the alarms fire correctly.
- 30-day soak test on a non-OEM device (Pixel a-series) passes with zero missed alarms.
- All 15 `SchedulePhaseRulesTest` cases pass, plus any new DST/anchor-mode cases added in M1.5.
- All fitness functions pass with no `continue-on-error`.

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
