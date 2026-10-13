# Travel records which clock the alarm used

**Source:** commit `037337b4484d` (`docs/soak-test-protocol.md`, travelling, daylight saving, time zone change).

## Steps

1. If you travel across time zones during the soak, record the transition date and direction.
2. Record which clock the alarms used after the change.

## Expected result

The record says whether the app fired alarms at the old local time, the new local time, or stopped firing. The protocol states that v0.1 stores schedules in local time and fires at local times, and that timezone-aware behavior is later roadmap work.
