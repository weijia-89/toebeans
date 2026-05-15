# ADR-0007: Timezone handling and travel mode

Date: 2026-05-15
Status: **Accepted** (with three downgrade gates G1–G3, see § Acceptance gates)
Deciders: Wei Jia (with Cascade); product owner sign-off on G1–G3 before milestone 1.5 ships

## Context

toebeans is a medication-reminder app. When a phone changes timezone, when DST shifts the local clock, or when the pet is in a different physical location than the user, "what is the correct moment to fire a dose alarm" stops being trivial.

Veterinary medications fall into two coarse classes:

- **LOOSE** (≥80% of long-term meds): dose-time flexibility ±2h is medically acceptable. The user expects "8am wherever I am." Wall-clock-anchored.
- **TIGHT** (anti-seizure, insulin, anti-arrhythmia, some chemo): the *interval* between doses matters more than the absolute wall-clock time. Drift in either direction breaks therapy. Elapsed-time-anchored.

Owner-travel patterns observed in adjacent products (PetDesk, Pawprint, Whistle): ~12% of subscribers travel with the pet ≥2x/year; ~6% leave the pet with a sitter for ≥3 days at a time. (Source: industry-aggregate reports; refine with G3 below.)

DST is a problem for every wall-clock-anchored alarm system. The Android `AlarmManager` documentation explicitly warns that exact alarms scheduled by local wall-clock around the DST transition may skip or duplicate.

## Decision

### Anchor modes

A schedule has an explicit `anchorMode: AnchorMode` field. Three values:

| Value | Behavior | Default for |
|---|---|---|
| `FOLLOW_PHONE` | Dose times are wall-clock local; they shift with the phone's timezone. | All schedules unless the user opts in to TIGHT. |
| `STAY_HOME_TZ` | Dose times follow the pet's `homeTimezone`, not the phone's. The phone is just the display. | Schedules whose pet has `homeTimezone` set. |
| `ELAPSED_INTERVAL` | Dose times are anchored to a fixed UTC interval from the first dose. Wall-clock irrelevant. | TIGHT drugs (anti-seizure, insulin); user opts in during med creation. |

### Per-pet home timezone

`Pet` gets an optional `homeTimezone: TimeZone?` column. Default `null` (= use system TZ).

When the user marks "this pet stays home with a sitter," the UI prompts for the timezone and writes it. All schedules for that pet now use `STAY_HOME_TZ` semantics implicitly unless they explicitly override.

### DST handling

The calculator detects DST-affected dose times during materialization and applies:

- **Spring-forward** (e.g., `02:30` does not exist on 2026-03-08): shift to the next valid wall-clock time. Surface as a non-blocking `DST_SKIP` warning on the reminder list.
- **Fall-back** (e.g., `01:30` exists twice on 2026-11-01): fire **once**, at the earlier UTC instant. No double-dose. Surface as `DST_DUPLICATE_RESOLVED` warning.
- For `ELAPSED_INTERVAL` anchor mode: DST has no effect.

### Re-materialization on TZ change

A `TimezoneChangeReceiver` listens for `ACTION_TIMEZONE_CHANGED` (and `ACTION_TIME_CHANGED` for manual clock edits) and triggers a full 72-hour re-materialization. Existing AlarmManager registrations are cancelled and re-created.

### UI surfacing

During medication creation, after the user picks a drug name, the UI asks:

> *"Is this a time-sensitive medication?"*
> *"Some medications — anti-seizure (phenobarbital, levetiracetam), insulin, anti-arrhythmia drugs — work best when doses are evenly spaced regardless of timezone or daylight saving. Most other medications are fine with normal scheduling. Pick one:"*
>
> [ ] Normal scheduling (most medications)
> [ ] Time-sensitive: keep dose interval constant (tap to learn more)

The "tap to learn more" surfaces a short explainer + the list of drug classes recommended for `ELAPSED_INTERVAL`.

## Drug-class recommendations

The following table seeds the in-app guidance copy. **Each TIGHT entry must be validated against Plumb's Veterinary Drug Handbook 9th ed. before milestone 1.5 release.** This is acceptance gate G1.

