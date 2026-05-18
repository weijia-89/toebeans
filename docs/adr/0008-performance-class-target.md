# ADR-0008: Performance-class target — budget 2023 Android

Date: 2026-05-15
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

toebeans is a medication-reminder app, not a heavyweight content-and-media app. The audience is "pet owners," which skews demographically toward the same device population as parents-of-young-children and price-conscious adults — i.e., budget Android.

If we let the design implicitly assume a $700 flagship, two failure modes appear:

- **Performance jank** for the actual user population (slow app start, dropped frames on the reminder list scroll, perceptible UI latency on tap).
- **OOM crashes** during 30-day-view rendering or backup export on devices with 2–3 GB total RAM.

Both are silent failure modes that the developer never sees on their Pixel.

We need an **explicit lowest-supported-device target** that every ADR and every code review can refer to.

## Decision

**The lowest supported device is the Nokia C12 / Moto G Play 2023 class.** Concretely:

| Property | Value |
|---|---|
| RAM | 2 GB total (Android Go) or 3 GB (non-Go) |
| Per-app heap budget (OS-imposed) | ~96 MB (Android Go) to ~192 MB (non-Go) |
| CPU class | Helio G37 / Snapdragon 480 / Unisoc T606 |
| Android version | API 33 (Android 13) minimum |
| Display | 720×1600, 60Hz |
| Storage | 32 GB total; users have 1–4 GB free typically |

### Bounds derived from this target (mechanically enforced)

| Bound | Value | Enforced in |
|---|---|---|
| `SchedulePhase.dosesPerDay` max | **6** (owner-care ceiling; q4h; q1h is clinical-care territory and out of scope) | `SchedulePhase.init` `require` |
| `SchedulePhase.durationDays` max | 3,650 (10 years; covers any realistic chronic taper) | `SchedulePhase.init` `require` |
| `SchedulePhase.dayInterval` max | 30 (monthly dosing is the longest interval; e.g., bravecto in dogs) | `SchedulePhase.init` `require` |
| `SchedulePhase.doseTimesLocal.size` ≤ `dosesPerDay` | (already enforced) | `SchedulePhase.init` `require` |
| `ScheduleCalculator.computeScheduledDoses` window max | 30 days (720h; covers milestone 2 "next month" view) | `computeScheduledDoses` `require` |
| `ScheduleCalculator` event-count safety cap | 100,000 events (defense in depth; the per-field caps make this unreachable for legitimate input) | `computeScheduledDoses` final-size assertion |

**On `dosesPerDay`:** the hardware (ADR-0008) could comfortably handle 24/day (q1h). The domain (toebeans is for owner-administered medication, not clinical care) caps it at 6. The enforced bound is the stricter (6). If a future feature targets clinical-care (post-op, ICU pets in foster homes), this cap may need a re-evaluation — but that is a new ADR, not an in-place edit.

These caps are **defense in depth.** A correctly-built UI never reaches them. They exist to prevent a buggy or malicious caller from allocating gigabytes.

### Performance budgets (soft, asserted via benchmarks)

| Surface | Budget | Where measured |
|---|---|---|
| App cold-start (Activity onCreate → first frame) | < 2,000 ms on Nokia C12 class | Macrobenchmark module (milestone 1) |
| Reminder-list scroll | 60 fps median, ≤ 5% frames > 32ms | Macrobenchmark |
| `computeScheduledDoses` for 72h × 4 doses/day × 2 phases | < 50 ms on Helio G37 | JVM microbenchmark |
| Backup export of 1,000-event archive | < 3,000 ms (originally PBKDF2-dominated; v1 ships plain JSON per ADR-0016, so the budget is JSON serialize + I/O only and met with ~50x headroom. Restore the original budget if/when the encrypted posture reactivates per ADR-0016 v2 triggers.) | JVM microbenchmark |
| AlarmManager re-materialization (boot) | < 500 ms for 50 active schedules | Robolectric perf test |

### Anti-patterns (do not introduce without ADR override)

- **Loading the full event history into memory** for any screen. Use paged queries (SQLDelight's `Query.AsCursor` or Paging 3).
- **Recomposing the entire reminder list on a single event update.** Use stable keys and `LazyColumn` item-level recomposition.
- **Embedding large binary assets** (images > 100 KB; fonts not subset). Cumulative APK weight target ≤ 8 MB.
- **Using Compose's `derivedStateOf` for cheap computations.** It is only worth its overhead for genuinely expensive derivations.
- **Allocation in hot paths** (per-frame, per-alarm). The alarm-firing path runs on a constrained `JobIntentService`-style budget — allocations there can OOM.

### Reference devices (CI matrix when budget allows)

Milestone 1 ships with macrobenchmarks on a single device class (emulator-config approximating Nokia C12). Milestone 1.5+ should expand to:

- Nokia C12 (Android Go, 2 GB) — minimum viable user
- Moto G Play 2023 (3 GB) — modal budget Android
- Samsung Galaxy A14 (4 GB) — typical budget Android
- Pixel 7a (8 GB) — developer reference (NOT the design target)

## Consequences

### Positive

- Every future ADR can refer to "the ADR-0008 target" instead of relitigating "what device do we support?"
- Junior contributors get a hard answer to "is this fast enough?" — run the benchmark on the target.
- Forces the codebase to be cheap on memory and CPU, which compounds into longer battery life and smaller APK.

### Negative

- Some "nice" features (heavy animations, ML-on-device, large embedded reference databases) are off the table or require explicit ADR override.
- CI matrix expansion is a recurring cost (~2 min added per push at milestone 1.5+).

### Rejected alternatives

- **Target the Pixel 8 / iPhone 15 class.** Rejected: misaligned with audience; would mask real performance regressions.
- **No explicit target; "performance is everyone's job."** Rejected: in practice, no one's job. Performance regressions ship.

## Verification

- Macrobenchmark module added in milestone 1 (`androidApp/macrobenchmark/`).
- `gradle.properties` declares `toebeans.perfTarget=nokia-c12-class` as documentation.
- ADR-0004 § STRIDE-DoS row references this ADR for the mechanical bound source.
- Milestone 2 may add a "performance regression" CI gate that fails if any benchmark exceeds its budget by > 20%.
