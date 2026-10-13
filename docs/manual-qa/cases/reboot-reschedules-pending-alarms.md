# Reboot reschedules pending alarms

**Source:** commit `0d86fec2455d`, fail line updated in `234fd8b7f9e0` (`docs/beta-smoke-add-medication.md`, reboot spot-check).

## Steps

1. With a pending dose row in the database, reboot the device.
2. After boot, check whether alarms for that pending row were scheduled again.

## Expected result

BootReceiver re-schedules alarms within the 72 hour horizon. If reboot does not rehydrate, record a smoke fail on the alarm path.
