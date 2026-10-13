# Manual QA (hands-on)

Automate emulator, install, fresh state, and launch. **You only navigate** on device.

## Quick start

From repo root:

```bash
bash scripts/manual_qa_boot.sh fresh
bash scripts/manual_qa_boot.sh fresh --boot-avd toebeans-pixel7
bash scripts/manual_qa_boot.sh warm                    # keep existing app data
bash scripts/manual_qa_boot.sh fresh --open-style-lab  # also opens docs/style-lab in browser
```

Env: `TOEBEANS_AVD`, `ANDROID_SERIAL`, `ANDROID_HOME`.

Verify before merge:

```bash
./gradlew ktlintCheck detekt :shared:jvmTest
```

## What the script does

1. Waits for a device (optional `--boot-avd` starts the emulator).
2. `./gradlew :androidApp:installDebug`
3. `fresh`: `pm clear` then launch; `warm`: launch only.
4. Prints **navigation-only** steps.

PR test plans should reference this script for prerequisites.

## Dose-log + FAB smoke (UI)

After boot, run the hands-on paths in [`beta-smoke-add-medication.md`](beta-smoke-add-medication.md):

- **§2 Add medication:** pet detail FAB / empty-state **Add medication**
- **§5 Log dose:** Today **Log dose** or pet detail **Log dose now** → **Given ✓** / **Logged today**, persists after restart

## Recorded cases

Cases already written in git history, pull request test plans, commit messages, ADRs, and docs. One file per case. Each file names the source, the steps, and the expected result. Nothing here is a new test.

### Boot script

- [Fresh install core navigation](manual-qa/cases/fresh-install-core-nav.md)
- [Warm launch keeps prior session](manual-qa/cases/warm-launch-keeps-prior-session.md)
- [Today tab loads after install](manual-qa/cases/today-tab-loads.md)

### Style lab and doc checks

- [Style lab pack toggle](manual-qa/cases/style-lab-pack-toggle.md)
- [Style lab matches the chosen pack](manual-qa/cases/style-lab-matches-chosen-pack.md)
- [BootReceiver docs match the code](manual-qa/cases/bootreceiver-docs-match-code.md)
- [Follow-ups no longer say the receiver is missing](manual-qa/cases/followups-no-longer-say-receiver-missing.md)
- [Roadmap rows match the add-medication intent](manual-qa/cases/roadmap-rows-match-add-med-intent.md)

### Beta smoke

- [First launch empty state](manual-qa/cases/first-launch-empty-state.md)
- [Add a pet](manual-qa/cases/add-pet.md)
- [Add a medication](manual-qa/cases/add-medication.md)
- [Add a schedule](manual-qa/cases/add-schedule.md)
- [Night-dose affirmation](manual-qa/cases/night-dose-affirmation.md)
- [Schedule survives force-stop](manual-qa/cases/schedule-survives-force-stop.md)
- [Logged dose persists after restart](manual-qa/cases/log-dose-persists-after-restart.md)
- [Notification at the scheduled time](manual-qa/cases/notification-at-scheduled-time.md)
- [Reboot reschedules pending alarms](manual-qa/cases/reboot-reschedules-pending-alarms.md)

### Schedule, alarms, and anchor mode

- [Midnight-straddle schedule persists](manual-qa/cases/midnight-straddle-schedule-persists.md)
- [Midnight-straddle save returns](manual-qa/cases/midnight-straddle-save-returns.md)
- [Discontinue stops future fires](manual-qa/cases/discontinue-stops-future-fires.md)
- [Existing installs keep FOLLOW_PHONE](manual-qa/cases/anchor-mode-default-follow-phone.md)
- [DST spring-forward badge](manual-qa/cases/dst-spring-forward-badge.md)
- [Time-sensitive anchor mode saves](manual-qa/cases/anchor-mode-elapsed-interval.md)
- [Elapsed interval has no DST badge](manual-qa/cases/elapsed-interval-no-dst-badge.md)
- [One-week emulator DST dogfood](manual-qa/cases/dst-emulator-dogfood-week.md)
- [Exact alarms off shows a Today banner](manual-qa/cases/exact-alarms-off-shows-banner.md)
- [Open alarm settings](manual-qa/cases/open-alarm-settings.md)
- [Turning exact alarms on hides the banner](manual-qa/cases/exact-alarms-on-hides-banner.md)
- [Empty Today still shows the alarm banner](manual-qa/cases/empty-today-shows-alarm-banner.md)
- [Allowed exact alarms keep the banner hidden](manual-qa/cases/exact-alarms-allowed-hides-banner.md)

### Today, pets, and demo data

- [Today Log dose terracotta pill](manual-qa/cases/today-log-dose-terracotta-pill.md)
- [Dark theme keeps the Log dose pill](manual-qa/cases/dark-theme-log-dose-pill.md)
- [Today long page is not clipped](manual-qa/cases/today-long-page-not-clipped.md)
- [Today pet chip filter](manual-qa/cases/today-pet-chip-filter.md)
- [Today Edit reaches medication and schedule](manual-qa/cases/today-edit-reaches-medication-and-schedule.md)
- [Demo data rows, FAB, and dose edit](manual-qa/cases/demo-data-rows-fab-and-edit.md)
- [Today FAB opens a blank add-medication form](manual-qa/cases/today-fab-blank-add-medication.md)
- [Reminders save then add schedule](manual-qa/cases/reminders-save-then-add-schedule.md)
- [Add medication hides the Reminders FAB](manual-qa/cases/add-medication-hides-reminders-fab.md)
- [Pet rename keeps medications](manual-qa/cases/pet-rename-keeps-medications.md)
- [Pet detail Log dose now](manual-qa/cases/pet-detail-log-dose-now.md)
- [Settings dark theme persists](manual-qa/cases/settings-dark-theme-persists.md)
- [Pets tab paw icon tint](manual-qa/cases/pets-tab-paw-tint.md)
- [Today date header](manual-qa/cases/today-date-header.md)
- [Settings About and What's coming cards](manual-qa/cases/settings-about-and-coming-cards.md)
- [Today pet chips](manual-qa/cases/today-pet-chips.md)
- [Pets list avatar and terracotta FAB](manual-qa/cases/pets-list-avatar-and-fab.md)
- [Pet detail avatar stays the shared component](manual-qa/cases/pet-detail-avatar-unchanged.md)
- [Add medication form layout](manual-qa/cases/add-medication-form-layout.md)

### Medication form

- [Medication search selects amox](manual-qa/cases/medication-search-amox.md)
- [Medication search selects Gabapentin](manual-qa/cases/medication-search-gabapentin.md)
- [Unit picker visible, default empty](manual-qa/cases/unit-picker-visible-default-null.md)
- [Edit medication loads the saved unit](manual-qa/cases/edit-medication-loads-unit.md)
- [Schedule phase unit override persists](manual-qa/cases/schedule-phase-unit-override.md)
- [Today shows amount and unit](manual-qa/cases/today-shows-amount-and-unit.md)
- [Pet detail shows the formatted dose](manual-qa/cases/pet-detail-shows-formatted-dose.md)
- [Schedule detail shows an override dose](manual-qa/cases/schedule-detail-shows-override-dose.md)

### Soak and internal testing

- [Soak dose fires within five minutes](manual-qa/cases/soak-dose-within-five-minutes.md)
- [Silent miss stays pending on Today](manual-qa/cases/soak-silent-miss-still-pending.md)
- [Travel records which clock the alarm used](manual-qa/cases/soak-travel-record-which-clock.md)
- [Internal-testing install checks](manual-qa/cases/internal-testing-install-checks.md)
