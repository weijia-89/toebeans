# Edit a schedule without losing dose history

**Source:** stacked wave 2026-11-10, slice S2 (`sprint/schedule-edit-2026-11-10`), Schedule edit A1.

## Steps

1. Boot a fresh install (`bash scripts/manual_qa_boot.sh fresh`) and load demo data on first launch.
2. Open the Reminders tab and tap a schedule row to open Schedule Detail.
3. Tap the top-bar Edit action. The form opens with the schedule's dates and dose times prefilled.
4. Change one dose time (for example 8pm to a time 2 to 5 minutes from now), then tap Save schedule.
5. Return to Schedule Detail and check the phase row shows the new time.
6. Wait for the rescheduled reminder, or check the Reminders list still shows the schedule.

## Expected result

The form title reads Edit schedule. Save returns to the detail screen without a calculator error banner, and the detail screen shows the edited dose time. Previously logged doses (Given or Missed) still appear in the History tab for that medication. Pending reminders for the old dose time no longer fire; a reminder is armed for the new time.
