# Screenshot inventory (source captures in this repo)

Capture status: the eight images below are in-progress captures from demo data, not shots from a final signed release build. They are acceptable for this docs-only wave. Medication names in the descriptions below are fictional examples, not real drugs and not a medical recommendation. The in-app welcome dialog states that demo data is not a medical recommendation.

All eight app screenshots live in /Users/dubs/Projects/toebeans/docs/screenshots/. The capture log at docs/screenshots/README.md records the provenance: captured 2026-10-15, Pixel 7 profile, 320 dpi, API 30 AOSP emulator, demo data loaded from the first-launch dialog. Each image is a 720 x 1280 pixel PNG.

The paths below are the real source files on disk. Copying them into a fdroiddata repository for publication is an operator step and is not part of this change.

## Recommended order

1. 01-home-today.png
2. 06-home-dose-logged.png
3. 05-reminders.png
4. 07-schedule-detail.png
5. 02-pets-list.png
6. 03-pet-detail-luna.png
7. 04-settings.png
8. 00-welcome-dialog.png

The order leads with the main job, then the result of doing it, then the supporting screens. The welcome dialog goes last because it explains the local-only framing rather than showing the app at work.

## Each capture

### 1. 01-home-today.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/01-home-today.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: Today tab dated Thursday, October 15. Pet chips for Luna (3 meds) and Rufus (3 meds). A "Today's doses" card with four pending rows: Rufus Fleabane 16 mg at 7:30 AM, Rufus Jointcare 75 mg at 8:00 AM, Luna Thyraz 2.5 mg at 8:00 AM, Luna Prednil 5 mg at 9:00 AM. Each row has an orange Log dose button. Bottom navigation shows four tabs: Today, Reminders, Pets, Settings.
Recommended slot: 1 of 8.

### 2. 06-home-dose-logged.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/06-home-dose-logged.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: the same Today screen after one tap. The Rufus Fleabane row now reads "Given" with a green check where the Log dose button was. The other three rows still have Log dose buttons.
Recommended slot: 2 of 8.

### 3. 05-reminders.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/05-reminders.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: Reminders tab with five active schedule cards. Luna GutFlora once daily, Luna Thyraz twice daily, Luna Prednil once daily, Rufus Fleabane twice daily, Rufus Jointcare once daily, each for 3650 days. An orange Add medication button sits at the lower right. Bottom navigation shows four tabs.
Recommended slot: 3 of 8.

### 4. 07-schedule-detail.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/07-schedule-detail.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: a read-only Schedule detail screen reached from a Reminders row. Top bar has a back arrow, the title Schedule, and a Delete action. Cards show Luna Thyraz, date range 2024-01-01 to ongoing, and a phase of twice daily for 3650 days with doses at 08:00 and 20:00.
Recommended slot: 4 of 8.

### 5. 02-pets-list.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/02-pets-list.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: Pets tab with an orange plus button at the top right. Two cards: Luna, cat, 4.1 kg, 4 years old, 3 meds; Rufus, dog, 12.4 kg, 7 years old, 3 meds. Bottom navigation shows four tabs with Pets selected.
Recommended slot: 5 of 8.

### 6. 03-pet-detail-luna.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/03-pet-detail-luna.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: Luna detail screen with a back arrow and an edit pencil. Identity card reads Luna, cat, 4.1 kg, 4 years old. Medication cards show GutFlora 1 sachet and Thyraz 2.5 mg, each with a Log dose now button, and the top of Prednil 5 mg. An Add medication button sits at the lower right.
Recommended slot: 6 of 8.

### 7. 04-settings.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/04-settings.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: Settings tab. Display card has an Auto/Light/Dark theme picker with Auto selected and a Material You colors toggle set to off, with the note that it requires Android 12 or newer. Data card says data lives only on this device, describes JSON backup export and import, and says the backup has no encryption envelope. Bottom navigation shows four tabs with Settings selected.
Recommended slot: 7 of 8.

### 8. 00-welcome-dialog.png

Path: /Users/dubs/Projects/toebeans/docs/screenshots/00-welcome-dialog.png
Image: 720 x 1280 px PNG, in-progress capture
Shows: first-launch "Welcome to toebeans" dialog on a dimmed Today screen. The text says toebeans is a local-first medication reminder, that nothing leaves the device, and that the demo pets are an example rather than a real medical recommendation. Buttons offer Load demo data and Start fresh. The four-tab bottom navigation is visible behind the dialog.
Recommended slot: 8 of 8.

## Optional future gaps (not required for this wave)

The short base description in docs/fdroid/listing-draft.md does not mention the screens below, so no recapture is needed before filing. If the listing copy later mentions them, add the named captures first.

- History tab. Added in version 0.3.0 on 2026-10-24, nine days after these captures. None of the eight images shows the fifth tab. Add a capture of the History screen if the description mentions it.
- Emergency resources card. Added on 2026-11-16 (CHANGELOG.md 0.4.15) on Today and in Settings. No capture shows it. Add two captures if the description mentions it.
- Schedule detail Edit action. Added on 2026-11-11 (CHANGELOG.md 0.4.10). Image 07 shows Delete only, so it is stale. Recapture if the description mentions editing a schedule.
- Stale date and status bar. The captures read Thursday, October 15 with status-bar times from 5:53 to 6:05. Refresh all eight in one pass if a consistent, newer set is wanted.
- Fictional medication names in screenshots. The on-disk images still show demo drug names beside doses. The welcome dialog note covers this. A later pass may recapture with plainly fictional entries.
- Launcher icon. The manifest comment says there is no launcher icon resource yet and Android uses the system default. An icon capture waits on that slice.

## Deliberate omissions, not gaps

- No notification or dose-alarm screen. The capture log says notification UI is system chrome, not in-app. Do not fabricate one.
- No boot alarm rehydration screen. It runs from BootReceiver after reboot and has no UI, per the capture log.
- No medication create or edit form, and no dose-history edit screen. Optional. Only capture these if the final description walks through those steps.

## Non-image file in the folder

Path: /Users/dubs/Projects/toebeans/docs/screenshots/README.md
This is the capture log, not an app screenshot. It documents provenance and regeneration. Do not include it in a listing.
