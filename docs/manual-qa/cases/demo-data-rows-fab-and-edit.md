# Demo data rows, FAB, and dose edit

**Source:** PR #83 (`6dc6b56e7035`). The test plan has a checklist line and a device section; both are recorded here.

## Steps

1. Settings, reset app data, then first launch.
2. Tap Load demo data.
3. Look at Today, Pets, and the Reminders FAB.
4. Tap a due dose row.

## Expected result

- Today shows multiple due rows per pet.
- Pets and Today show 3 medications per pet.
- The Reminders FAB opens a blank Add medication screen for Rufus.
- The edit screen shows the correct medication immediately, with no ghost of a prior screen.
