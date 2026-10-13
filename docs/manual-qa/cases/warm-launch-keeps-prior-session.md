# Warm launch keeps prior session

**Source:** commit `2953b2918848` (`scripts/manual_qa_boot.sh`, warm scenario).

## Steps

1. Run `bash scripts/manual_qa_boot.sh warm` so the script launches without clearing app data.
2. Look at home / Today.
3. Exercise the change under test.
4. Force-stop and relaunch when the check is persistence across process death.

## Expected result

Warm state matches the expectation for the change under test. Home / Today shows the state from the prior session.
