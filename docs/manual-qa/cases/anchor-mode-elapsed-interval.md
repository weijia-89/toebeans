# Time-sensitive anchor mode saves

**Source:** PR #99 (`a58ae35a537e`), scenario B.

## Steps

1. Create a new schedule.
2. In Scheduling mode, select Time-sensitive: keep interval constant.
3. Save.
4. Check the database or schedule detail.

## Expected result

Scheduling mode shows two radio buttons. After save, `anchorMode` is `ELAPSED_INTERVAL`.
