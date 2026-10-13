# Schedule survives force-stop

**Source:** commit `0d86fec2455d` (`docs/beta-smoke-add-medication.md`, section 4).

## Steps

1. Open the Reminders tab and find the row for pet, medication, and schedule summary.
2. Tap the row.
3. Open the Today tab and look for the due row for the scheduled slot.
4. Force-stop the app and reopen it. Check the Reminders row again.

## Expected result

- Schedule detail shows phases, dose times, and the date range.
- Today shows a due row for the scheduled slot (calculator projection; it may show as pending before fire time).
- The schedule survives process kill: after force-stop and reopen, the Reminders row is still present.
