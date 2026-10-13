# Night-dose affirmation

**Source:** commit `074f11414102` (`docs/issues/v0.1-followups.md`, item 1). The beta-smoke schedule step in `0d86fec2455d` says to watch this banner and tap the affirmation if it is shown. The follow-up names a manual smoke test as the acceptance check for the affirmation copy.

## Steps

1. On the phase-create screen, enter a dose time in the range from 00:00 up to but not including 06:00.

## Expected result

A non-blocking warning: "This dose fires between midnight and 6am. Confirm you want to be woken up." The screen offers an explicit "Yes, that's intentional" affirmation.
