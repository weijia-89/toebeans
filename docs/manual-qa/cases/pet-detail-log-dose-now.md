# Pet detail Log dose now

**Source:** commit `6335facd4b13` (verified on an emulator).

## Steps

1. Open Luna's pet detail.
2. Tap Log dose now on the Methimazole row.
3. Tap the medication row (not the button).

## Expected result

- The Methimazole row is visible with a Log dose now button, gated on the seeded schedule.
- After the tap, a "Last dose: just now" subtitle appears without an app restart.
- The button remains visible for the next dose.
- Tapping the medication row still routes to the schedule editor.
