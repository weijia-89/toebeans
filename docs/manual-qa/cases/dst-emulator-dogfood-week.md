# One-week emulator DST dogfood

**Source:** ADR-0007 gate G2 in commit `344481b101c3`. The same ADR's verification status in commit `86ed24c65154` (2026-05-17) says the warning surfaces were not implemented yet. This file records the manual check the ADR asks for.

## Steps

1. Run a 1-week dogfood of the DST detection logic with a fake DST shift in the Android emulator.
2. Exercise a spring-forward dose and a fall-back dose.

## Expected result

- Spring-forward example from the ADR: 02:30 does not exist on 2026-03-08, so the dose shifts to the next valid wall-clock time, and the reminder list shows a non-blocking DST_SKIP warning.
- Fall-back example from the ADR: 01:30 exists twice on 2026-11-01, so the dose fires once, at the earlier UTC instant, with no double dose, and the list shows DST_DUPLICATE_RESOLVED.
- For ELAPSED_INTERVAL anchor mode, DST has no effect.
- As of the 2026-05-17 verification status, those warning surfaces were not implemented. The gate was still open.