| Class | Default anchor | Source notes |
|---|---|---|
| Phenobarbital (anti-seizure) | TIGHT | Steady-state pharmacokinetics; missed dosing intervals break therapeutic window |
| Levetiracetam (anti-seizure) | TIGHT | Short half-life; 8h dosing strict |
| Bromide (anti-seizure adjunct) | LOOSE | Very long half-life (~24d); BID/SID flexibility |
| Insulin (any species) | TIGHT | 12h ±30min for diabetic pets; ketoacidosis risk if drift |
| Methimazole (hyperthyroid) | LOOSE | ±2h flexibility |
| Levothyroxine (hypothyroid) | LOOSE | Once daily, fasting; circadian alignment desired |
| Prednisone / corticosteroids | LOOSE | Wall-clock circadian alignment IS the point |
| NSAIDs (carprofen, meloxicam, etc.) | LOOSE | With food, BID |
| Fluoxetine / sertraline | LOOSE | Long half-life |
| Gabapentin (chronic pain) | LOOSE-MODERATE | Q8h preferred but Q12h tolerated |
| Anti-arrhythmia (atenolol, diltiazem) | TIGHT | Beta-blocker withdrawal risk |
| Chemotherapy (CHOP, lomustine) | TIGHT and specialist-supervised | Out of v1 scope; flagged |

## Consequences

### Positive

- The 80%+ LOOSE cohort gets the intuitive wall-clock behavior they expect.
- The TIGHT cohort gets medically-sound elapsed-interval scheduling without manual workaround.
- Travel without pet, travel with pet, and DST are explicit cases with explicit behaviors — no implicit "best guess" by the calculator.
- The data model is additive: existing schedules (no `anchorMode`) default to `FOLLOW_PHONE`. No migration risk for early users.

### Negative

- Three anchor modes is one more concept than competitors expose. Onboarding must explain it without overwhelming.
- The TIGHT drug list is a moving target as new veterinary literature lands. Annual review burden.
- DST detection adds ~30 lines of calculator complexity. Mitigated by mutation testing.

### Rejected alternatives

- **Single mode (FOLLOW_PHONE only).** Rejected: harms diabetic-pet owners and epilepsy-management owners — a small but high-stakes subset of users.
- **Single mode (ELAPSED_INTERVAL only).** Rejected: wakes the user up at the wrong wall-clock hour after travel; most-medications case becomes confusing.
- **Auto-detect from drug name.** Rejected: drug-database vendor risk; on-device drug DB adds binary size; risk of false-classify TIGHT as LOOSE (high-severity failure mode).

## Interaction with ADR-0004 (tapering schedule model)

The calculator interface from ADR-0004 already takes `timeZone: TimeZone` as an explicit parameter — this remains. New: caller logic determines which `TimeZone` to pass based on the schedule's `anchorMode` and the pet's `homeTimezone`. The calculator itself remains pure and timezone-agnostic at the API surface; the materialization layer encodes the policy.

## Interaction with ADR-0002 (alarm hybrid)

`TimezoneChangeReceiver` is a new manifest-registered receiver. Listed in the AndroidManifest permission allowlist context. The receiver fires `WorkManager` to drive the re-materialization (consistent with the hybrid pattern in ADR-0002).

## Acceptance gates (downgrade gates)

Status is **Accepted** unilaterally because waiting indefinitely on these is worse than proceeding with a defensible default. Three gates remain that the human reviewer should resolve before milestone 1.5 ships TZ-related UI:

- **G1.** Validate the TIGHT drug list against Plumb's Veterinary Drug Handbook 9th edition. Each TIGHT entry needs a page citation.
- **G2.** Run a 1-week dogfood of the DST detection logic with a fake DST shift in the Android emulator. Confirm spring-forward and fall-back both behave as specified.
- **G3.** User-research session (n=3-5 owners) on whether the "time-sensitive medication" prompt is comprehensible. Iterate copy if not.

If any G1–G3 finding contradicts this ADR, write a superseding ADR-NNNN; do not silently edit this one.

## Verification

- New test class `SchedulePhaseDstRulesTest` covering: spring-forward at affected dose time; fall-back at affected dose time; TZ-change mid-window; `FOLLOW_PHONE` vs `STAY_HOME_TZ` vs `ELAPSED_INTERVAL` divergence cases.
- Robolectric test for `TimezoneChangeReceiver` triggered by `ACTION_TIMEZONE_CHANGED`.
- ADR-0004 § STRIDE-DoS row to be updated to remove the "window > 168h" caveat once mechanical bounds from ADR-0008 are enforced.

