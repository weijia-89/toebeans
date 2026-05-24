# Changelog

All notable changes are recorded here. The format follows [Keep a
Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security

* **CVE-2026-45799 (Wire):** Force `com.squareup.wire:wire-runtime` 6.3.0 and
  substitute discontinued `wire-runtime-jvm` on the `:macrobench` benchmark
  classpath (Dependabot alerts #44, #45). No app-runtime classpath change.

### Added

* **AppModule SQLDelight DI swap** ([PR #43](https://github.com/weijia-89/toebeans/pull/43), merge `951ac09`): `ToebeansDatabase` singleton with ADR-0010 `SqliteForeignKeysCallback`; `PetRepository`, `MedicationRepository`, and `ScheduleRepository` bind to SQLDelight impls. `DoseEventRepository` remains in-memory fake until its SqlDelight implementation lands.
* **SqlDelight `MedicationRepository`** (M1 step 3, option B): `SqlDelightMedicationRepository` satisfies `MedicationRepositoryContract`; green `SqlDelightMedicationRepositoryContractTest` on JVM. First-launch demo seeding upserts pets, meds, and schedules via repositories (no module-level fake maps).
* **SqlDelight `ReminderLookup`** (M1.3): `SqlDelightReminderLookup` resolves dose-event ids to
  `ScheduledReminder` snapshots for the receiver fire path; `DoseAlarmReceiver` default lookup
  opens SQLDelight outside Koin. `selectDoseEventById` indexed query (sdk-review F2). Contract
  suite includes schedule-delete CASCADE row-gone case (F3). Green
  `SqlDelightReminderLookupContractTest` + Robolectric `DoseAlarmReceiverLookupTest`.
  ADR-0011 `fired_at` write-before-show deferred (`@Ignore` spec in contract).
* **SqlDelight `ScheduleRepository`** (M1 step 3): `SqlDelightScheduleRepository` satisfies `ScheduleRepositoryContract`; green `SqlDelightScheduleRepositoryContractTest` on JVM.
* **BootReceiver phase 2** ([PR #40](https://github.com/weijia-89/toebeans/pull/40), merge `b5da01b`): on `RECEIVE_BOOT_COMPLETED`, replays alarm rehydration within a 72-hour horizon via `ToebeansApp.rehydrateBootAlarms`; schedule lookup remains stubbed (empty schedule → zero alarms, no crash).
* Dose-log surface on the Pet Detail screen. Each medication row gets a
  **Log dose now** button when an active schedule exists, plus a `Last dose:
  X ago` subtitle once a dose has been recorded.
* `DoseEventRepository` contract in `shared/.../core/data/`, with an
  in-memory fake implementation behind it. The SQLDelight-backed
  implementation lands when the persistence layer ships.
* Functional Settings screen. Theme override (Auto, Light, Dark) and an
  optional Material You toggle are persisted via `SharedPreferences` and take
  effect immediately. There is also an `Export data` placeholder that surfaces
  a Toast for now; the encrypted backup format ships later, on its own ADR.
* Empty-state illustrations across the main screens. Each empty surface now
  has an emoji glyph inside a sage-tinted circle above the headline, so the
  blank pet list and the no-medications detail screen read as intentional
  rather than broken.
* Custom paw `ImageVector` for the Pets bottom-navigation tab. Replaces a
  Material pets icon that did not match the warm palette.
* WCAG semantics pass: explicit headings, merged-label semantics on the pet
  identity card and theme rows, and `clearAndSetSemantics` on decorative
  emoji so screen readers do not announce them as content.
* Pet detail: the identity card now computes age from `birthdate` and the
  weight is shown to one decimal place.
* Today screen now shows the current date below the title and renders pet
  chips for the household roster.
* Bottom-nav-aware FAB placement and a warm color palette across screens.
* `docs/screenshots/` directory with seven captures from the `toebeans-pixel7`
  emulator. The README now embeds them inline: the Today screen as a hero
  under the status line, a three-up strip showing the dose-log flow (Pets,
  Pet Detail, Today after logging) beneath the feature bullets, the Reminders
  list and Settings paired below that, and the first-launch welcome dialog
  in the Security and privacy section.

### Changed

* The `medications`, `schedules`, `phasesByScheduleId`, and `doseEvents`
  fake stores moved from file-private to module-internal so the dose-event
  fake can join across them without duplicating storage.
* `PetDetailViewModel` now exposes a richer `MedicationWithStatus` row
  type that pre-computes the active schedule id and the last given dose for
  each medication. The previous `Medication`-only row required N+1 flows in
  Compose; the new shape moves the join to the view model where it is
  testable without spinning up Compose.

### Fixed

* `PetEdit` no longer drops `createdAt` or `archivedAt` on save. The screen
  was treating both as defaults instead of preserving the existing values.
* The pet detail screen now subscribes to `observeById` so edits made in the
  edit flow appear immediately on return, instead of leaving the previous
  view stale until a manual refresh.

### Verified on the emulator

* All four main screens (Today, Pets, Pet Detail, Settings) render correctly
  in light and dark themes.
* Toggling Dark in Settings flips the entire app to dark mode without an app
  restart, and back to Light or Auto the same way.
* Tapping **Log dose now** on Luna's Methimazole row records a dose and the
  `Last dose: just now` subtitle appears reactively, with no app restart.
* All five CI fitness functions pass on the current head:
  * `no-network`
  * `no-analytics`
  * `permission allow-list`
  * `scheduler purity`
  * `pre-commit hook` parity

## [0.0.1] – Initial scaffold

### Added

* Kotlin Multiplatform plus Compose Multiplatform Gradle skeleton.
* SQLDelight schema for `Pet`, `Medication`, `Schedule`, `SchedulePhase`,
  and `DoseEvent`.
* `ScheduleCalculator` interface in `shared/`, with a stub implementation
  and a failing `SchedulePhaseRulesTest` that pins the taper-correctness
  contract before the implementation ships.
* An AGPL-3.0-or-later license, plus a `SECURITY.md` threat model and a
  draft `ROADMAP.md`.
* Eight short ADRs covering KMP+Compose, the AlarmManager + WorkManager
  hybrid, the local-first posture, the tapering schedule model, the
  dose-event firing path, deferred mutation testing, the timezone +
  travel-mode policy, and a target performance class.
* CI workflow with five fitness functions. They cover networking,
  analytics, permission additions, scheduler purity, plus a parity check on
  the agent-contract documents.

[Unreleased]: https://github.com/weijia-89/toebeans/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/weijia-89/toebeans/releases/tag/v0.0.1
