# README screenshots

`00-welcome-dialog.png` and `01-home-today.png` were recaptured on 2026-10-15
at 720 × 1280 with demo data loaded. They show the current bottom nav
(Today, Reminders, Pets, Settings) and the paw icon on Pets.

`02` through `07` are still the 2026-05-26 captures at 1080 × 2400. Regenerate
the full set with `./scripts/capture_readme_screenshots.sh` after
`./gradlew :androidApp:installDebug` on a booted `toebeans-pixel7` emulator.

## Files

- `00-welcome-dialog.png`: first-launch local-only consent.
- `01-home-today.png`: Today tab with Luna and Rufus chips and pending dose rows.
- `02-pets-list.png`: Pets tab (Luna, Rufus).
- `03-pet-detail-luna.png`: Luna detail with Methimazole and **Log dose now**.
- `04-settings.png`: theme, Material You, export/import, Diagnostics.
- `05-reminders.png`: Reminders list (active schedules).
- `06-home-dose-logged.png`: Today after tapping **Log dose** on the morning row.
- `07-schedule-detail.png`: read-only schedule detail from a Reminders row tap.

Boot alarm rehydration has no dedicated screen; it runs from `BootReceiver` after
reboot. Notification UI is system chrome, not in-app.

## Roadmap reference

M1 Tier B (Reminder List + Schedule Detail): `docs/ROADMAP.md`. Play Store
internal-testing reuse: `docs/play-store-internal-testing-walkthrough.md`.
