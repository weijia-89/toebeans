# ADR-0004: Tapering schedule model — Schedule + ordered SchedulePhase rows

Date: 2026-05-14
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

The MVP must support both fixed-interval ("10mg twice daily until I stop it") and tapering ("10mg BID for 5 days, then 5mg BID for 5 days") regimens. These are the two most common patterns in veterinary medicine for non-PRN drugs (per literature in the feasibility dossier §7).

Three model options were considered:

1. **One row per dose, pre-generated.** Simple to query, but a 2× daily medication over 90 days creates 180 rows; a taper that the user edits triggers cascading rewrites. Rejected.
2. **Single `Schedule` with a JSON blob describing the program.** Loses query ergonomics; SQLite cannot index inside JSON without virtual columns.
3. **`Schedule` row + ordered `SchedulePhase` rows.** Each phase has duration, doses-per-day, dose-times, and an optional dose-amount override.

The phase-row model maps naturally to the typical taper structure ("phase 1 dose, phase 1 duration, phase 2 dose, phase 2 duration, …") and lets us materialize `DoseEvent`s lazily at a 72-hour horizon.

## Decision

`Schedule` is one row with `start_date`, optional `end_date`, and a foreign key to `Medication`. `SchedulePhase` rows hang off `Schedule` by `(schedule_id, phase_order)`, with `UNIQUE(schedule_id, phase_order)` enforced at the DB level.

A non-tapering regimen has exactly one phase. A taper has two or more. Adding PRN or load-then-maintain semantics (deferred to slice 2+) will extend either `SchedulePhase` with a `kind` column or introduce a sibling table — both are additive migrations.

`DoseEvent`s are materialized lazily by `ScheduleCalculator.computeScheduledTimes` at the 72-hour rescheduling horizon, not pre-generated at schedule creation time.

The interface and the test-as-spec for `ScheduleCalculator` are in [`shared/src/commonMain/kotlin/app/toebeans/core/scheduler/ScheduleCalculator.kt`](../../shared/src/commonMain/kotlin/app/toebeans/core/scheduler/ScheduleCalculator.kt) and [`shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt`](../../shared/src/commonTest/kotlin/app/toebeans/core/scheduler/SchedulePhaseRulesTest.kt). The latter is required to fail at v0.1 (no implementation yet) and is a vibe-dangerous gate per AGENTS.md.

## Consequences

### Positive

- Editing a taper means editing 1–N small rows, not 180 rows.
- The scheduler is a pure function — easy to test (the `scheduler purity` fitness function enforces this).
- Lazy materialization keeps the `DoseEvent` table small (≤ `MAX_PENDING_PER_PET × pets × 72h_doses`).

### Negative

- A bug in the `ScheduleCalculator` is a medication-critical bug. Mitigation: vibe-dangerous classification, mandatory test-as-spec, mutation testing on this module specifically.
- Lazy materialization makes "show me my next 30 days" queries either expensive (recompute) or stale (rely on what's in the DB). Mitigation: the UI never needs >72 hours of lookahead at v1; the History view shows only resolved/missed events.

### Rejected alternatives

- One row per dose (option 1). Rejected for edit-cascade cost.
- JSON blob per schedule (option 2). Rejected for indexing and migration friction.

## Verification

- `SchedulePhaseRulesTest` defines the four ground-truth cases: single phase BID; two-phase taper concatenation; window-narrower-than-schedule; endDate-cap.
- DST handling is deferred to a separate test class with its own ADR (slice 1.5).
- Mutation score on `shared/.../scheduler/` must be ≥80% after implementation; fitness function blocks merge below that.
