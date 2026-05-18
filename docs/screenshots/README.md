# Reminder List + M1 surface screenshots (2026-05-18)

These screenshots capture seven screens from the toebeans Android app running on the `toebeans-pixel7` emulator with the seeded demo dataset (Rufus the dog, Luna the cat on twice-daily Methimazole). They are the visual record for ROADMAP M1 Tier B (the Reminder List + Schedule Detail surface) and may be referenced by Play Store internal-testing materials in M1.2.

All seven captures are at the canonical Pixel 7 portrait size of 1080 × 2400 px. No real user data appears in any frame; every visible row, name, and dose comes from the first-launch demo seed.

## Files

- `00-welcome-dialog.png`: first-launch local-first consent screen.
- `01-home-today.png`: Today tab showing both demo pets and the pending dose for Luna.
- `02-pets-list.png`: Pets tab listing Luna and Rufus with their thumbnails.
- `03-pet-detail-luna.png`: Luna's pet detail screen with the Methimazole row and the "Log dose now" affordance.
- `04-settings.png`: Settings screen with the theme picker, Material You toggle, and data export buttons.
- `05-reminders.png`: Reminders tab (the Tier B Reminder List screen) showing the per-pet projection of the seed schedules.
- `06-home-dose-logged.png`: Today tab after logging a dose. The row shows a Given check and a "Logged today" indicator.

## Provenance

Captured on the `toebeans-pixel7` emulator during the M1 Tier B surface implementation. The images are embedded inline in the root `README.md`; this directory README documents the per-file contents for reviewers who load the directory directly on GitHub.

## Roadmap reference

- M1 Tier B: ROADMAP § Milestone 1, Reminder List + Schedule Detail rows (`docs/ROADMAP.md`).
- M1.2 internal-testing track: ROADMAP § Milestone 1.2; the walkthrough at `docs/play-store-internal-testing-walkthrough.md` may reuse these captures.
