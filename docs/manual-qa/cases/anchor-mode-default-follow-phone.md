# Existing installs keep FOLLOW_PHONE

**Source:** PR #98 (`88124daf4556`), manual verification checklist. The other two checks in that list (migration review, and `toDomain` fallback) do not state an observable result, so they are not separate cases.

## Steps

1. Confirm the `anchor_mode` default on the schedule migration.

## Expected result

The default is `FOLLOW_PHONE`, and that default is backward-compatible with existing app installs.
