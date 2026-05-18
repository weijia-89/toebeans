# ADR-0002: AlarmManager + WorkManager hybrid for medication reminders

Date: 2026-05-14
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

Medication reminders are **medication-critical** and must fire within ±60 seconds of their scheduled time (success criterion 2 in the MVP design doc).

Android offers two relevant scheduling primitives:

| API | Strength | Weakness |
|---|---|---|
| **WorkManager** | Survives reboots, app process death, doze; recommended default | Coalesced; firing windows are loose (10+ minutes); not exact |
| **AlarmManager.setExactAndAllowWhileIdle** | Exact timing even in doze | Requires `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission on API 31+; user can revoke |
| **AlarmManager.setExact** (no allowWhileIdle) | Exact when device awake | Skipped during doze; unsuitable for overnight reminders |

WorkManager alone fails the ±60s requirement on doze-enabled devices. AlarmManager alone leaks the rescheduling-after-boot behavior into bespoke `BroadcastReceiver` code; we want WorkManager's RESCHEDULER and constraint-aware enqueueing for the persistent state machine.

## Decision

Hybrid:

- **WorkManager** is the source of truth for "what's pending in the next 72 hours." A periodic WorkManager job (`ReminderRescheduleWorker`) runs every 6 hours and on `RECEIVE_BOOT_COMPLETED` to keep the AlarmManager queue populated. It writes pending `DoseEvent` rows to SQLite.
- **AlarmManager.setExactAndAllowWhileIdle** is used to fire each individual reminder at its exact `scheduledAt` instant. The receiver writes `fired_at` and surfaces the notification.

Permission handling:

- The app requests `POST_NOTIFICATIONS` on first launch (API 33+).
- The app requests `SCHEDULE_EXACT_ALARM` on first launch with a clear explanation screen. If the user denies, the app degrades to `setWindow` with a 10-minute window and shows a persistent "downgraded reliability" banner.
- `USE_EXACT_ALARM` is declared as a backup for medication-class apps (Google Play classifies medication-tracking apps as eligible).

## Consequences

### Positive

- ±60s firing requirement is satisfied in the common case.
- Boot reschedule is centralized in one WorkManager job.
- Graceful degrade path exists for users who deny exact-alarm permission.

### Negative

- Two scheduling primitives means two test surfaces. Mitigation: fitness function `scheduler purity` forces the algorithm to be pure (no platform clock), so the WorkManager and AlarmManager interactions are isolated to one file each.
- OEM-customized Android (Xiaomi, OnePlus, Samsung Game Booster, Huawei) may kill the rescheduling worker. The "DontKillMyApp" category of issues. Mitigation: in-app guidance pointing users to disable battery optimization for toebeans, plus telemetry-free local detection (a boot-completed receiver writes a heartbeat that a follow-up worker checks).

### Rejected alternatives

- **WorkManager only.** Rejected: cannot satisfy ±60s on doze-enabled devices.
- **AlarmManager only.** Rejected: the queue-management state machine becomes bespoke and untestable.
- **Foreground service.** Rejected: heavyweight, drains battery, fails Play Store "background work" policy for our category.

## Verification

- `MAX_PENDING_PER_PET = 64` cap is asserted in the rescheduler tests.
- `AUTO_RESCHEDULE_HORIZON_HOURS = 72` is asserted in the rescheduler tests.
- A 30-day soak test (success criterion 1) runs on a non-OEM-customized Pixel device before the milestone 1 release tag.

## References

- Android docs: *Schedule alarms* (`setExactAndAllowWhileIdle`).
- Android docs: *WorkManager*.
- Google Play policy: *Use of high priority FCM and exact alarms* (eligibility for medication apps).
- dontkillmyapp.com: OEM battery-optimization catalogue (a known operational hazard).
