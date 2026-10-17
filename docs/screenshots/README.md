# README screenshots

All eight were recaptured on 2026-10-15 from the current app at 720 × 1280
(Pixel 7 profile, 320 dpi, API 30 AOSP emulator image) with demo data loaded
via the first-launch dialog. Regenerate with
`./scripts/capture_readme_screenshots.sh` after
`./gradlew :androidApp:installDebug` on a booted `toebeans-pixel7` emulator.

## Files

- `00-welcome-dialog.png`: first-launch local-only consent.
- `01-home-today.png`: Today tab with Luna and Rufus chips and pending dose rows.
- `02-pets-list.png`: Pets tab (Luna, Rufus).
- `03-pet-detail-luna.png`: Luna detail with Methimazole and **Log dose now**.
- `04-settings.png`: theme picker, Material You toggle, data export/import.
- `05-reminders.png`: Reminders list (active schedules).
- `06-home-dose-logged.png`: Today after tapping **Log dose** on the morning
  row (Apoquel shows **Given**).
- `07-schedule-detail.png`: read-only schedule detail from a Reminders row tap.

Boot alarm rehydration has no dedicated screen; it runs from `BootReceiver` after
reboot. Notification UI is system chrome, not in-app.

## Roadmap reference

M1 Tier B (Reminder List + Schedule Detail): `docs/ROADMAP.md`. Play Store
internal-testing reuse: `docs/play-store-internal-testing-walkthrough.md`.