# toebeans — MVP design doc

**Date:** 2026-05-14
**Status:** DRAFT — pending user approval
**Author:** Cascade (working session w/ Wei Jia)
**Skill provenance:** `brainstorming` → this doc → `code-helper plan-new-app` → scaffold
**Companion:** see `@research/00-feasibility-dossier.md` for market/competitive/M&A evidence

---

## 1. Product summary

**toebeans** is the pet admin layer. v1 ships **medication and dose reminders with adherence logging** for multi-pet households, Android-first, local-only, on Kotlin Multiplatform / Compose Multiplatform. No AI. No cloud. No symptom checker. No diagnosis. The reminder-firing path is **vibe-dangerous** (medication-critical) and gated accordingly.

### Falsifiable success criteria (code-helper §6.1)

1. **Reliability:** zero missed reminders in a 30-day soak test on a single non-OEM-customized Android device with battery optimization disabled.
2. **Latency:** reminders fire within ±60 seconds of scheduled time across the soak test.
3. **Survivability:** all pet, schedule, and dose-event data survives app reinstall via at least one of the user-selected backup mechanisms.

No user-acquisition or revenue criteria in scope for slice 1.

---

## 2. Stack decision (code-helper §2)

| Layer | Chosen | Rejected | 1-sentence why each |
|---|---|---|---|
| Core language | **Kotlin** | Java | Null-safety + coroutines; KMP gating |
| UI | **Compose Multiplatform** (Android first) | Native Android Views + UIKit | Single UI codebase; Compose-iOS stable May 2025 [verified] |
| Persistence | **SQLDelight (KMP-shared)** | Room KMP (still experimental 2025), Realm, Firebase | Boring SQLite; KMP-native; type-safe queries; works on Android + iOS |
| Background work | **WorkManager + AlarmManager (setExactAndAllowWhileIdle)** | Pure WorkManager | AlarmManager required for medication-critical timing on Android 12+ |
| DI | **Koin (KMP-native)** | Hilt (Android-only), Kodein | KMP-compatible, light, no kapt |
| Date/Time | **kotlinx-datetime** | java.time directly | KMP-compatible; consistent timezone handling |
| Build | **Gradle Kotlin DSL + version catalogs** | Maven, Bazel | Standard for KMP |
| Testing | **kotlin.test + Turbine (Flow) + Robolectric (Android) + Espresso (instrumentation)** | JUnit-only | KMP-friendly; Turbine for Flow assertions |
| Lint/format | **ktlint + detekt** | spotbugs, custom | Boring; CI-enforceable |

**DB:** SQLite via SQLDelight. **Infra:** none (local-only). **No cloud** in v1.

ADRs to write at scaffold time:
- ADR-0001: KMP + Compose Multiplatform vs native-only
- ADR-0002: AlarmManager + WorkManager hybrid for medication reminders
- ADR-0003: Local-first, no cloud, backup posture (manual + opt-in Auto Backup)
- ADR-0004: Tapering schedule model (`SchedulePhase` chain)
- ADR-0005: Vibe-dangerous classification of reminder-firing path

---

## 3. Repo tree

```
toebeans/
  .codeit/                       # code-helper state (created on first run)
  README.md
  CHANGELOG.md
  AGENTS.md                      # vibe-coding host contract
  CLAUDE.md                      # same content as AGENTS.md (symlink or copy)
  SECURITY.md
  LICENSE                        # decision deferred (see §11 in dossier)
  gradle/
    libs.versions.toml
    wrapper/
  build.gradle.kts
  settings.gradle.kts
  docs/
    superpowers/specs/           # this doc lives here
    adr/                         # MADR-short ADRs
    ARCHITECTURE.md
  research/
    00-feasibility-dossier.md
  shared/                        # KMP shared module
    src/commonMain/kotlin/app/toebeans/core/
      model/                     # Pet, Medication, Schedule, DoseEvent
      repository/                # PetRepository, ScheduleRepository, DoseEventRepository
      scheduler/                 # SchedulePhase logic; next-fire computation
      backup/                    # Export/import codec (encrypted JSON)
    src/commonTest/kotlin/
    src/androidMain/kotlin/      # AlarmManager + WorkManager glue
    src/iosMain/kotlin/          # placeholder for slice 5
  androidApp/
    src/main/kotlin/app/toebeans/android/
      ui/                        # Compose screens
      notifications/             # NotificationChannel, action handlers
      MainActivity.kt
      ToebeansApp.kt
    src/main/AndroidManifest.xml
    src/test/                    # unit tests (Robolectric)
    src/androidTest/             # instrumented tests
  scripts/
    soak_test_check.sh           # success-criterion 1 verifier
```

