# Silent miss stays pending on Today

**Source:** commit `037337b4484d` (`docs/soak-test-protocol.md`, a dose silently missed).

## Steps

1. After a dose had no notification, open toebeans.
2. Check Logged today and the Today worklist.
3. Note the time, the expected dose, when you noticed, and the phone state at the expected fire time if you can reconstruct it.

## Expected result

The dose should not appear in Logged today, because it was not logged. It should appear on the Today worklist as a still-pending row.
