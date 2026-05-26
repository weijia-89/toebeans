# Beta smoke: add pet → add medication → add schedule

**Audience:** operator (Wei) running this **before** inviting M1.2 internal testers.
**Gate:** Q9 in [`research/decisions/2026-05-26-m12-beta-gates.md`](../research/decisions/2026-05-26-m12-beta-gates.md): basic add-medication path **must work** for beta; anchor-mode prompt is **deferred** to M1.5 / ADR-0007.
**Version under test:** `0.1.0` (`ToebeansApp.APP_VERSION_NAME` / Settings → About).

Related docs (read first):

- [Play Store internal-testing walkthrough](play-store-internal-testing-walkthrough.md): distribution setup (~3–5 h + ID wait).
- [Soak-test protocol](soak-test-protocol.md): what testers do for 30 days after install.

---

## Scope

| In scope (beta blocker) | Out of scope (document, do not block beta) |
|---|---|
| First launch → empty DB or demo | Anchor-mode prompt (M1.5 / ADR-0007 G1–G3) |
| Add pet with required fields | Medication edit via pet-detail row tap (route exists; nav not wired) |
| Add medication (name + dose amount) | Compose snapshot / macrobench coverage |
| Create schedule (start date, ≥1 phase, dose times) | Full 30-day soak (tester protocol) |
| Schedule appears on **Reminders** + **Today** worklist | Play Console clicks (human-only) |
| Optional: notification at fire time (see § Reminder verification) | Encrypted backup passphrase (ADR-0016 v2) |

---

## Operator pre-flight (day 0, before testers)

Complete **before** sending the internal-testing invite link.

### Build & device

- [ ] **JDK 17** on PATH (`README.md`, `AGENTS.md`). JDK >17 has rough edges with Kotlin 2.0 + AGP 8.7; stay on 17 until toolchain catches up.
- [ ] Android SDK API 34+ installed.
- [ ] Build and install debug or release candidate:

  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  cd /path/to/toebeans
  ./gradlew :androidApp:installDebug
  ```

- [ ] Physical phone preferred (Pixel a-series ideal). Emulator OK for **data/UI** steps; **alarm timing** on emulator is unreliable (see § Simulator limits).
- [ ] OS notifications **allowed** for toebeans (`POST_NOTIFICATIONS` on Android 13+).
- [ ] Note phone make/model, Android version, security patch (soak protocol day-0 baseline).

### Distribution (if not already done)

- [ ] Walk through [play-store-internal-testing-walkthrough.md](play-store-internal-testing-walkthrough.md) through Phase 7 (internal track + tester emails).
- [ ] Privacy policy URL live; release AAB built with `./gradlew :androidApp:bundleRelease`.
- [ ] Operator has **not** yet invited testers until this smoke passes (or fails with documented, accepted gaps).

### Automated smoke (fast)

Run from repo root on **JDK 17**. CI remains the load-bearing gate if local Robolectric is slow; AGENTS.md documents the local-JDK17 `test_verif` pattern.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"

# Shared: medication repo contract + data smoke
./gradlew :shared:jvmTest \
  --tests "app.toebeans.core.data.SqlDelightMedicationRepositoryContractTest" \
  --tests "app.toebeans.core.data.MedicalRepositoryContractSmokeTest" \
  --console=plain

# Android: medication + schedule create ViewModels + DI smoke
./gradlew :androidApp:testDebugUnitTest \
  --tests "app.toebeans.android.ui.medications.MedicationEditViewModelDeleteTest" \
  --tests "app.toebeans.android.ui.medications.MedicationEditViewModelDiscontinueTest" \
  --tests "app.toebeans.android.ui.schedule.ScheduleCreatePreflightTest" \
  --tests "app.toebeans.android.ui.schedule.ScheduleCreateNightDoseTest" \
  --tests "app.toebeans.android.ui.schedule.ScheduleCreateMidnightStraddleTest" \
  --tests "app.toebeans.android.di.AppModuleSmokeTest" \
  --console=plain
```

- [ ] All listed tests green (or note failure + block tester invite).

---

## Manual smoke path (~15 minutes)

Use a **real or realistic** pet/medication name. Do **not** rely on demo seed alone for alarm checks; demo Luna/Rufus schedules may not match your test clock.

### 0. First launch

1. Fresh install (or clear app data).
2. Welcome dialog: tap **Start fresh** (not required for smoke, but matches tester default).
3. Confirm **Today** shows empty / add-pet CTA.

**Pass:** no crash; empty state renders.

### 1. Add pet

1. **Pets** tab → **Add pet** (or Today empty-state CTA).
2. Fill **Name** (required), optional species/weight/birthdate/notes.
3. Tap **Save**.

**Pass:** pet appears on Pets list; tap opens pet detail.

### 2. Add medication

1. On pet detail, **Add medication** (empty-state button or FAB when list non-empty).
2. Fill **Name** and **Dose amount** (both required, e.g. `Methimazole`, `2.5 mg`).
3. Tap bottom **Save**.

**Pass:** returns to pet detail; medication row visible with name + dose amount.

**Note:** Saving does **not** auto-open schedule creation. Next step is required.

### 3. Add schedule

1. On pet detail, **tap the medication row** → **Create schedule** screen.
2. Set **Start date** to **today** (or tomorrow if testing future fire).
3. Leave default single phase or add phase as needed.
4. Set **dose time(s)** :  for a quick notification check, pick a time **2–5 minutes from now**. Watch night-dose `[00:00, 06:00)` banner; tap **Yes, that's intentional** if shown).
5. Tap **Save schedule**.

**Pass:** no calculator `formError` banner; navigates back to pet detail without crash.

### 4. Verify persistence (UI)