---

## 4. Architecture

```
+--------------------------------------------------+
|                Compose UI (androidApp)           |
|   Screens: Home / AddPet / AddMedication /       |
|            Schedule / DoseHistory / Settings     |
+----------------------+---------------------------+
                       |
                       v
+--------------------------------------------------+
|          Shared core (KMP, commonMain)           |
|   Repositories <-> Scheduler <-> Backup          |
|   Domain models: Pet, Medication, Schedule,      |
|                  SchedulePhase, DoseEvent        |
+--------+---------------------+-------------------+
         |                     |
         v                     v
+----------------+    +--------------------------+
| SQLDelight DB  |    |   Platform actuators     |
| (file-local)   |    |   Android:               |
|                |    |   - AlarmManager (exact) |
|                |    |   - WorkManager (recur)  |
|                |    |   - Notifications        |
|                |    |   iOS (slice 5):         |
|                |    |   - UNUserNotifications  |
+----------------+    +--------------------------+
```

**Ports-and-adapters?** No. We have one production adapter per platform (Android now; iOS later). Compose-MP + KMP already enforces the port boundary. A second adapter implementation would force the pattern; one does not (per code-helper §6.4).

---

## 5. Data model

Minimal MVP schema. All tables local, SQLite via SQLDelight.

```
Pet
  id              UUID PK
  name            TEXT NOT NULL
  species         TEXT NOT NULL  -- 'dog' | 'cat' at v1 (enum widens later)
  birthdate       DATE NULLABLE
  weight_kg       REAL NULLABLE
  notes           TEXT NULLABLE
  created_at      TIMESTAMP
  archived_at     TIMESTAMP NULLABLE  -- soft delete

Medication
  id              UUID PK
  pet_id          UUID FK -> Pet.id
  name            TEXT NOT NULL              -- 'Prednisone'
  dose_amount     TEXT NOT NULL              -- '10mg' (free text, not parsed at MVP)
  notes           TEXT NULLABLE
  created_at      TIMESTAMP
  discontinued_at TIMESTAMP NULLABLE

Schedule                            -- a "schedule program" attached to one medication
  id              UUID PK
  medication_id   UUID FK -> Medication.id
  start_date      DATE NOT NULL
  end_date        DATE NULLABLE     -- nullable = "until I stop it"
  created_at      TIMESTAMP

SchedulePhase                       -- ordered phases for tapering
  id              UUID PK
  schedule_id     UUID FK -> Schedule.id
  phase_order     INT NOT NULL      -- 0, 1, 2, ...
  duration_days   INT NOT NULL      -- e.g. 5
  doses_per_day   INT NOT NULL      -- 1, 2, 3, 4
  dose_times_local TEXT NOT NULL    -- JSON array of HH:MM in user's local time
  dose_amount     TEXT NULLABLE     -- override Medication.dose_amount for this phase

DoseEvent                           -- one row per fired-or-logged dose
  id              UUID PK
  schedule_id     UUID FK -> Schedule.id
  scheduled_at    TIMESTAMP NOT NULL
  fired_at        TIMESTAMP NULLABLE  -- when the notification actually fired
  resolved_at     TIMESTAMP NULLABLE  -- when user tapped 'Given' or 'Skipped'
  status          TEXT NOT NULL       -- 'pending' | 'given' | 'skipped' | 'missed'
  note            TEXT NULLABLE
```

**Notes on the model:**

- A taper is represented as a `Schedule` with N `SchedulePhase` rows in order.
- A single fixed-interval regimen is a `Schedule` with 1 `SchedulePhase`.
- `DoseEvent` rows are created **lazily** at scheduling time (next 72h horizon), not pre-generated for the entire phase. Avoids massive write storms for long phases.
- Missed-dose detection: a `pending` event becomes `missed` after 4h past `scheduled_at` (configurable; not user-facing in v1).
- Timezone discipline: `dose_times_local` is local wall-clock. Computation of `scheduled_at` happens in the device's current timezone. DST transitions surface as a known issue; mitigation in ADR-0004.

### Data classification (LINDDUN-lite)

| Field | Classification | Treatment |
|---|---|---|
| Pet name, species, birthdate, weight | non-PII pet attribute | local-only |
| Medication name, dose | quasi-medical | local-only; not transmitted in v1 |
| DoseEvent times | behavioral pattern | local-only |
| User PII | **none collected in v1** | n/a |
| Device identifiers | none | n/a |

No PII collected. No analytics. No crash reporting in v1 (revisit at pre-distribution tier).

---

## 6. Deployment model

