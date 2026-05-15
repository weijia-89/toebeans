# toebeans — architecture

**Audience:** future contributors (human or agent) who need to understand the shape of the system before changing it.

**Voice:** descriptive, third-person, present tense (per [`codeit` Section 7](https://github.com/wei/codeit.skill)).

---

## Components

```
+--------------------------------------------------+
|              Compose UI  (androidApp)            |
|  Screens: Home / AddPet / AddMedication /        |
|           Schedule / DoseHistory / Settings      |
+----------------------+---------------------------+
                       |
                       v
+--------------------------------------------------+
|         Shared core  (KMP, commonMain)           |
|  Repositories <-> Scheduler <-> Backup           |
|  Domain models: Pet, Medication, Schedule,       |
|                 SchedulePhase, DoseEvent         |
+--------+---------------------+-------------------+
         |                     |
         v                     v
+----------------+    +--------------------------+
| SQLDelight DB  |    |   Platform actuators     |
| (app-private   |    |   Android:               |
|  file storage) |    |   - AlarmManager (exact) |
|                |    |   - WorkManager (recur)  |
|                |    |   - Notifications        |
|                |    |   iOS (slice 5):         |
|                |    |   - UNUserNotifications  |
+----------------+    +--------------------------+
```

## Module boundaries (Parnas-aligned)

Each module hides a **design decision likely to change**, not a flow step.

| Module | Hides | Justification |
|---|---|---|
| `core/scheduler/` | The algorithm that turns a `Schedule + Phases` into a list of `ScheduledDose` instants. Hides DST handling, phase-boundary rules, missed-dose detection. | Algorithm details are likely to change as we add PRN, load-then-maintain, snoozes. UI and persistence should not have to change. |
| `core/backup/` | The serialization format and encryption details of the export file. | The format may rev for new fields; encryption parameters may rev for new key derivation. Consumers (Settings UI) only see "export()" / "import()". |
| `core/repository/` | The query interface to SQLDelight. UI never sees SQL. | Allows us to swap the persistence layer (slice 5: iOS may use the same SQLDelight DB; slice 6: cloud sync layer may add a remote source-of-truth). |
| `androidApp/notifications/` | The mechanics of AlarmManager + WorkManager + NotificationChannel lifecycle. | OEM-specific battery workarounds will accumulate here. The rest of the app sees only `NotificationActuator.schedule(dose: ScheduledDose)`. |

## Why no ports-and-adapters at v1

Cockburn's hexagonal architecture is justified **only when 2+ adapter implementations are planned** (per [`code-helper` §6.4](https://github.com/wei/code-helper.skill)).

At v1:

- One persistence adapter (SQLDelight, used identically on Android and iOS).
- One notification adapter per platform (Android + iOS), each compiled into its own platform target.
- One UI adapter (Compose Multiplatform).

KMP's `expect`/`actual` already enforces the port boundary for cross-platform code. We do not add a redundant interface layer in `commonMain` "just in case." A second adapter implementation in any layer triggers a refactor with an ADR.

## Data flow: a reminder firing

```
[boot or app open]
  -> ReminderRescheduler.rescheduleNext72h()
     -> reads Schedules from SQLDelight
     -> calls ScheduleCalculator.computeScheduledTimes(schedule, phases, tz, now, now+72h)
     -> writes pending DoseEvents to SQLDelight
     -> registers AlarmManager.setExactAndAllowWhileIdle for each
[alarm fires]
  -> AndroidAlarmReceiver.onReceive()
     -> updates DoseEvent.fired_at = now
     -> NotificationActuator.show(doseEvent)
        -> NotificationChannel "medication-critical"
        -> two inline actions: Given / Skipped
[user taps Given]
  -> NotificationActionReceiver.onReceive(action=GIVEN)
     -> updates DoseEvent.status = 'given', resolved_at = now
     -> cancels notification
     -> ReminderRescheduler.rescheduleNext72h() if the queue is shallow
```

## Threading and concurrency

- All scheduler logic is pure-functional and runs on `Dispatchers.Default`.
- DB access via SQLDelight's coroutine bindings, on `Dispatchers.IO`.
- UI on `Dispatchers.Main` (Compose-managed).
- AlarmManager callbacks run on the main thread; they immediately delegate to a coroutine on `Dispatchers.IO`.

## Persistence schema

See [`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md` §5](superpowers/specs/2026-05-14-toebeans-mvp-design.md#5-data-model) for the canonical schema.

DoseEvents are materialized **lazily** at a 72-hour horizon. Rationale:

- A 2× daily medication over a 90-day phase would otherwise pre-generate 180 rows.
- Tapering phases mean dose schedules can change; pre-generation makes edits expensive.
- 72h is long enough that AlarmManager has all near-term events scheduled even if the app is killed and not woken until boot.

## Configuration

There is no runtime configuration at v1. Every value that varies between environments is a Kotlin `const` or a `BuildConfig` constant.

`AUTO_RESCHEDULE_HORIZON_HOURS = 72`
`MAX_PENDING_PER_PET = 64`
`MISSED_DOSE_TIMEOUT_HOURS = 4`

Changing any of these requires an ADR.

## Open questions (slice 2+)

These are **not** part of the v1 design. Listed here so contributors don't accidentally bake in answers.

- How does caregiver sharing handle conflicts when two devices both log "Given"? (Likely: last-write-wins by `resolved_at`; explicit conflict UI deferred.)
- Multi-device sync without cloud (peer-to-peer over local network)? Probably not worth it; cloud tier wins.
- iOS adoption of AlarmManager-equivalent. UNUserNotifications is calendar-precise; no exact-alarm-permission UX overhead.
- When does on-device OCR start to matter? See dossier §8.2 slice 4 decision gate.
