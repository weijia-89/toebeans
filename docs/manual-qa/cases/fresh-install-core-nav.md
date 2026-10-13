# Fresh install core navigation

**Source:** commit `2953b2918848` (`scripts/manual_qa_boot.sh`, fresh scenario).

## Steps

1. Run `bash scripts/manual_qa_boot.sh fresh` so the script clears app data and launches the app.
2. If a welcome or demo dialog appears, choose the path for the test (empty or demo).
3. Open the Pets tab, open a pet, and confirm detail loads.
4. Add or open a medication, create or edit a schedule, and save.
5. Open the Reminders tab.

## Expected result

- Core navigation works on a fresh install.
- No crash on schedule save.
- The schedule appears on the Reminders tab.