| Environment | Channel | Purpose |
|---|---|---|
| local-debug | `./gradlew installDebug` | dev |
| internal-test | Play Console internal test track | future, slice 6 |
| production | Play Store closed/open testing → production | future |

**Secrets:** none in v1 (no cloud).
**CI steps (slice 1):**
1. ktlint
2. detekt
3. unit tests (commonTest + androidTest unit)
4. Robolectric tests
5. instrumented soak test (smoke, not full 30-day — see §7)
6. **fitness functions** (see §9)
7. Build debug APK

**SLSA target:** L1 at v1 (provenance generated by GitHub Actions); L2 is the goal at slice 6.

---

## 7. First five ADRs (titles + context)

1. **ADR-0001 — KMP + Compose Multiplatform.** Context: Android first, iOS preserved. Compose-MP stable for iOS as of May 2025. Single UI codebase outweighs maturity risk.
2. **ADR-0002 — AlarmManager + WorkManager hybrid for medication reminders.** Context: WorkManager alone misses Doze/standby exactness; AlarmManager.setExactAndAllowWhileIdle required. Permission UX must explain why.
3. **ADR-0003 — Local-first, no cloud in v1. User-controlled backup.** Context: privacy posture is a moat (dossier §3.3). Default = manual encrypted JSON export; opt-in = Android Auto Backup with sign-in disclosure.
4. **ADR-0004 — Tapering schedule model: Schedule + ordered SchedulePhase rows.** Context: covers fixed-interval and tapering uniformly. DoseEvents materialized lazily at 72h horizon.
5. **ADR-0005 — Vibe-dangerous classification for the reminder-firing path.** Context: medication adherence is health-critical. Per code-helper §1, this path requires human-written tests, human-read diffs, staged rollout. Test-as-spec is mandatory.

---

## 8. AGENTS.md / CLAUDE.md scaffold

```
# toebeans — agent host contract

## Posture
- Anti-enterprise default. Boring tech. Modular monolith (KMP shared + Android app).
- Reminder-firing path is VIBE-DANGEROUS. Treat per code-helper §1.
- Local-only. No network calls from v1 code. No analytics. No crash telemetry.
- No AI features. No symptom checker. No diagnostic content. No treatment recommendations.

## Review gates (require human approval)
- Any change to:
  - shared/.../scheduler/
  - shared/.../backup/
  - androidApp/.../notifications/
  - SQLDelight schema migrations
  - AndroidManifest permissions
  - Gradle dependency adds

## Test-as-spec
- Write the failing test FIRST for any reminder-firing or scheduler change.
- See docs/test_as_spec_examples.md for the test signature conventions.

## Vibe-safe surfaces (AI may ship unread if tests + lint + fitness functions green)
- UI screen layouts not touching scheduler
- String resources
- README/docs/ARCHITECTURE.md updates
- ADR additions (review by human at merge, not at edit)

## Vibe-impossible (refuse)
- Adding AI symptom checker.
- Adding diagnostic content.
- Pre-generating DoseEvents for an entire phase (write-storm).
- Removing the AlarmManager fallback in favor of WorkManager-only.

## Confidence-score rule
- Apply code-helper §5 every change. Log to .codeit/calibration.jsonl.
```

---

## 9. Fitness functions (≥3, code-helper §6.12)

Enforced in CI on every PR:

