# toebeans roadmap

A milestone names who can use the app without a broken path.

This file is the canonical answer to what is feasible now.
When a chat claim and this file disagree, this file wins.

Last updated: 2026-11-09. Current milestone: Milestone 1.2.

## Milestone 1.2: Internal beta (current)

Ship-ability: one developer can install a debug build and use the local app.
`docs/play-store-internal-testing-walkthrough.md` stays unused until a tester cannot install from the F-Droid path.
The day-14 read has not started.
History: [#134](https://github.com/weijia-89/toebeans/pull/134) replaces the Play Store internal-testing row with the F-Droid tester channel.

### Open

| Item | Tier | Status |
|---|---|---|
| Gradle `9.9.0` wrapper shipped in pull request 147 (`78171d31`). The `wire-runtime-jvm` exclude stays. `checkDebugAarMetadata` passes on that wrapper with the AndroidX and datetime pins. AGP stays 8.7.0. | vibe-dangerous | shipped |
| AGP 9. Closed pull request 141 needs Gradle `9.6.0` or newer. `assembleDebug` is green on the pin branch. Start AGP 9 only after that branch is on main. | vibe-dangerous | blocked-operator-merge |
| GitHub Actions `ci` is `startup_failure` with zero jobs. Run `42554072648` is on main. Run `42630625548` is on pull request 147. A human checks Actions billing and the repo Actions setting. | n/a | blocked-operator-credential |
| F-Droid tester channel for Victoria, the operator, and the operator's spouse. Tutorial: `docs/fdroid-tester-tutorial.md`. This run does not publish a release. | n/a | blocked-operator-runtime |
| Public F-Droid listing. The operator files the fdroiddata merge request later. This run does not file it. | n/a | blocked-operator-runtime |
| Day-14 adoption read. The developer reads the soak log in `docs/soak-test-protocol.md`. The app does not collect analytics. | n/a | blocked-operator-runtime |

Not this run.
ADR-0007 G1 and G3 stay in `docs/issues/v0.1-followups.md` item 2.
Argon2id stays unwired until an ADR-0016 v2 trigger.
Vet-visit tables stay in the vet-visit ADR-0020.
A later scheduler change must keep given, skipped, and missed rows on edit.

### Shipped

| What | PR | Date |
|---|---|---|
| Style lab sign-off, terracotta-warm | decision in `docs/style-lab/DECISIONS.md` | 2026-05-26 |
| License posture AGPL-3.0 | ADR-0020 license file | 2026-05-26 |
| Distribution wedge D1, pet-owner direct | operator decision | 2026-05-26 |
| Soak-test protocol | `docs/soak-test-protocol.md` | 2026-05-26 |
| Beta smoke checklist. Anchor-mode prompt came later. | `docs/beta-smoke-add-medication.md`, [#99](https://github.com/weijia-89/toebeans/pull/99), [#100](https://github.com/weijia-89/toebeans/pull/100) | 2026-05-26, 2026-07-23, 2026-07-25 |
| First-launch dialog. Stores start empty. Demo data loads on tap. | PR not recorded | 2026-05 |
| Unified add-medication modal | [#100](https://github.com/weijia-89/toebeans/pull/100) | 2026-07-25 |
| On-device medicine name search | `27426dc` | 2026-07 |
| Dose unit picker | [#97](https://github.com/weijia-89/toebeans/pull/97) | 2026-07-20 |
| Today pet filter, scroll, and Edit | [#75](https://github.com/weijia-89/toebeans/pull/75) | 2026-05-28 |
| History tab, read-only, given-only feed | [#119](https://github.com/weijia-89/toebeans/pull/119) | 2026-10-24 |
| All-status history query | [#122](https://github.com/weijia-89/toebeans/pull/122) | 2026-10-25 |
| History lists every status for 30 days, and a pet chip filters that list | [#131](https://github.com/weijia-89/toebeans/pull/131) | 2026-11-08 |
| `BootReceiver` manifest flag is `false`. `schedule` enables the component. | [#133](https://github.com/weijia-89/toebeans/pull/133) | 2026-11-08 |
| Kotlin `2.4.21` and Kover `0.9.11` | [#140](https://github.com/weijia-89/toebeans/pull/140) | 2026-11-08 |
| ktlint `14.2.0` | [#144](https://github.com/weijia-89/toebeans/pull/144) | 2026-11-09 |
| Demo load writes the 72-hour horizon and registers those alarms | [#132](https://github.com/weijia-89/toebeans/pull/132) | 2026-11-08 |
| TalkBack reaches the night-dose and midnight-straddle buttons | [#123](https://github.com/weijia-89/toebeans/pull/123) | 2026-10-25 |
| Rendered Compose tests (denial banner, delete dialog, calculator error, night-dose, midnight-straddle) | [#116](https://github.com/weijia-89/toebeans/pull/116), [#117](https://github.com/weijia-89/toebeans/pull/117), [#121](https://github.com/weijia-89/toebeans/pull/121) | 2026-10-24 |
| Exact-alarm denial banner | [#105](https://github.com/weijia-89/toebeans/pull/105) | 2026-10-13 |
| Notification denial: outcome, crash-log line, Today banner, first-launch permission ask, silent-notify gate | [#108](https://github.com/weijia-89/toebeans/pull/108), [#110](https://github.com/weijia-89/toebeans/pull/110), [#111](https://github.com/weijia-89/toebeans/pull/111), [#112](https://github.com/weijia-89/toebeans/pull/112), [#113](https://github.com/weijia-89/toebeans/pull/113) | 2026-10-13 to 2026-10-14 |
| Manual QA case catalog | [#107](https://github.com/weijia-89/toebeans/pull/107), [#109](https://github.com/weijia-89/toebeans/pull/109) | 2026-10-13 |
| Gradle `9.9.0` wrapper. `help` and dependency submission configure. `wire-runtime-jvm` is an exclude. | [#147](https://github.com/weijia-89/toebeans/pull/147), `78171d31` | 2026-11-09 |

## History

### Milestone 1 (shipped)

Ship-ability at close: a developer plus 1 to 3 testers on a debug build.
The 30-day soak did not run.
That read sits in Milestone 1.2.

| What | PR | Date |
|---|---|---|
| SqlDelight `ScheduleRepository` and contract tests | [#42](https://github.com/weijia-89/toebeans/pull/42) | 2026-05-23 |
| SqlDelight `PetRepository` and contract tests | PR not recorded | 2026-05 |
| AppModule binds pet, schedule, medication, and dose-event repos to SqlDelight | [#43](https://github.com/weijia-89/toebeans/pull/43) | 2026-05 |
| SqlDelight `MedicationRepository` and contract tests | PR not recorded | 2026-05 |
| SqlDelight `DoseEventRepository` and contract tests | PR not recorded | 2026-05 |
| Schedule Detail delete, hard delete, foreign-key cascade | PR not recorded | 2026-05 |
| `DoseAlarmReceiver` reads the dose row from SqlDelight | PR not recorded | 2026-05-24 |
| `BootReceiver` rehydrates the 72-hour horizon | [#52](https://github.com/weijia-89/toebeans/pull/52) | 2026-05-26 |
| `RequestCodeAllocator` replaces `hashCode` request codes | PR not recorded | 2026-05-17 |
| Anchor mode on the scheduler, Schedule Create, and Add Medication | [#98](https://github.com/weijia-89/toebeans/pull/98), [#99](https://github.com/weijia-89/toebeans/pull/99), [#100](https://github.com/weijia-89/toebeans/pull/100) | 2026-07-21 to 2026-07-25 |
| Reminders tab and Schedule Detail | PR not recorded | 2026-05 |
| Night-dose warning and calculator error banner on Schedule Create | PR not recorded | 2026-05-17, 2026-05-18 |
| Backup export and import, plain JSON | ADR-0016 | 2026-05 |
| Macrobench cold start and Reminder List scroll | ADR-0008 | 2026-05 |
| `StaleEventGuard` on Today | PR not recorded | 2026-05 |
| Crash-log fitness gate, no domain symbols in the handler | ADR-0009 | 2026-05 |
| Bottom nav Today, Reminders, Pets, Settings | [#95](https://github.com/weijia-89/toebeans/pull/95) | 2026-07-16 |
| Wave 1 Today, Reminders, and Pets polish | [#75](https://github.com/weijia-89/toebeans/pull/75), [#76](https://github.com/weijia-89/toebeans/pull/76), [#77](https://github.com/weijia-89/toebeans/pull/77), [#78](https://github.com/weijia-89/toebeans/pull/78), [#80](https://github.com/weijia-89/toebeans/pull/80) | 2026-05-28 |

Detail lives in [CHANGELOG](../CHANGELOG.md).
Do not restate a pull request here.

### v0.1 scaffold (shipped)

This milestone is not current.
Ship-ability at the time was an engineering artifact.

| What | PR | Date |
|---|---|---|
| KMP and Compose Multiplatform scaffold (JDK 17) | PR not recorded | 2026-05 |
| Domain models and SqlDelight schema | PR not recorded | 2026-05 |
| `DefaultScheduleCalculator` and `SchedulePhaseRulesTest` | PR not recorded | 2026-05 |
| Backup codec, PBKDF2 and AES-256-GCM | PR not recorded | 2026-05 |
| Notification actuator and boot replay | PR not recorded | 2026-05 |
| CI fitness gates, lint, tests, and Android assemble | PR not recorded | 2026-05 |
| ADRs 0001 through 0020. Two files share the number 0020. | `docs/adr/` | 2026-05 |
| Calibration log and Kover line-coverage gate on `scheduler/` and `backup/` | ADR-0006 | 2026-05 |
| Koin smoke test, pet and medication delete, `DoseEvent.medicationId`, local crash log | PR not recorded | 2026-05 |
| Test failures fail the build (`ignoreFailures` removed) | PR not recorded | 2026-05 |

### Design sign-off (shipped)

Terracotta-warm is the chosen pack.
Sage stays the tertiary color for Given.
Material You stays as already shipped.
Recorded 2026-05-26 in `docs/style-lab/DECISIONS.md`.