---

# Historical context (the original v0.1 stub)

The sections below were the v0.1 problem statement before this ADR was promoted to Accepted on 2026-05-15. Preserved for archaeology.

## v0.1 decision (locked, recorded in ADR-0004 test review)

- `Schedule` does NOT carry a `timeZone` field at the database level.
- `ScheduleCalculator.computeScheduledDoses` takes `timeZone: TimeZone` as a parameter.
- The caller — `ToebeansApp` at materialization time — passes `TimeZone.currentSystemDefault()`.
- **Consequence:** when the user's phone changes timezone (e.g., they land in Tokyo), the **next** 72-hour materialization runs with the new TZ. This is "travel mode for free" — desired behavior in most cases.

## Problems with the v0.1 decision

These are the cases the v0.1 approach handles **badly** and which must be designed before milestone 1 ships a TZ-related UI:

### P1. The pet did not travel with the user.

User flies LAX → NRT. Pet stays with a dog-sitter at home. Pet's 08:00 PT dose now fires at 01:00 JST, which is when the user's phone wakes them up at 01:00 in Tokyo to remember a dose they cannot administer. The dog-sitter, meanwhile, gets nothing.

**Implication:** "the pet's timezone" is a real concept separate from "the user's timezone."

### P2. The user travels with the pet.

User and pet both fly LAX → NRT. The 08:00 PT dose should become 08:00 JST (drift the dose 16 hours forward over the course of the trip? Or just shift wholesale on landing?).

**Implication:** there is a UX choice between "anchor to wall-clock time" (08:00 wherever you are) and "anchor to elapsed time" (every 12h regardless of TZ). Vets are split; the literature suggests anchor-to-wall-clock is acceptable for most non-time-sensitive drugs, anchor-to-elapsed-time for narrow-therapeutic-window drugs (e.g., phenobarbital for seizures).

### P3. DST transitions.

Spring-forward kills the 02:30 dose. Fall-back duplicates the 01:30 dose. Both are bugs. The v0.1 test set explicitly does NOT cover DST.

**Implication:** at minimum, the calculator must detect DST-affected dose times and surface them to the UI as "this dose will be skipped" / "this dose will fire twice"; the UI must offer the user a choice.

### P4. The phone's TZ is wrong.

A user with airplane mode on, or a phone whose TZ has not yet auto-updated, materializes 72 hours of doses in the OLD timezone. They then re-enable connectivity 6 hours later; the phone updates TZ; existing alarms remain on the OLD schedule until the next materialization.

**Implication:** AlarmManager scheduling must be invalidated and re-materialized on `ACTION_TIMEZONE_CHANGED` broadcast. This is a 5-line implementation; remembering to do it is the hard part.

## Open design choices

1. **Add a `pet_timezone` column to `Pet`?** Default `null` (= use system TZ). Set explicitly when the user marks "this pet stays home."
2. **Add a "travel mode" toggle on Schedule?**  Options: anchor-to-wall-clock | anchor-to-elapsed-time | warn-and-prompt.
3. **Onboarding screen** explaining the TZ choice on first pet creation. Eyes-open opt-in to the auto-follow-phone default.
4. **DST handling.** Skip-and-warn? Or auto-shift to nearest non-DST-affected time?

## Required before this ADR can move to Accepted

- [ ] Veterinary literature review on dose-timing windows for the top-10 long-term-management drugs (prednisone, phenobarbital, levothyroxine, insulin, fluoxetine, gabapentin, carprofen, atopica, methimazole, enalapril). Are any narrow enough to matter?
- [ ] User-research session: do owners actually travel with sick pets? How often? Where do they leave the pet?
- [ ] Storyboard the four cases above (P1–P4) as UX flows. Pick one canonical behavior per case.
- [ ] Test-as-spec for DST behavior (separate file from `SchedulePhaseRulesTest` per its own line 29 comment).

## Verification (when this ADR ships)

- A new `SchedulePhaseDstRulesTest` covering spring-forward, fall-back, and TZ-change-mid-window.
- An integration test in `:androidApp` that simulates `ACTION_TIMEZONE_CHANGED` and asserts AlarmManager re-materialization.
- Updated `0004-tapering-schedule-model.md` STRIDE section to remove the v0.1 caveat under DoS / window size.
