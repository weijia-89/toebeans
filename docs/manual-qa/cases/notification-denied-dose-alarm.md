# Notification denied, dose alarm fires

**Source:** ADR-0011 section "Decision" steps 1 to 4 and the merged PR test plans for #108 to #113 (each left the device steps open because no emulator was booted). First run: 2026-10-24 overnight device QA session on the `toebeans-pixel7` AVD (Android 14, API 34), app build from `origin/main` at `f35377b`.

## Steps

1. `./gradlew :androidApp:installDebug` from a worktree at the merged `main` commit.
2. `adb shell am start -n app.toebeans.android/.MainActivity`.
3. Complete first launch: welcome dialog, `Load demo data`, allow `POST_NOTIFICATIONS` at the system prompt.
4. Stage a pending dose event: force-stop the app, insert a `pending` `DoseEvent` row through `run-as` (debug build), relaunch, then press Home so the app is backgrounded.
5. Turn notifications off: `adb shell pm revoke app.toebeans.android android.permission.POST_NOTIFICATIONS`.
6. Drive the fire path: `adb shell am broadcast -n app.toebeans.android/.notifications.DoseAlarmReceiver -a app.toebeans.action.DOSE_FIRE -e app.toebeans.extra.REMINDER_ID <doseEventId>`.
7. Check the notification shade and `adb shell dumpsys notification --noredact`.
8. Read the app's private `files/crash.log` through `run-as`.
9. Bring the app back to the foreground and look at the Today screen.
10. Positive control: `pm grant` the permission back, fire a second staged dose event, re-check the shade and the `NotificationAudit` table.

## Expected result

- `DoseEvent.fired_at` is set in the database even though the notification cannot be shown (ADR-0011 step 1, write-before-show).
- No notification appears in the shade; `dumpsys notification` shows the `medication-critical` channel with `importance=NONE userSet=true` and no posted record.
- `files/crash.log` contains the line `POST_NOTIFICATIONS_DENIED_AT_FIRE reminder_id=<doseEventId>` (ADR-0011 step 3).
- The next foreground transition shows the Today banner with the exact ADR-0011 copy: "Your last dose alarm did not show as a notification because notifications are disabled for toebeans. Tap to fix in Settings, or open the History tab to see what fired."
- Positive control: with the permission granted, the notification posts on `medication-critical` (title names the pet and medication), a `NotificationAudit` row is recorded for the delivered dose, and the banner stays visible while any fired-but-undelivered dose remains in the latest-ten window.

## Recorded run (2026-10-24, AVD toebeans-pixel7, Android 14)

- [x] `./gradlew :androidApp:installDebug` from detached worktree at `f35377b`: installed on `emulator-5554`.
- [x] `adb shell am start -n app.toebeans.android/.MainActivity`: activity started.
- [x] First launch: welcome dialog rendered; `Load demo data` seeded 2 pets, 6 medications, 6 schedules; system `POST_NOTIFICATIONS` prompt appeared; Allow granted.
- [x] Denied path steps 4 to 9: all five expected results observed (fired_at set, no posted notification, crash-log marker, banner copy verbatim).
- [x] Positive control step 10: notification posted (title names the pet and the medication with the due wording), `NotificationAudit` row recorded, banner correctly still visible because the earlier undelivered dose stays inside the latest-ten window.
- [ ] Real AlarmManager end-to-end fire on this emulator: **not verified**. `dumpsys alarm` showed zero registered toebeans alarms after demo-data load; the demo loader seeds schedules but does not materialize `DoseEvent` rows or register alarms. Driving a fire through the schedule-create UI was blocked because the on-device keyboard swallowed `adb input text` keystrokes in the medicine-search name field. The receiver path itself is fully verified above; the schedule-create-to-alarm registration path remains open on hardware.

## Known gap this run surfaced

The demo-data path never registers an AlarmManager entry and never materializes a `DoseEvent` row, so a demo-data install has no alarms to fire. Today's dose rows on a demo install are calculator projections only. This matches the current design (schedule-create is the alarm-scheduling entry point) but is worth a note in the soak-test protocol: alarm-path QA needs a schedule created through the app, not demo data.
