# Database audit + remediation plan — 2026-06-07

**Tier:** vibe-dangerous-adjacent (SQLDelight + medication-critical fire path)  
**Reviewer posture:** trainer → form-check + review-rigor  
**Scope:** SQLDelight schema (`*.sq`), repositories, `SqlDelightReminderLookup`, boot rehydrate

---

## 1. Audit summary

### Schema (4 tables, no migrations yet)

| Table | Rows purpose | FK chain |
|-------|--------------|----------|
| `Pet` | `archived_at` soft-delete | root |
| `Medication` | `discontinued_at` soft-retire | → Pet CASCADE |
| `Schedule` + `SchedulePhase` | dosing windows | → Medication CASCADE |
| `DoseEvent` | lazy 72h materialization + history | → Schedule, Medication CASCADE |

**Strengths [verified]:** PRAGMA foreign_keys=ON (ADR-0010); denormalized `medication_id` on DoseEvent; CHECK constraints on phase/dose counts; indexed fire-path `selectDoseEventById`.

**Gaps [verified]:**

| ID | Rubric | Finding |
|----|--------|---------|
| DB-01 | COR/P0 | ~~`SqlDelightReminderLookup` joins schedule only~~ **FIXED** — `selectDoseEventByIdIfChainActive` (2026-06-07). |
| DB-02 | COR/P0 | ~~boot rehydrate without active-chain filter~~ **FIXED** — `selectPendingDoseEventsInRangeActive` (2026-06-07). |
| DB-03 | ARC/P1 | `selectAllActiveSchedules` (joins discontinued + archived) exists in `Schedule.sq:63-68` but **`observeActiveWithPhases` uses `selectSchedulesActiveOnOrAfter`** without med/pet filter; UI compensates (`HomeViewModel.kt:225`). |
| DB-04 | COR/P3 | `dayInterval` not persisted; domain defaults to 1 (`SqlDelightScheduleRepository.kt:192`). |
| DB-05 | COR/P3 | `dose_amount` single TEXT; unit picker needs migration (Wave 2). |
| DB-06 | TST/P3 | ~~No contract test~~ **FIXED** — `ReminderLookupContract` + `BootReceiverTest` + `DoseAlarmReceiverLookupTest` discontinued case. |

### Bug inventory crosswalk

| Session ID | Maps to | In this plan |
|------------|---------|--------------|
| B-01 P0 | DB-01 + DB-02 | **Fix now** |
| B-02 P0 | unified modal save | Deferred (Wave 2 / M1.2) |
| B-03 P1 | permission gate | No action |
| B-04 P1 | schedule edit paths | Partially addressed by DB-01/02 |
| B-05 P2 | Reminders UI placeholders | Wave 2 |
| B-06 P2 | stale VM | Mitigated via `prepareRoute` on all edit entry |
| B-07 P3 | doc drift | Note in plan; optional README sync |
| B-08 P3 | missing shell tests | Addressed by new contract tests |

---

## 2. Remediation plan (review-rigor rows)

### Emit (≥90% conf)

| ID | Action | Prevents | Cost | Conf |
|----|--------|----------|------|------|
| **F1** | Add `selectDoseEventByIdIfChainActive` in `DoseEvent.sq` (join Medication + Pet; require `discontinued_at IS NULL` AND `archived_at IS NULL`). Wire `SqlDelightReminderLookup.lookup` to it. | Discontinued/archived med notifications at fire time | S | **95%** S1=1 S2=1 S3=1 S4=1 S5=1 S6=1 S7=0.5 |
| **F2** | Add `selectPendingDoseEventsInRangeActive` (same joins + pending window). Wire `ToebeansApp.loadPendingRemindersInHorizon`. | Boot rehydrate re-arming discontinued alarms | S | **95%** |
| **F3** | Extend `ReminderLookupContract` with `lookup returns null when medication discontinued` + `archived pet`; implement in SqlDelight + in-memory subclasses. | Regression on fire path | S | **92%** |
| **F4** | Add `BootReceiverTest` case: discontinued med pending row → **zero** alarms after `BOOT_COMPLETED`. | Boot path regression | S | **90%** |

**Falsifiers:** F1 — Robolectric `DoseAlarmReceiverLookupTest` with discontinued seed still shows notification (must cancel). F2 — BootReceiverTest schedules alarm for discontinued row (must be 0). F3 — contract test fails before impl, passes after.

**Reads:** `DoseEvent.sq`, `SqlDelightReminderLookup.kt`, `ToebeansApp.kt:94-109`, `ReminderLookupContractTest.kt`, `BootReceiverTest.kt`.

### Deferred (explicit)

| ID | Action | Reason |
|----|--------|--------|
| D1 | `discontinue()` cancels AlarmManager + ends schedules | Needs `NotificationActuator` in ViewModel; wider blast radius; follow-on slice |
| D2 | `observeActiveWithPhases` uses `selectAllActiveSchedules` semantics | Contract/fake parity change; UI already filters; separate PR |
| D3 | `dose_unit` column migration | Wave 2 TB-D; needs ADR |
| D4 | Unified modal rehydration (B-02) | M1.2 scope |

---

## 3. Adversarial plan review (trainer + review-rigor)

### Findings on v1 plan → fixes applied

| Issue | Severity | Resolution |
|-------|----------|------------|
| Plan proposed only lookup fix, missed boot rehydrate (DB-02) | **High** | Added F2 + F4 |
| Plan suggested changing `observeActiveWithPhases` in same PR | **Medium** | Deferred D2 — fake parity + contract scope creep |
| Plan omitted archived-pet filter | **Medium** | F1/F2 include `archived_at IS NULL` (symmetric with `selectAllActiveSchedules`) |
| Claimed "fix all bugs" including B-02 unified modal | **Low** | B-02 explicitly deferred; not a DB-only fix |

### Plan v2 verdict

**APPROVED for implementation:** F1–F4 only. Human review required on merge (vibe-dangerous SQL + notifications path per AGENTS.md).

---

## 4. Verification

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest --tests '*ReminderLookup*' --tests '*BootReceiver*'
./gradlew ktlintCheck detekt :shared:jvmTest :androidApp:testDebugUnitTest :shared:koverVerify
```

Manual QA (post-fix): discontinue active med with pending dose → no notification at slot; reboot → no alarm rehydrate for that row.

---

## 5. Post-implementation status (2026-06-07)

| Item | Status |
|------|--------|
| F1–F4 | **Shipped** (uncommitted on `fix/pet-detail-med-edit-nav-readme`; recommend `fix/discontinued-med-fire-path` PR) |
| Gauntlet | **BUILD SUCCESSFUL** (ktlint, detekt, jvmTest, android unit, koverVerify) |
| B-01 P0 | **Closed** |
| B-08 P3 | **Closed** |
| D1–D4 | **Still deferred** — not medication-display bugs; separate slices |

### Trainer post-review

**Program notes:** P0 medication-critical gap closed at SQL read boundaries. Proactive alarm cancel on `discontinue()` (D1) remains optional hardening, not a wrong-dose vector.

**Your form:** Audit → plan → adversarial plan → test-first impl completed. Split DB PR from #88 before merge.

**Next session:** Branch `fix/discontinued-med-fire-path`, trainer comment, merge before Wave 2 nav work.
