# ADR-0007: Timezone handling and travel mode

Date: 2026-05-15
Status: **PROPOSED — STUB**
Deciders: TBD (Wei Jia + reviewer)

## Status note

This is a placeholder ADR. The design is **not yet finalized** — this file exists to:

1. Record the v0.1 decision (system-TZ-at-materialization, no per-schedule TZ).
2. Capture the open questions that must be resolved before slice 1 ships any TZ-related UI.

**Do not implement against this ADR.** It is a problem statement, not yet a solution.

## v0.1 decision (locked, recorded in ADR-0004 test review)

- `Schedule` does NOT carry a `timeZone` field at the database level.
- `ScheduleCalculator.computeScheduledDoses` takes `timeZone: TimeZone` as a parameter.
- The caller — `ToebeansApp` at materialization time — passes `TimeZone.currentSystemDefault()`.
- **Consequence:** when the user's phone changes timezone (e.g., they land in Tokyo), the **next** 72-hour materialization runs with the new TZ. This is "travel mode for free" — desired behavior in most cases.

## Problems with the v0.1 decision

These are the cases the v0.1 approach handles **badly** and which must be designed before slice 1 ships a TZ-related UI:

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
