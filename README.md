# toebeans

Pet medication tracker for Android. Local-only. No cloud, no telemetry, no
third-party services.

Status: `v0.1.0-dev`, pre-MVP scaffold. The dose-log surface works end-to-end.
The reminder-firing path lands in the next milestone.

## What it does today

* Add pets with weight and birthdate, plus a free-text notes field.
* Add medications underneath each pet.
* Define a schedule: start date, dose times, doses per day.
* Tap **Log dose now** on a medication to record a dose given.
* See `Last dose: 2h ago` on each row, or `just now` / `yesterday` /
  `on May 13` once enough time has passed.
* Theme picker (Auto / Light / Dark). Optional Material You dynamic color on
  Android 12 and up.

## What it deliberately doesn't do

The app has no symptom checker. There are no drug-interaction warnings, no
dose-safety checks, no diagnostic content of any kind. The app is not a vet
and the design refuses to act like one.

It also doesn't talk to the network. No cloud sync. No analytics. No crash
reporting either. A CI check fails the build if a network library shows up
in the dependency graph at all.

## Stack

Kotlin 2.0 with Compose Multiplatform UI. A KMP `shared` module holds the
domain models and repository contracts. The schedule-calculator interface
lives there too, behind a stub implementation for now. SQLDelight is wired
in for the on-disk schema, but the current scaffold uses in-memory fakes for
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

The nine failing tests in `SchedulePhaseRulesTest` are intentional. They are
written as a spec for the schedule-calculator implementation, which is still
a stub.

## Repository layout

```
toebeans/
  shared/      KMP shared module
  androidApp/  Android app: Compose UI, fake repositories, theme prefs
  docs/
    adr/       Short architecture decisions (MADR format)
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

## License

[AGPL-3.0-or-later](LICENSE).
