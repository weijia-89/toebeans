# 0011. POST_NOTIFICATIONS denial does not silently lose a dose

- Status: accepted
- Date: 2026-05-17
- Deciders: Wei
- Tier: vibe-dangerous (per AGENTS.md; medication-firing path)

## Context

Android 13 (API 33) introduced the runtime `POST_NOTIFICATIONS` permission. By default, fresh installs on API 33+ do NOT have this permission. Apps must request it at runtime, and the user may deny.

The toebeans medication-firing path currently relies on `AndroidNotificationActuator.show()` posting a user-visible notification when an alarm fires. If `POST_NOTIFICATIONS` is denied, the silent path is:

1. `AlarmManager` fires on schedule (this does not require `POST_NOTIFICATIONS`).
2. `DoseAlarmReceiver.onReceive` runs.
3. `NotificationManagerCompat.notify(...)` is called.
4. Android silently drops the notification because the app lacks the permission.
5. No exception is thrown. No log entry is written. The user sees nothing.
6. The DoseEvent in the database is never marked as fired or missed.

This is a load-bearing silent failure. A user can lose every dose without observable signal. The form-check `firepath` review (2026-05-17) caught this as a medication-critical hazard.

### Primary sources

- https://developer.android.com/develop/ui/views/notifications/notification-permission, "On Android 13 (API level 33) and higher, the system doesn't show notifications from your app unless the user grants the POST_NOTIFICATIONS permission."
- https://developer.android.com/reference/androidx/core/app/NotificationManagerCompat#notify(int,android.app.Notification), no return value indicating delivery; `NotificationManagerCompat.areNotificationsEnabled()` is the explicit check.
- AGENTS.md vibe-dangerous tier: "silent failure on the medication-firing path is forbidden."

## Decision

The receiver path writes the `DoseEvent` lifecycle to the database **before** attempting the notification, and the UI **surfaces a denial banner** on next foreground transition if the actuator detected a denial.

Concretely:

1. **Receiver-side DB write is the source of truth.** `DoseAlarmReceiver.onReceive` writes `DoseEvent.fired_at = now()` synchronously before calling `actuator.show()`. Whether or not the user sees the notification, the database records that the alarm fired. The history view, the export, and any downstream sync agree.

2. **Actuator returns a delivery outcome.** `AndroidNotificationActuator.show()` returns a sealed `NotificationOutcome` of one of three values:
   - `Posted(notificationId: Int)` on success
   - `PermissionDenied` when `NotificationManagerCompat.areNotificationsEnabled()` returns false
   - `Failed(reason: String)` on unexpected exceptions (e.g., RuntimeException from notify)
3. **Receiver logs PermissionDenied to LocalCrashLog.** When the actuator returns `PermissionDenied`, the receiver writes a one-line entry to the local crash log with the reminder id and a fixed marker string `POST_NOTIFICATIONS_DENIED_AT_FIRE`. The crash log is already rate-limited and PII-free per its existing contract.

4. **UI surfaces a non-dismissable banner on next foreground transition** if the most recent ten `DoseEvent` rows include at least one with `fired_at IS NOT NULL` AND there is no corresponding entry in a new `notification_audit` table indicating the user-visible notification was actually delivered. The banner copy reads, verbatim: "Your last dose alarm did not show as a notification because notifications are disabled for toebeans. Tap to fix in Settings, or open the History tab to see what fired."

5. **First-launch flow requests `POST_NOTIFICATIONS` permission** during the existing `FirstLaunchDialog` flow before the user can dismiss the dialog. The dialog already exists for demo-data choice; this ADR adds the permission request as an additional gating step. If the user denies, the banner from (4) is shown on first foreground transition with copy adapted to first-run state.

## Test contract

The wire-up PR for this ADR must include:

1. A unit test of `AndroidNotificationActuator.show()` against a Robolectric-mocked `NotificationManagerCompat` that returns `areNotificationsEnabled() == false`. The test asserts the actuator returns `PermissionDenied` and does NOT call `notify()`.

2. A unit test of `DoseAlarmReceiver.onReceive` that injects the actuator above and asserts:
   - `DoseEvent.fired_at` is set in the database regardless of notification outcome.
   - A `LocalCrashLog` entry is written with the marker string when the actuator returns `PermissionDenied`.

3. An instrumented UI test (or Robolectric Compose test) that puts the database in a state where one `DoseEvent.fired_at` exists but no `notification_audit` row exists for it, and asserts the banner composable renders with the expected copy.

4. A fitness function (`scripts/test_post_notifications_silent_failure_guard.sh`) that greps `AndroidNotificationActuator.kt` for any path that calls `notify()` without first checking `areNotificationsEnabled()`. The fitness function fails CI if such a path exists.

## Consequences

**Positive:**
- Silent dose loss on default-deny API 33+ devices becomes impossible.
- The database is the source of truth for medication history, decoupled from notification delivery.
- The user has at least one signal (the banner) on every foreground transition if notifications are disabled.

**Negative:**
- The receiver path gains a synchronous DB write before the notification call. This adds latency to the alarm-firing path. Macrobenchmark in the `:macrobench` module must include a "doseAlarmReceiver cold-write latency" budget, recommended 50ms p95 on a Pixel 7.
- The `notification_audit` table is new schema. ADR-0010 (FK enforcement) applies. The wire-up PR for this ADR ships after ADR-0010 lands.
- The first-launch permission request shifts onboarding friction. Some users will deny on first launch and the banner will appear immediately. Document this in the user-facing onboarding copy.

## Alternatives considered

**Rejected: log-only without UI surface.** The crash log is operator-visible, not user-visible. The user has no signal. Rejected because the medication-firing path requires user-visible signal.

**Rejected: foreground service with persistent notification.** A persistent notification channel would bypass the per-message permission for that channel only. Rejected because (a) foreground services require an additional permission with Google Play review, (b) the persistent notification is itself a UX cost, (c) the user-denied notification permission still blocks one-shot dose notifications even with a foreground service present.

**Rejected: in-app sound at alarm fire time.** Playing an audio cue without a notification bypasses the permission boundary but introduces a different silent-failure mode (phone in silent mode, headphones unplugged). Rejected as net negative.

## Forward references

- ADR-0010 (foreign-key enforcement) lands first; this ADR's `notification_audit` table depends on FK semantics.
- ADR-0012 (BootCompletedReceiver lifecycle) lands in parallel; it interacts because boot replay must also write to `notification_audit` and surface the banner if it discovers stale fired-but-unnotified events.

## Sourcing

Surfaced in the 2026-05-17 form-check adversarial review of the toebeans notification firepath. Codified as a load-bearing miss in the `test_as_spec/test_locks_in_bug` and `hallucination_floor/library_behavior_unverified` pressure scenarios.
