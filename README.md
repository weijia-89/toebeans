# toebeans

Pet medication tracker for Android. Local-only. No cloud, no telemetry, no
third-party services.

Status: `v0.1.0-dev`, pre-MVP scaffold. The dose-log surface works end-to-end.
The reminder-firing path lands in the next milestone.

**SDK weekend (2026-05-23):** BootReceiver phase 1 on `main` ([PR #39](https://github.com/weijia-89/toebeans/pull/39)). Phase 2 [PR #40](https://github.com/weijia-89/toebeans/pull/40) @ `2b6af8a` (72h rehydration stub, empty schedule lookup).

<p align="center">
  <img src="docs/screenshots/01-home-today.png" alt="Today screen with two pets and two pending Methimazole doses for Luna" width="280">
</p>

## What it does today

* Add pets with weight and birthdate, plus a free-text notes field.
* Add medications underneath each pet.
* Define a schedule: start date, dose times, doses per day.
* Tap **Log dose now** on a medication to record a dose given.
* See `Last dose: 2h ago` on each row, or `just now` / `yesterday` /
  `on May 13` once enough time has passed.
* Theme picker (Auto / Light / Dark). Optional Material You dynamic color on
  Android 12 and up.

<p align="center">
  <img src="docs/screenshots/02-pets-list.png" alt="Pets list with Luna the cat and Rufus the dog" width="220">
  &nbsp;
  <img src="docs/screenshots/03-pet-detail-luna.png" alt="Luna's detail showing Methimazole 2.5 mg and a Log dose now button" width="220">
  &nbsp;
  <img src="docs/screenshots/06-home-dose-logged.png" alt="Today screen after logging: morning dose shows Given check, Logged today row appears" width="220">
</p>

Pick a pet, tap **Log dose now** on the medication row. The Today screen
records the morning dose as `Given ✓` and a row appears under **Logged today**.

The Reminders tab lists every active schedule, and Settings holds the theme
picker alongside the Material You toggle and the JSON export/import controls.

<p align="center">
  <img src="docs/screenshots/05-reminders.png" alt="Reminders list: Luna's Methimazole, twice daily for 3650 days" width="220">
  &nbsp;
  <img src="docs/screenshots/04-settings.png" alt="Settings: theme picker Auto/Light/Dark, Material You toggle, Data export and import, Diagnostics" width="220">
</p>

## What it deliberately doesn't do

The app has no symptom checker. There are no drug-interaction warnings, no
dose-safety checks, no diagnostic content of any kind. The app is not a vet
and the design refuses to act like one.

It also doesn't talk to the network. No cloud sync. No analytics. No crash
reporting either. A CI check fails the build if a network library shows up
in the dependency graph at all.

## Stack

Kotlin 2.0 with Compose Multiplatform UI. A KMP `shared` module holds the
domain models and repository contracts. The schedule calculator (a pure
function that projects a `Schedule` plus its phases into a list of
`ScheduledDose` values for a given window) also lives there and is fully
implemented; its 15-case test-as-spec passes green. SQLDelight is wired in
for the on-disk schema, but the current scaffold uses in-memory fakes for
the repositories so the UI can be reviewed before the persistence layer
ships. DI is via Koin. The reminder-firing path will use AlarmManager for
exact firing and WorkManager for the recurring sweep.

The Android app targets API 24 (Android 7.0) and up.

## Build

You need JDK 17 and an Android SDK at API 34 or later. JDK versions newer
than 17 have rough edges with the Kotlin 2.0 + AGP 8.7 combination, so stay on
17 until the toolchain catches up.

```bash
brew install openjdk@17
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
./gradlew :androidApp:installDebug
```

To run the shared-module tests without an Android SDK:

```bash
./gradlew :shared:jvmTest --console=plain
```

The shared-module tests are the test-as-spec contract for the schedule
calculator. Fifteen cases lock down empty-result fast paths, phase
concatenation, end-date-inclusive semantics, day-interval (skip-day)
behavior, the malformed-input throw discipline, and the ADR-0008 bounds
(window ≤ 30 days, event count ≤ 100,000). All fifteen pass green; the
calculator is fully implemented.

## Repository layout

```
toebeans/
  shared/      KMP shared module
  androidApp/  Android app: Compose UI, fake repositories, theme prefs
  docs/
    adr/         Short architecture decisions (MADR format)
    screenshots/ Emulator captures embedded in this README
    ARCHITECTURE.md
    ROADMAP.md
  scripts/     Build helpers and CI checks
```

## Contributing

This is a personal project. If that changes, contribution guidelines will go
here.

## Security and privacy

Every record lives in app-private storage. Nothing leaves the device. There
is no opt-in or opt-out for telemetry because there is none to opt into. The
threat model is in [SECURITY.md](SECURITY.md).

The first-launch dialog states the local-only posture in plain language and
offers an opt-in demo dataset before any data is written.

<p align="center">
  <img src="docs/screenshots/00-welcome-dialog.png" alt="First-launch welcome dialog: toebeans is a local-first medication reminder, with Load demo data and Start fresh options" width="280">
</p>

## License

[AGPL-3.0-or-later](LICENSE).
