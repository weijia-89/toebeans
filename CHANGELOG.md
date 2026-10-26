# Changelog

All notable changes are recorded here. The format follows [Keep a
Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). One section per
large chunk of merged work.

## [0.3.0] - Notification denial UX, History tab, rendered UI tests (2026-10)

### Added

- **History tab** ([#119](https://github.com/weijia-89/toebeans/pull/119), 2026-10-24): fifth bottom-nav tab with a read-only, newest-first 30-day dose-event log (status label, local scheduled time, alarm-fired line), joined across every pet.
- **Scoped all-statuses history query** ([#122](https://github.com/weijia-89/toebeans/pull/122), 2026-10-25): `DoseEventRepository.observeRecentForHistory(sinceInclusive, limit)` returns pending, given, skipped, and missed events inside a parameterized window, newest first, truncated at the SQL level (`selectRecentForHistory`). Contract pinned by `SqlDelightDoseEventRepositoryHistoryQueryTest` (test-as-spec: the red commit was reviewed before the implementation landed).
- **Night-dose affirm button reachable by TalkBack** ([#123](https://github.com/weijia-89/toebeans/pull/123), 2026-10-25): the night-dose and midnight-straddle banners use merged semantics with a polite live region, so the affirm and dismiss buttons stay in the accessibility tree instead of being erased by `clearAndSetSemantics`.
- **Rendered banner tests for B7, B8, B9** ([#117](https://github.com/weijia-89/toebeans/pull/117), 2026-10-24): three rendered Compose tests close the ADR-0013 F4 gap. The schedule-delete dialog, the inline calculator error banner, and the night-dose affirm banner now have rendered-UI contract tests instead of ViewModel-only coverage.
- **Midnight-straddle banner render test** ([#121](https://github.com/weijia-89/toebeans/pull/121), 2026-10-24): pins the exact banner copy, the polite live region, and the absent-when-flag-false case, with the flag derived through the production detection rule.
- **Denied-notification device QA run** ([#118](https://github.com/weijia-89/toebeans/pull/118), 2026-10-24): recorded manual QA run of the ADR-0011 denial path on the `toebeans-pixel7` emulator, cataloged at `docs/manual-qa/cases/notification-denied-dose-alarm.md`.
- **ADR-0013 rendered-UI test adoption** ([#116](https://github.com/weijia-89/toebeans/pull/116), 2026-10-24): first rendered-UI contract test via `androidx.compose.ui:ui-test-junit4-android` under Robolectric, pinning the ADR-0011 denial banner sentence and settings tap. Adds test-only Gradle deps (`ui-test-junit4-android`, `ui-test-manifest` debug-only) plus `testOptions.unitTests.isIncludeAndroidResources` and a `robolectric.properties` default application so existing Robolectric tests keep their pre-adoption boot behavior.
- **Manual QA case catalog** ([#107](https://github.com/weijia-89/toebeans/pull/107), [#109](https://github.com/weijia-89/toebeans/pull/109), 2026-10-13): one file per recorded manual case under `docs/manual-qa/cases/`, indexed from `docs/manual-qa.md`; PR test plans that name device steps must add a case file (CI-enforced).
- **Exact-alarm denial banner** ([#105](https://github.com/weijia-89/toebeans/pull/105), 2026-10-13, ADR-0002): Today shows a non-dismissable banner when exact alarms are denied. Copy names the 10 minute window. "Open alarm settings" opens the system Alarms and reminders screen.

### Fixed

- **Silent notify gate** ([#113](https://github.com/weijia-89/toebeans/pull/113), 2026-10-14): CI fails if `AndroidNotificationActuator.kt` calls `.notify(` before `areNotificationsEnabled()` (ADR-0011).
- **First launch asks for notifications** ([#112](https://github.com/weijia-89/toebeans/pull/112), 2026-10-14): on API 33 the welcome dialog requests `POST_NOTIFICATIONS` before it closes. A denial shows "Notifications are off, so a dose alarm will not show. Tap to fix in Settings." on Today. A grant does not show that line.
- **Today banner when a fired dose was not delivered** ([#111](https://github.com/weijia-89/toebeans/pull/111), 2026-10-14): if one of the latest ten dose rows has `fired_at` and no `NotificationAudit` row, Today shows the ADR-0011 sentence. A tap opens notification settings. A successful `notify` writes the audit row.
- **Denied dose alarms leave a crash-log line** ([#110](https://github.com/weijia-89/toebeans/pull/110), 2026-10-13): when notifications are off, `DoseAlarmReceiver` still writes `fired_at` and appends `POST_NOTIFICATIONS_DENIED_AT_FIRE` plus the reminder id. Pet and medication names stay out of the log.
- **Notification denial is visible to the caller** ([#108](https://github.com/weijia-89/toebeans/pull/108), 2026-10-13): `AndroidNotificationActuator.show()` returns `NotificationOutcome.PermissionDenied` when notifications are off, and does not call `notify`. Success returns `NotificationOutcome.Posted`.

### Security

- **Wire 6.3.0** ([#104](https://github.com/weijia-89/toebeans/pull/104), 2026-10-14): force `com.squareup.wire:wire-runtime` 6.3.0 (CVE-2026-45799) and substitute discontinued `wire-runtime-jvm` on the `:macrobench` benchmark classpath (Dependabot alerts #44, #45). No app-runtime classpath change.
- **Okio 3.17.0:** force `com.squareup.okio:okio` and `okio-jvm` on Gradle plugin and `:macrobench` classpaths (CVE-2023-3635, Dependabot alert #46). Not on `:androidApp:releaseRuntimeClasspath` or `:shared:jvmRuntimeClasspath`.

## [0.2.0] - Medication UX and anchor mode (2026-07)

### Added

- **Unified medication modal (create flow)** ([#100](https://github.com/weijia-89/toebeans/pull/100), 2026-07-25): one surface for medication, initial schedule window, dose times, and notification settings; required fields are name and dose amount only.
- **Dose unit picker** ([#97](https://github.com/weijia-89/toebeans/pull/97), 2026-07-20): separate unit dropdown (tbsp, tsp, g, mL, tablet, and more) alongside the dose amount.
- **Medicine name search** (`27426dc`, 2026-07): local typeahead against an on-device medicine-name index in the medication form; no cloud.
- **ADR-0007 anchor mode** ([#98](https://github.com/weijia-89/toebeans/pull/98), 2026-07-21): `ELAPSED_INTERVAL` scheduling alongside `FOLLOW_PHONE`, with the SQLDelight v3 migration.
- **ADR-0007 G2 DST warnings** ([#99](https://github.com/weijia-89/toebeans/pull/99), 2026-07-23): DST-related scheduler warning surfaces, pinning kotlinx-datetime snap behavior.
- **Style-lab design handoff** (`8531bb8`): `docs/design-handoff/manifest.yaml` with per-screen `androidApp/` Compose paths; `scripts/style_lab_handoff_check.sh` compares Kotlin mtimes to `docs/style-lab/index.html`; Cursor session and pre-write hooks; operator doc `docs/design-handoff.md`. Magic Patterns MCP is denied in-repo (local-only policy).

### Changed

- **Bottom nav order** ([#95](https://github.com/weijia-89/toebeans/pull/95), 2026-07-16): **Today, Reminders, Pets, Settings**, Reminders adjacent to Today (supersedes the [#74](https://github.com/weijia-89/toebeans/pull/74) order of 2026-05-28).

### Fixed

- **Trainer PR head sync** ([#96](https://github.com/weijia-89/toebeans/pull/96), 2026-07-16): guard against an empty `gh api` response in `trainer_pr_review_patch_head.sh`.

## [0.1.0] - Milestone 1, Wave 1 merge train (2026-05-28)

### Added

- **Today M1.2 UX** ([#75](https://github.com/weijia-89/toebeans/pull/75)): scrollable Today body, in-page pet filter, Edit beside Log dose.
- **Trainer PR head sync** ([#76](https://github.com/weijia-89/toebeans/pull/76)): port `trainer_pr_review_patch_head.sh` from buds for the CI gate.
- **Reminders add affordances** ([#77](https://github.com/weijia-89/toebeans/pull/77)): extended FAB when schedules exist; empty-state add CTA.
- **Pets tab IA** ([#78](https://github.com/weijia-89/toebeans/pull/78)): compact top-bar add; drop extended FAB on Pets list.
- **Medication edit context** ([#80](https://github.com/weijia-89/toebeans/pull/80)): read-only pet and schedule hint on the edit screen.
- **AppModule SQLDelight DI swap** ([#43](https://github.com/weijia-89/toebeans/pull/43), merge `951ac09`): `ToebeansDatabase` singleton with ADR-0010 `SqliteForeignKeysCallback`; `PetRepository`, `MedicationRepository`, `ScheduleRepository`, and `DoseEventRepository` bind to SQLDelight impls on the shared `toebeans.db` file.
- **SqlDelight `DoseEventRepository`** (M1 step 3): `SqlDelightDoseEventRepository` persists dose rows for UI quick-log, backup import, and receiver lookup (`selectDoseEventById`); green `SqlDelightDoseEventRepositoryContractTest` on JVM. Production DI no longer uses `FakeDoseEventRepository`.
- **SqlDelight `MedicationRepository`** (M1 step 3, option B): satisfies `MedicationRepositoryContract`; green contract test on JVM. First-launch demo seeding upserts pets, meds, and schedules via repositories.
- **SqlDelight `ReminderLookup`** (M1.3): resolves dose-event ids to `ScheduledReminder` snapshots for the receiver fire path; `DoseAlarmReceiver` default lookup opens SQLDelight outside Koin. Indexed `selectDoseEventById` query. Contract suite includes the schedule-delete CASCADE row-gone case. Green `SqlDelightReminderLookupContractTest` + Robolectric `DoseAlarmReceiverLookupTest`.
- **SqlDelight `ScheduleRepository`** (M1 step 3): satisfies `ScheduleRepositoryContract`; green contract test on JVM.
- **BootReceiver phase 3**: on `RECEIVE_BOOT_COMPLETED`, `ToebeansApp.loadPendingRemindersInHorizon` queries pending `DoseEvent` rows inside the 72-hour window via SQLDelight and re-schedules AlarmManager entries (empty DB still schedules zero alarms).
- **BootReceiver phase 2** ([#40](https://github.com/weijia-89/toebeans/pull/40), merge `b5da01b`): replays alarm rehydration within a 72-hour horizon via `ToebeansApp.rehydrateBootAlarms`; schedule lookup was stubbed until phase 3. Receiver SQLDelight lookup in [#52](https://github.com/weijia-89/toebeans/pull/52).
- Dose-log surface on the Pet Detail screen: a **Log dose now** button per medication row when an active schedule exists, plus a `Last dose: X ago` subtitle once a dose has been recorded.
- `DoseEventRepository` contract in `shared/.../core/data/` with an in-memory fake implementation behind it.
- Functional Settings screen: theme override (Auto, Light, Dark) and an optional Material You toggle persisted via `SharedPreferences`; an `Export data` placeholder ahead of the backup format decision.
- Empty-state illustrations across the main screens: an emoji glyph inside a sage-tinted circle above the headline.
- Custom paw `ImageVector` for the Pets bottom-navigation tab.
- WCAG semantics pass: explicit headings, merged-label semantics on the pet identity card and theme rows, `clearAndSetSemantics` on decorative emoji so screen readers do not announce them as content.
- Pet detail: identity card computes age from `birthdate`; weight shown to one decimal place.
- Today screen shows the current date below the title and renders pet chips for the household roster.
- Bottom-nav-aware FAB placement and a warm color palette across screens.
- `docs/screenshots/` directory with seven captures from the `toebeans-pixel7` emulator, embedded inline in the README.

### Changed

- The `medications`, `schedules`, `phasesByScheduleId`, and `doseEvents` fake stores moved from file-private to module-internal so the dose-event fake can join across them without duplicating storage.
- `PetDetailViewModel` now exposes a richer `MedicationWithStatus` row type that pre-computes the active schedule id and the last given dose for each medication, moving the join from Compose into the testable view model.

### Fixed

- `PetEdit` no longer drops `createdAt` or `archivedAt` on save; both are preserved instead of being treated as defaults.
- The pet detail screen subscribes to `observeById` so edits made in the edit flow appear immediately on return.
- **ToebeansApp KDoc** (sdk-review F1): startup docs match the AppModule SqlDelight DoseEvent DI; stale fake-repository wording removed.
- **Codereview P4 pass** (F1/F3/F4/F5): `FakeDoseEventRepository` KDoc reflects SqlDelight DI; `DoseEventRepositoryContract` + SqlDelight subclass; `selectAllGivenSinceLimited` (SQL `LIMIT 50`); AppModule/receiver ordering comments; ROADMAP date 2026-05-25.

### Verified on the emulator

- All four main screens (Today, Pets, Pet Detail, Settings) render correctly in light and dark themes.
- Toggling Dark in Settings flips the entire app to dark mode without an app restart, and back to Light or Auto the same way.
- Tapping **Log dose now** on Luna's Methimazole row records a dose and the `Last dose: just now` subtitle appears reactively, with no app restart.
- All six CI fitness functions pass on the current head: `no-network`, `no-analytics`, `permission allow-list`, `scheduler purity`, `AGENTS/CLAUDE` parity.

## [0.0.1] - Initial scaffold

### Added

- Kotlin Multiplatform plus Compose Multiplatform Gradle skeleton.
- SQLDelight schema for `Pet`, `Medication`, `Schedule`, `SchedulePhase`, and `DoseEvent`.
- `ScheduleCalculator` interface in `shared/`, with a stub implementation and a failing `SchedulePhaseRulesTest` that pins the taper-correctness contract before the implementation ships.
- An AGPL-3.0-or-later license, plus a `SECURITY.md` threat model and a draft `ROADMAP.md`.
- Eight short ADRs covering KMP+Compose, the AlarmManager + WorkManager hybrid, the local-first posture, the tapering schedule model, the dose-event firing path, deferred mutation testing, the timezone + travel-mode policy, and a target performance class.
- CI workflow with six fitness functions covering networking, analytics, permission additions, scheduler purity, plus a parity check on the agent-contract documents.

[0.3.0]: https://github.com/weijia-89/toebeans/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/weijia-89/toebeans/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/weijia-89/toebeans/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/weijia-89/toebeans/releases/tag/v0.0.1
