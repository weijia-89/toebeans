# F-Droid listing draft (operator copy needed)

Filing note: this wave prepares docs only. Filing a public listing needs a signed release and explicit approval from the operator. Nothing here is published, no tag is cut, and no request is filed. The operator reviews the words below and files the listing in a later, approved step.

Status: base copy filled from Option A (short) in docs/fdroid/description-options.md. Every marked word slot now holds candidate words. The operator reviews them before filing. This file fixes the shape of the listing and the facts already in the repo.

Every fact row carries a source so the operator can check it. Keep the facts unchanged when editing the words.

## Facts already in the repo

| Field | Value | Source |
|---|---|---|
| App name | toebeans | docs/fdroid/app.toebeans.android.yml (`Name`, `AutoName`) |
| Application id | app.toebeans.android | same file, header comment |
| Category | Sports & Health | same file, `Categories` |
| License, repo position | AGPL-3.0-or-later | README.md License section; docs/adr/0020-license-posture-m12.md |
| License token in the fdroiddata draft | AGPL-3.0-or-later | docs/fdroid/app.toebeans.android.yml (`License`) |
| Author | Wei Jia | same file, `AuthorName` |
| Source URL | https://github.com/weijia-89/toebeans | same file, `SourceCode` |
| Issue tracker | https://github.com/weijia-89/toebeans/issues | same file, `IssueTracker` |
| Changelog | https://github.com/weijia-89/toebeans/blob/main/CHANGELOG.md | same file, `Changelog` |
| versionName / versionCode | 0.1.0 / 1 | same file, `Builds` |

The licence token is settled. AGPL-3.0-or-later is the single source of truth across README.md, the ADR, the metadata draft, and this listing. The root LICENSE file keeps its legal text unchanged.

## Short description slot

The metadata draft uses one line: "Local reminders for pet medication" (35 characters). Confirm the field limit with the target repository at filing time.

Short description: Local reminders for pet medication

This stays short. It carries no vet claim, no medical claim, and no AI feature claim.

## Full description skeleton

The words below come from Option A (short) in docs/fdroid/description-options.md. Options B and C remain available if the listing needs more length. Edit the words, not the facts.

### 1. Opening

toebeans is a medication reminder for pets. It runs on your Android phone and keeps every record there.

### 2. How reminders work

Add a pet, add a medication, then set when the doses are due. toebeans raises an alarm at each dose time. Alarms cover the next 72 hours, and a reboot does not clear them.

Facts to keep honest:

- AlarmManager fires doses over a 72-hour horizon; BootReceiver reloads pending rows in that window after a reboot (README.md).
- If notifications are denied, Today says so and points to Settings (README.md, ADR-0011 path).
- Editing or deleting a schedule keeps the dose log; an orphaned row cannot fire an alarm (CHANGELOG.md 0.4.10 and 0.4.16).

### 3. Local-only and privacy

Pets, medications, schedules, and dose history live in app-private storage. The app has no network access and sends nothing anywhere.

Facts to keep honest:

- The manifest declares only POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED. No INTERNET permission (androidApp/src/main/AndroidManifest.xml).
- A CI fitness function fails the build if a network library appears (README.md; AGENTS.md).
- JSON export and import are manual, and the file is not encrypted (screenshot 04-settings.png).

### 4. What the app will not do

toebeans does not give medical advice. It has no symptom checker, no dose-safety check, and no drug warnings. It is a reminder, not a vet. Ask a veterinary professional what to give your pet.

Facts to keep honest:

- README.md "What it deliberately doesn't do" is the canonical wording of the refusal.
- The emergency card carries poison-control phone numbers and the line "toebeans does not provide medical advice. In an emergency, call a veterinary professional." (CHANGELOG.md 0.4.15; ADR-0019 framing).
- Do not name a real drug next to a dose in the listing copy. Any example copy in this wave uses fictional medication names, not demo drug names. The in-progress captures still show demo data, and the welcome dialog states that demo data is not a medical recommendation. Keep that framing visible.

### 5. Permissions (optional section)

toebeans asks for four permissions: notifications to post dose alarms, two exact-alarm permissions for timing, and boot access to restore alarms after a restart. It asks for no network permission.

### 6. Links

Use these URLs from the facts table for the listing:

- Source: https://github.com/weijia-89/toebeans
- Issue tracker: https://github.com/weijia-89/toebeans/issues
- Changelog: https://github.com/weijia-89/toebeans/blob/main/CHANGELOG.md

No other link is needed for the listing.

## Screenshot order

Source PNGs are in /Users/dubs/Projects/toebeans/docs/screenshots/. Full paths and what each shows are in docs/fdroid/screenshot-inventory.md. Recommended order for the eight slots:

1. `01-home-today.png`: main job, pending doses and Log dose buttons
2. `06-home-dose-logged.png`: the result of logging a dose
3. `05-reminders.png`: active schedules
4. `07-schedule-detail.png`: dose times and date range
5. `02-pets-list.png`: the household roster
6. `03-pet-detail-luna.png`: medications under a pet
7. `04-settings.png`: local-only statement, backup, theme
8. `00-welcome-dialog.png`: first-launch framing, kept last as context

These are in-progress captures from 2026-10-15. The short description does not mention the History tab, the emergency card, or the schedule Edit action, so no recapture is required for this wave. The screenshot inventory lists those items as optional future gaps.

## Constraints checklist

- [x] Local-only: records in app-private storage, no network calls. Sources: README.md Security and privacy; docs/fdroid/app.toebeans.android.yml Description.
- [x] No network permission: the manifest declares four permissions and none is INTERNET. Source: androidApp/src/main/AndroidManifest.xml.
- [x] No AI feature claims: the metadata draft claims no AI feature. Operator keeps final copy free of AI claims. Source: docs/fdroid/app.toebeans.android.yml; AGENTS.md vibe-impossible list.
- [x] No medical advice: the metadata draft claims none. Operator keeps final copy free of dose advice, diagnosis, and interaction warnings. Sources: README.md; docs/adr/0019-vet-recommended-content-framework.md; CHANGELOG.md 0.4.15.

The boxes record the state of the sources, not approval of final copy. Re-check them against the operator's words before filing.

## Open items for the operator

- Review every filled slot and change any words that do not fit.
- The base is Option A (short). Options B and C remain available if the listing needs more length.
- Build and sign a release, confirm a reproducible build, and give explicit approval before filing. This wave does none of that.
- Set the release tag in docs/fdroid/app.toebeans.android.yml before filing. This wave leaves an operator TODO in that field.
- Optional later: recapture screenshots to add the History tab, the emergency card, and the schedule Edit action, or to refresh dates. See docs/fdroid/screenshot-inventory.md.
