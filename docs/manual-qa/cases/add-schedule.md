# Add a schedule

**Source:** commit `0d86fec2455d` (`docs/beta-smoke-add-medication.md`, section 3).

## Steps

1. On pet detail, tap the medication row to open Create schedule. This is the path for a medication that does not already have a schedule.
2. Set Start date to today, or tomorrow if the check is a future fire.
3. Leave the default single phase, or add a phase.
4. Set at least one dose time. For a quick notification check, pick a time 2 to 5 minutes from now.
5. If the night-dose banner for the range 00:00 through 06:00 is shown, tap Yes, that's intentional.
6. Tap Save schedule.

## Expected result

No calculator formError banner. The app navigates back to pet detail without a crash.
