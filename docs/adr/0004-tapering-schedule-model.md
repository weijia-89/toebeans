# ADR-0004: Tapering schedule model — Schedule + ordered SchedulePhase rows

Date: 2026-05-14
Status: Accepted (with 2026-05-15 test-as-spec review amendment — see § Test-as-spec review)
Deciders: Wei Jia (with Cascade)

> **Note for future readers:** this ADR was amended on 2026-05-15 after human review of the test-as-spec. The amendments are in § Test-as-spec review and § STRIDE threat model. The original 2026-05-14 decision (model shape) remains. If you are reading this to understand current behavior, read all sections in order.

## Context

The MVP must support both fixed-interval ("10mg twice daily until I stop it") and tapering ("10mg BID for 5 days, then 5mg BID for 5 days") regimens. These are the two most common patterns in veterinary medicine for non-PRN drugs (per literature in the feasibility dossier §7).

Three model options were considered:

1. **One row per dose, pre-generated.** Simple to query, but a 2× daily medication over 90 days creates 180 rows; a taper that the user edits triggers cascading rewrites. Rejected.
2. **Single `Schedule` with a JSON blob describing the program.** Loses query ergonomics; SQLite cannot index inside JSON without virtual columns.
3. **`Schedule` row + ordered `SchedulePhase` rows.** Each phase has duration, doses-per-day, dose-times, and an optional dose-amount override.

The phase-row model maps naturally to the typical taper structure ("phase 1 dose, phase 1 duration, phase 2 dose, phase 2 duration, …") and lets us materialize `DoseEvent`s lazily at a 72-hour horizon.

## Decision

`Schedule` is one row with `start_date`, optional `end_date`, and a foreign key to `Medication`. `SchedulePhase` rows hang off `Schedule` by `(schedule_id, phase_order)`, with `UNIQUE(schedule_id, phase_order)` enforced at the DB level.

A non-tapering regimen has exactly one phase. A taper has two or more. Adding PRN or load-then-maintain semantics (deferred to milestone 2+) will extend either `SchedulePhase` with a `kind` column or introduce a sibling table — both are additive migrations.

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

- `SchedulePhaseRulesTest` defines the ground-truth cases (see "Test-as-spec review" below for the full v0.1 list).
- DST handling is deferred to a separate test class with its own ADR — see `0007-timezone-and-travel-mode.md`.
- Mutation score on `shared/.../scheduler/` must be ≥80% after implementation; fitness function blocks merge below that.

## Test-as-spec review (2026-05-15)

Human review of `SchedulePhaseRulesTest` per AGENTS.md § Test-as-spec rules locked the following decisions. These are now part of the contract:

| ID | Decision | Test |
|---|---|---|
| **D1** | `Schedule.endDate` is **INCLUSIVE.** "Give through Friday" means the pet gets doses on Friday. Asymmetric with `toExclusive` window param (which is mechanical). | Test 4 (`endDate caps the schedule before phase exhaustion`) |
| **D2** | A phase with `doseTimesLocal[0] == 00:00` produces a dose at exactly `startDate @ 00:00 local`. No "skip to next morning" semantics. A UX-level warning when any dose time is in `[00:00, 06:00)` is tracked as a milestone-1 follow-up. | Test 5 (`phase with midnight dose time anchors first dose at startDate 0000 local`) |
| **D3** | Malformed `phaseOrder` input throws `IllegalArgumentException`. Specifically: duplicate `phaseOrder` and gaps (`[0, 2]`) are rejected. Silently producing wrong dose counts from malformed input is medication-critical. | Tests 6, 7 (`duplicate phaseOrder throws`, `phaseOrder gap throws`) |
| **D4** | Empty `phases` list is a no-op: returns empty list. Not an error. | Test 8 (`empty phases returns empty result`) |
| **D5** | Schedule whose `startDate` is on or after the window's `toExclusive` returns an empty list. The schedule simply has not started yet. | Test 9 (`schedule starting after toExclusive returns empty result`) |
| **D6** | The `timeZone` parameter is captured by the **caller** from `TimeZone.currentSystemDefault()` at materialization time. `Schedule` does NOT pin a timezone. This gives "travel mode" for free when the phone TZ changes. Per-schedule pinned TZ is a follow-up — see `0007-timezone-and-travel-mode.md`. | Implicit in all tests (caller passes the TZ explicitly) |
| **D7** | The method is named `computeScheduledDoses` (singular `Dose` in the return type, plural in the method to match `List<...>`). Renamed from `computeScheduledTimes`. | All tests |
| **F5** | Returned list is globally sorted by `scheduledAt` across phase boundaries, not just within a phase. | Test 2 (`two-phase taper concatenates phases without overlap or gap`) — sort assertion added |

## STRIDE threat model (scheduler surface)

Per AGENTS.md confidence-score component 9.

| STRIDE | Threat | Mitigation |
|---|---|---|
| **S**poofing | N/A — calculator has no identity surface | — |
| **T**ampering | Wrong dose count from malformed `phases` input (e.g., duplicate `phaseOrder`) silently producing a too-long schedule | D3: `IllegalArgumentException` on duplicates and gaps; fail-loud rather than fail-quiet |
| **R**epudiation | Caller can claim "the calculator told me to give 11 doses" | Calculator is deterministic and pure; given the same `(schedule, phases, tz, window)` it returns the same list. Test-as-spec pins behavior. Calibration log `.codeit/calibration.jsonl` records every change to this surface. |
| **I**nformation disclosure | N/A — no secrets in dose data | — |
| **D**enial of service | A 30-year-long phase × 24-doses-per-day query could allocate ~262K events | Mitigated by the 72-hour materialization horizon; calculator is never called with a window > 168h in milestone 1. Will be re-checked when milestone 2 adds "show next 30 days." |
| **E**levation of privilege | N/A — calculator has no auth boundary | — |
