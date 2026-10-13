# Soak dose fires within five minutes

**Source:** commit `037337b4484d` (`docs/soak-test-protocol.md`, successful soak and the daily-log late definition).

## Steps

1. Install from the internal-testing link.
2. Add at least one real pet, one real medication, and a real schedule whose first dose is in the future.
3. Use the app each day and record whether each scheduled notification surfaced.
4. Keep the phone out of explicit Do Not Disturb for the on-time check.

## Expected result

- On time means the notification surfaced within 5 minutes of the scheduled time.
- A successful soak means every scheduled dose either fired on time or is accounted for (intentional skip, deliberate medication change, or similar).
- A dose that surfaces more than 5 minutes late, on a phone not in Do Not Disturb, counts as fired late.
- A silent miss (no notification and no record, noticed only by checking the app) is a soak failure even once in 30 days.
