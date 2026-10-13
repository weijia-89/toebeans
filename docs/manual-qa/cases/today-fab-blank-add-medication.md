# Today FAB opens a blank add-medication form

**Source:** PR #84 (`78fdf5e42dae`).

## Steps

1. Boot fresh with `bash scripts/manual_qa_boot.sh fresh --boot-avd toebeans-pixel7`.
2. Load demo data.
3. On Today, tap the FAB.
4. Tap a dose row.

## Expected result

- Add medication opens blank.
- The edit screen shows the correct medication only.
