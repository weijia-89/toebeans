# Add a medication

**Source:** commit `0d86fec2455d` (`docs/beta-smoke-add-medication.md`, section 2). `docs/manual-qa.md` points at this path.

## Steps

1. On pet detail, tap Add medication (empty-state button, or the FAB when the list is not empty).
2. Fill Name and Dose amount (both required). The doc example is Methimazole and 2.5 mg.
3. Tap the bottom Save.

## Expected result

The app returns to pet detail. The medication row is visible with name and dose amount. Saving does not auto-open schedule creation.
