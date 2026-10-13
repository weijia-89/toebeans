# Notification at the scheduled time

**Source:** commit `0d86fec2455d` and the shipped-path update in `4f3f3852e16c` (`docs/beta-smoke-add-medication.md`, reminder procedure). Shorter form: PR #60 (`ef9d67f70085`), scenario A.

## Steps

1. Complete the manual smoke through saving a schedule, with the first dose about 3 minutes ahead (the doc says T+3 min; PR #60 says the same).
2. Background the app. Do not force-stop.
3. Wait until the scheduled time.

## Expected result

- A notification fires on channel `medication-critical`. Title or body mentions the pet and the medication.
- PR #60's expected result is that `medication-critical` notification.
- If it fires, tapping it opens the app. The Today row stays consistent.
- If it does not fire, and Today still has a pending row at the scheduled time, record a smoke fail on the alarm path with phone state (Do Not Disturb, battery saver, exact-alarm permission on Android 12+).
- After schedule save, the push should fire on a device when exact-alarm permission and battery settings allow. Emulator timing is best-effort; confirm on hardware. A silent miss is a smoke fail unless it is a documented known gap.
