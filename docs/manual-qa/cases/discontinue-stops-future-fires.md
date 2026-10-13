# Discontinue stops future fires

**Source:** PR #89 (`d1e6700c4c26`), scenario A.

## Steps

1. Add pet Luna, medication Methimazole, and a schedule with a dose in the next few hours.
2. Discontinue the medication from Medication edit.
3. Wait through the fire time, or note whether an already scheduled alarm is cancelled.
4. Reboot the emulator (`adb reboot`) and wait for boot.

## Expected result

- The dose is still visible in history.
- No new notification at fire time, or an already scheduled alarm is silently cancelled.
- Boot rehydration does not re-schedule alarms for the discontinued medication.
