# 0012. BootCompletedReceiver replays missed doses and is enabled after first dose

- Status: accepted
- Date: 2026-05-17
- Deciders: Wei
- Tier: vibe-dangerous (per AGENTS.md; medication-firing path)

## Context

Android's `AlarmManager` does not survive a device reboot. Every alarm scheduled before reboot is silently dropped by the system. After reboot, the app must re-schedule its alarms; otherwise every future dose alarm is lost until the user opens the app and the foreground path re-schedules.

The toebeans `AndroidManifest.xml` currently allowlists `RECEIVE_BOOT_COMPLETED` but does NOT declare a `BootCompletedReceiver`. There is a comment in the manifest tracking this as a follow-up. The form-check `firepath` review (2026-05-17) caught this as a medication-critical hazard: every device reboot currently produces silent dose loss until the user manually opens the app.

### Primary sources

- https://developer.android.com/reference/android/content/Intent#ACTION_BOOT_COMPLETED, "This broadcast is sent once after the system has finished booting. ... For an application targeting Android 8.0 (API level 26) or higher, this broadcast can only be received if your app is exempt from background restrictions or has a foreground service running, OR is registered statically in the manifest."
- https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSetting, "Set the enabled setting for a package component. ... This setting will override any enabled state which may have been set by the component in its manifest."
- AGENTS.md vibe-dangerous tier: receivers on the medication-firing path require explicit lifecycle documentation and replay logic.

## Decision

The app ships a `BootCompletedReceiver` that is **declared disabled in the manifest** and **enabled programmatically** after the first successful dose schedule is materialized. On boot, the receiver replays the upcoming window of dose alarms and writes a `LocalCrashLog` entry recording the replay outcome.

Concretely:

1. **Manifest declaration, disabled by default.**

   ```xml
   <receiver
       android:name=".notifications.BootCompletedReceiver"
       android:enabled="false"
       android:exported="true"
       android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
       <intent-filter>
           <action android:name="android.intent.action.BOOT_COMPLETED" />
           <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
           <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
       </intent-filter>
   </receiver>
   ```

   The `android:enabled="false"` is load-bearing. Without it, the receiver fires on every device reboot even for fresh installs that have not yet scheduled a single dose. The empty-DB replay produces noise without value and (per the metacognitive-miscalibration evidence in form-check) trains the user to ignore the receiver lifecycle.

2. **Programmatic enable after first dose schedule.** When the first `Schedule` is materialized to upcoming `DoseEvent` rows (i.e., `computeScheduledDoses` returns non-empty for the first time in the app's lifetime), `AppModule` enables the receiver via `PackageManager.setComponentEnabledSetting`. A `SharedPreferences` flag `boot_receiver_enabled_at` tracks the timestamp to avoid redundant calls.

3. **Receiver replays the upcoming window.** On `BOOT_COMPLETED` (or the locked-boot / package-replaced variants), the receiver:
   1. Opens the database via the SqliteForeignKeysCallback driver (ADR-0010).
   2. Queries `DoseEvent` rows where `scheduled_for > now()` AND `scheduled_for < now() + 24h` AND `fired_at IS NULL`.
   3. For each row, calls `AlarmManager.setExactAndAllowWhileIdle` with the existing reminder id and the row's `scheduled_for` instant.
   4. Writes a single `LocalCrashLog` entry with marker `BOOT_REPLAY_OK reminders=<n> at=<iso8601>`.

4. **Receiver also handles missed-dose detection.** For any `DoseEvent` row where `scheduled_for < now()` AND `fired_at IS NULL` (i.e., the device was off during the scheduled fire time), the receiver:
   1. Marks the row `missed_at = now()` (new column added in the schema migration for this ADR).
   2. Posts a single summary notification "N doses were missed while your phone was off" with a tap action that opens the History tab filtered to missed doses.

   The summary notification uses the same `POST_NOTIFICATIONS` check from ADR-0011. If notifications are denied, the rows are still marked `missed_at` (database is the source of truth) and the banner from ADR-0011 surfaces on next foreground.

## Test contract

The wire-up PR for this ADR must include:

1. An instrumented test (or Robolectric receiver test) that:
   - Pre-populates the database with three `DoseEvent` rows: one in the past (now - 1h, fired_at NULL), one upcoming (now + 1h, fired_at NULL), one too-far-future (now + 48h, fired_at NULL).
   - Sends a synthetic `BOOT_COMPLETED` intent to the receiver.
   - Asserts: the past row is marked `missed_at`; the upcoming row gets an `AlarmManager.setExactAndAllowWhileIdle` call; the too-far-future row is untouched; the `LocalCrashLog` has one entry with the BOOT_REPLAY_OK marker.

2. A unit test of the `AppModule` enable-on-first-dose logic that asserts the `SharedPreferences` flag is written and `setComponentEnabledSetting` is called exactly once across multiple invocations.

3. A fitness function (`scripts/test_boot_receiver_lifecycle.sh`) that greps `AndroidManifest.xml` for the `BootCompletedReceiver` declaration and asserts `android:enabled="false"` is present. The fitness function also greps the source for any unguarded call to `setComponentEnabledSetting` for the receiver component (the call must be inside the first-dose-materialized code path).

## Consequences

**Positive:**
- Device reboots no longer silently lose dose alarms.
- Missed doses produce a user-visible summary (subject to ADR-0011 permission posture).
- The disabled-by-default lifecycle prevents noise on fresh installs that have never scheduled a dose.

**Negative:**
- The schema gains a `missed_at` column. This is a vibe-dangerous schema change per AGENTS.md and requires its own migration ADR if the schema version bumps.
- The `setComponentEnabledSetting` call has a small effect on the install-state delta (PackageManager state diverges from the manifest declaration). Document this in the security review checklist.
- Boot replay adds work to the receiver path. The macrobenchmark must include a "boot replay cold path" budget, recommended 500ms p95 for a 24-hour window with up to 100 upcoming doses.

## Alternatives considered

**Rejected: enable receiver in the manifest unconditionally.** Causes the receiver to fire on every device reboot even for users who have not yet scheduled any dose. Empty-DB replays are noise without value and the cumulative training effect (user notices "toebeans does something on boot" with no observable benefit) is a UX cost.

**Rejected: use `WorkManager` periodic worker instead of `BootCompletedReceiver`.** `WorkManager` does not run on `BOOT_COMPLETED`; its first execution after reboot is bounded by the OS's batch-scheduling heuristic, typically minutes to hours. The medication-firing path cannot tolerate that latency for the next upcoming dose. `WorkManager` is the right tool for the periodic-sweep concern (deferred to a separate scope).

**Rejected: foreground service that survives reboot.** Foreground services do not survive reboot on modern Android. Process resurrection requires either a static manifest receiver or the (deprecated) `START_STICKY` semantics, both of which still need `BOOT_COMPLETED` to do anything useful.

## Forward references

- ADR-0010 (FK enforcement) lands first; the receiver's DB queries depend on FK semantics.
- ADR-0011 (POST_NOTIFICATIONS denial UX) lands in parallel; the summary missed-dose notification shares the permission check and the banner surface.
- Future ADR for the WorkManager periodic stale-pending sweep (separate from boot replay).

## Sourcing

Surfaced in the 2026-05-17 form-check adversarial review of the toebeans notification firepath as the second-of-two medication-critical hazards in the firing path lifecycle (the first being ADR-0011). Codified as a load-bearing miss in the `test_as_spec/test_locks_in_bug` pressure scenario.