1. **Reminders** tab → row for pet · medication · schedule summary.
2. Tap row → **Schedule detail** shows phases, dose times, date range.
3. **Today** tab → due row for the scheduled slot (calculator projection; may show as pending before fire time).

**Pass:** schedule survives process kill (force-stop → reopen app; Reminders row still present).

### 5. Log dose (adherence path)

1. **Today** → **Log dose** on the due row **or** pet detail → **Log dose now** on medication row.
2. Confirm row moves to **Given ✓** / appears under **Logged today**.

**Pass:** dose logged without crash; state persists after app restart.

---

## Reminder verification (notification fire)

Medication-critical path. Treat a silent miss as **smoke fail** unless explicitly documented as a known main gap.

### Expected on `main` (as of M1.2 beta planning)

| Layer | Status on `main` |
|---|---|
| Pet / medication / schedule **persistence** (SQLDelight) | Shipped |
| **Today** worklist (calculator projection) | Shipped |
| **Reminders** list + schedule detail | Shipped |
| `DoseAlarmReceiver` + SQLDelight lookup | Shipped |
| `BootReceiver` 72h **rehydration** (re-schedule existing **pending** dose rows) | Shipped |
| **Schedule-create → pending DoseEvent materialization + AlarmManager.schedule** in UI process | **Not wired** (deferred slice; see Known gaps) |

**Operator implication:** After step 3, a **push notification at the scheduled time may not fire** for user-created schedules until the 72h materializer + post-save scheduling slice lands. UI and logging paths can still pass smoke.

### Procedure (when testing anyway)

1. Complete § Manual smoke through step 3 with fire time **T+3 min**.
2. Background the app; do **not** force-stop.
3. At **T**, expect notification on channel `medication-critical` (title/body mentions pet + medication).
4. If notification fires: tap → app opens; optional check Today row still consistent.
5. If **no notification**: check Today for pending row at scheduled time → if row exists but no notification, record **smoke fail (alarm path)** with phone state (DND, battery saver, exact-alarm permission on Android 12+).

### Simulator / emulator limits

- AlarmManager `setExactAndAllowWhileIdle` timing is **best-effort** on emulators; ±several minutes or total miss is common.
- Do not treat emulator-only miss as ship/no-ship; repeat on physical hardware.
- Android 12+ may require **Alarms & reminders** special access for exact alarms; document if prompted.
- toebeans does **not** bypass Do Not Disturb; note DND schedule during test.

### Reboot spot-check (optional, ~5 min)

1. With a pending dose row in DB (only if materializer exists or manually inserted in debug), reboot device.
2. Confirm `BootReceiver` re-schedules alarms within 72h horizon (`BootReceiverTest` documents contract).

Skip for beta invite if materializer gap applies. Rehydration cannot schedule doses that were never materialized.

---

## Pass / fail summary

| Result | Action |
|---|---|
| **PASS** (steps 0–4 + 5; notification optional per known gap) | Proceed to Play Console tester invite + hand off [soak-test-protocol.md](soak-test-protocol.md). |
| **FAIL** (crash, data loss, schedule not saved, Today/Reminders empty after save) | **Block** tester invite; file issue; fix on `main` before beta. |
| **PARTIAL** (UI pass, notification fail due to materializer gap) | Document in daily log; operator decision: invite testers with written expectation that **reminders are UI-only until next slice**, or hold invite until alarm path wired. Q9 requires path **usable**: logging doses + seeing schedule counts; silent alarm miss is a **product risk**, not an excuse to skip disclosure. |

---

## Known gaps on `main` (2026-05-26)

Verified against `origin/main` at operator dispatch. Re-verify after merge.

1. **72h dose materializer not wired after schedule save.** `ScheduleCreateViewModel.save()` persists schedule + phases only; it does not insert pending `DoseEvent` rows or call `NotificationActuator.schedule()`. `ToebeansApp.rehydrateBootAlarms` only re-schedules **existing** pending rows. **Impact:** user-created schedules may not push notifications. Today/Reminders UI still works via calculator projection.
2. **Anchor-mode prompt** absent (M1.5 / ADR-0007): **accepted** per Q9; no beta blocker.
3. **Pet detail medication tap** navigates to **Create schedule**, not **Edit medication** (`Destinations.medicationEdit` unused in nav). Edit requires knowing route or future UX fix.
4. **`NotificationChannel("medication-critical")` registration** still called out as pending in `ToebeansApp` KDoc. Confirm on device if notifications are silent.
5. **Discontinued medications:** alarms for already-materialized doses may still fire (`MedicationEditViewModel` KDoc); inactive filter on lookup deferred.
6. **README drift:** README still says "Notification firing remains on the ROADMAP" while receiver + boot rehydration partially shipped. Treat this doc + ROADMAP as authoritative for beta.

---

## Sign-off

| Field | Value |
|---|---|
| Date | |
| Git SHA | |
| Device | |
| Steps 0–5 | PASS / FAIL |
| Notification (if tested) | PASS / FAIL / SKIPPED (gap) |
| Tester invite | GO / NO-GO |
| Notes | |

---

---

## Worker log (2026-05-26)

**Agent:** tb-add-med-smoke
**Worktree:** .worktrees/tb-add-med-smoke
**Branch:** docs/beta-smoke-add-medication

### Automated test run

JDK: OpenJDK 17 via /usr/libexec/java_home -v 17.

| Task | Result |
|---|---|
| :shared:jvmTest (MedicationRepository contract + MedicalRepositoryContractSmokeTest) | PASS |
| :androidApp:testDebugUnitTest (MedicationEdit VM, ScheduleCreate VM, AppModuleSmokeTest) | PASS |

Note: Android tests required copying local.properties into the worktree (not committed; gitignored).
