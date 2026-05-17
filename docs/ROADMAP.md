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
| ✓ | `SchedulePhaseRulesTest` test-as-spec, 9 tests, all green |
| ✓ | Backup codec (`BackupCipher` PBKDF2 + AES-256-GCM, expect/actual JVM+Android) + 15 tests |
| ✓ | Notification actuator (`AndroidNotificationActuator` + boot receiver) + 9 Robolectric tests |
| ✓ | 5 fitness functions (no-network, no-analytics, scheduler-purity, permission-allowlist, AGENTS/CLAUDE parity) |
| ✓ | GitHub Actions CI (fitness + lint + tests + Android assemble) |
| ✓ | 6 ADRs (KMP+Compose, AlarmManager hybrid, local-first, tapering model, vibe-dangerous reminder firing, Kover deferred-pitest) + 2 new (ADR-0007 timezone, ADR-0008 perf-class) |
| ✓ | Vibe-dangerous pre-commit hook + `.codeit/calibration.jsonl` audit log |

---

## Milestone 1 — MVP (the "one user can use this" version)

**Ship-ability:** you + 1–3 trusted testers.

| Pending | What | Source |
|---|---|---|
| | Remove `ignoreFailures = true` in `shared/build.gradle.kts` | Hand-back item 3 |
| | Raise `koverVerify` line-coverage threshold 0 → 85 | Hand-back item 4 |
| | SQLDelight repositories (`PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`) | Persistence layer |
| | Real `DoseAlarmReceiver` DB lookup (replaces placeholder) | v0.1-followups #3 |
| | Boot-time scheduler rehydration in `ToebeansApp` | v0.1-followups #4 |
| | PendingIntent collision mitigation (monotonic int counter) | v0.1-followups #5 |
| | Compose UI: Pet list, Add Pet, Add Medication (with anchor-mode prompt per ADR-0007), Add Phase, Reminder List, Schedule Detail |
| | Midnight-mode UX warning during phase creation | v0.1-followups #1 |
| | Inline error UI when calculator throws `MalformedScheduleException` | from D3 decision |
| | Backup export UI (with passphrase entry) + import flow |
| | First macrobenchmark module (cold-start, list scroll, calculator perf) | ADR-0008 |
| | DI graph (Koin) wiring repositories + actuator |
| | Crash-on-render-of-stale-event safety net (defensive against bug-leak) |

**Definition of done (milestone 1):**
- A real pet owner can install the app, add a pet, add a medication with phases, and the alarms fire correctly.
- 30-day soak test on a non-OEM device (Pixel a-series) passes with zero missed alarms.
- All 9 `SchedulePhaseRulesTest` pass.
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
