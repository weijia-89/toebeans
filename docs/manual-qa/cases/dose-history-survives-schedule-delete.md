# Dose history survives schedule delete

**Source:** operator decision 2026-11-22, dose history migration 4.

## Steps

1. On a fresh install, add a pet, a medication, and a schedule with a dose a few minutes out.
2. Log one dose as Given and leave a later dose Pending.
3. Delete the schedule.
4. Open the History tab and the Today worklist.
5. Wait for the pending dose's original fire time and watch the notification shade.
6. Tap Edit on a surviving dose row.

## Expected result

- Both dose rows are still in History with their medication name and scheduled time.
- The pending row is not under Logged today.
- No notification fires for the deleted schedule.
- Edit opens the medication form, not a schedule detail screen for a deleted schedule.