1. **No-network gate.** A unit test fails the build if any class in `commonMain/` or `androidMain/` (slice 1) imports `okhttp3`, `ktor`, `java.net.URL`, `HttpURLConnection`, `URLSession`, or similar. Implementation: `tests/test_no_network.sh` greps the dependency graph and source tree.
2. **No-analytics gate.** Build fails if Gradle dependency resolution surfaces Firebase, GA, Mixpanel, Segment, or any telemetry SDK. Implementation: dependency check on the Gradle classpath.
3. **Scheduler purity.** Unit test fails if any function in `shared/.../scheduler/` references a platform clock other than the injected `Clock` (kotlinx-datetime). Forces testability; prevents real-time leakage.
4. **Permission whitelist.** Lint task fails if AndroidManifest declares any permission not on the explicit allowlist: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`.
5. **Test coverage minimum on scheduler.** `shared/.../scheduler/` requires ≥85% line coverage. JaCoCo gate in CI.

---

## 10. Vibe-safety map (code-helper §6.10)

| Surface | Tier | Human review required? |
|---|---|---|
| `shared/.../scheduler/` | vibe-dangerous | yes (≥95 confidence per change) |
| `shared/.../backup/` (encryption + serialization) | vibe-dangerous | yes |
| `androidApp/.../notifications/` (NotificationActionReceiver) | vibe-dangerous | yes |
| AndroidManifest permission changes | vibe-dangerous | yes |
| SQLDelight schema migrations | vibe-dangerous | yes |
| `androidApp/.../ui/` Compose screens | vibe-safe | no (CI gates only) |
| Strings, themes, icons | vibe-safe | no |
| ADR doc writes | vibe-careful | yes at merge |
| README / ARCHITECTURE updates | vibe-careful | yes at merge |

**Vibe-impossible** (explicit refusal): symptom checker, diagnostic content, claim-amount calculation logic, anything implying medical advice.

---

## 11. Smells to watch in month 3 (code-helper §6.11)

1. **Manifest permission creep** — anything beyond the §9 allowlist.
2. **DoseEvent table growth without index maintenance** — slow Home screen.
3. **Snooze creep** — if we add snooze (deferred from MVP), watch for users using it to silence forever.
4. **Notification channel proliferation** — keep to 1–2 channels (medication-critical, general).
5. **OEM-specific battery workarounds in code** — keep these in one file, documented; do not scatter.

---

## 12. Threat model (STRIDE, code-helper §6.13)

| Threat | Surface | Mitigation | Tag |
|---|---|---|---|
| **S — Spoofing** | n/a (no auth in v1) | n/a | `[verified]` |
| **T — Tampering** | local DB; backup file | Manual export: file encrypted with AES-256-GCM, key derived (Argon2id) from a passphrase the user enters at export time and again at import time. Passphrase never persisted. DB is in app-private storage. Android Auto Backup: encryption provided by Android platform (per-user device-bound key). | `[verified]` |
| **R — Repudiation** | DoseEvent log | n/a (single-user, no claims to repudiate) | `[verified]` |
| **I — Info disclosure** | backup file leaked; device shared | Backup is encrypted with user passphrase (not stored). Settings warn before enabling Android Auto Backup that data lands in user's Google account. | `[verified]` |
| **D — Denial of service** | malicious notification flood on boot | RECEIVE_BOOT_COMPLETED handler reschedules only future events inside the 72h horizon; capped at `MAX_PENDING_PER_PET=64`. | `[verified]` |
| **E — Elevation of privilege** | exported components | All Activities/Receivers explicitly `android:exported="false"` except the NotificationAction receiver (intent-filter-restricted). | `[verified]` |

**Privacy (LINDDUN-lite):** see §5 data classification table. No data collection. No analytics. No transmission.

---

## 13. Pre-flight 10-question checklist (code-helper §6.9)

1. **Q:** What is the smallest reproducible failure that we cannot ship with? **A:** A pending DoseEvent that fails to fire within ±60s of `scheduled_at` on a non-OEM Android device.
2. **Q:** What is the user's biggest existing alternative? **A:** Phone calendar + manual entry; physical written log; nothing.
3. **Q:** What is the first thing that will break in production? **A:** OEM battery optimization on Xiaomi/OnePlus/Samsung silently killing AlarmManager.
4. **Q:** What is the most expensive code path? **A:** Boot-time reschedule of all active SchedulePhases. Bounded by 72h horizon × pets × meds; cheap.
5. **Q:** What is the slowest user interaction we tolerate? **A:** First app open after install with restored backup; target <2s for typical 1–3 pet load.
6. **Q:** What feature will users ask for that we'll refuse? **A:** Symptom checker / "is this dose safe" / drug interaction warning.
7. **Q:** What feature will users ask for that we'll defer to slice 2? **A:** Caregiver sharing; multi-device sync.
8. **Q:** What's the rollback story for a bad reminder bug? **A:** Play Store staged rollout (10% → 100%). Local backup means user data survives downgrade.
9. **Q:** What state matters across app reinstall? **A:** All Pet, Medication, Schedule, SchedulePhase rows. DoseEvent history >30 days is nice-to-have; <30 days is required.
10. **Q:** What is the one thing we will say "no" to in the next 6 months? **A:** Any AI / LLM feature.

---

## Approval

Wei Jia, please review and approve, request changes, or push back. Once approved, I will:
1. Apply `codeit --engagement-type new-app` over this design (it writes the launch-ready checklist + scoring inputs).
2. Scaffold the repo per §3 (Gradle, KMP, Compose, SQLDelight, DI, lint, fitness-function CI skeleton).
3. Write the first 5 ADRs to `docs/adr/`.
4. Write the first failing scheduler test (test-as-spec for tapering correctness).
5. Stop and hand back for next-decision review before writing scheduler implementation.

No code beyond the test will be written without your explicit approval (vibe-dangerous gate).
