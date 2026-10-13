# Logged dose persists after restart

**Source:** commit `0d86fec2455d` (`docs/beta-smoke-add-medication.md`, section 5). `docs/manual-qa.md` points at this path.

## Steps

1. On Today, tap Log dose on the due row, or on pet detail tap Log dose now on the medication row.
2. Restart the app and look at the same row.

## Expected result

The row moves to Given with a check mark, or appears under Logged today. The dose is logged without a crash, and that state persists after restart.
