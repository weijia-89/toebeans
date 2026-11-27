#!/usr/bin/env bash
# Fixture self-test for scripts/test_posture_m2_failing_test_first.sh.
#
# Fixtures are copies of the real hook and its CI mirror with one enforced
# property removed each time: calibration requirement, scheduler pattern, or
# mirror sync. The unmodified pair is the clean fixture.

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_posture_m2_failing_test_first.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

HOOK_SOURCE="$REPO_ROOT/scripts/git-hooks/pre-commit"
MIRROR_SOURCE="$REPO_ROOT/scripts/ci-vibe-dangerous-check.sh"
for required in "$HOOK_SOURCE" "$MIRROR_SOURCE"; do
    if [[ ! -f "$required" ]]; then
        echo "FAIL fixture source not found: $required"
        exit 1
    fi
done

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

seed_fixture() {
    local root="$1"
    mkdir -p "$root/scripts/git-hooks"
    cp "$HOOK_SOURCE" "$root/scripts/git-hooks/pre-commit"
    cp "$MIRROR_SOURCE" "$root/scripts/ci-vibe-dangerous-check.sh"
}

GOOD="$tmpdir/good"
NO_CALIBRATION="$tmpdir/no-calibration"
NO_SCHEDULER="$tmpdir/no-scheduler"
MIRROR_DRIFT="$tmpdir/mirror-drift"
for fixture in "$GOOD" "$NO_CALIBRATION" "$NO_SCHEDULER" "$MIRROR_DRIFT"; do
    seed_fixture "$fixture"
done

sed -i '' '/calibration\.jsonl/d' "$NO_CALIBRATION/scripts/git-hooks/pre-commit"
sed -i '' '/core\/scheduler\//d' "$NO_SCHEDULER/scripts/git-hooks/pre-commit"
sed -i '' '/AndroidManifest/d' "$MIRROR_DRIFT/scripts/ci-vibe-dangerous-check.sh"

expect_fail() {
    local label="$1" root="$2" needle="$3"
    local report rc
    report=$(bash "$SCRIPT" "$root" 2>&1) && rc=0 || rc=$?
    if [[ "$rc" -eq 0 ]]; then
        echo "FAIL $label: checker PASSED on a defective fixture"
        echo "$report"
        FAILED=$((FAILED + 1))
    fi
    if ! grep -qF "$needle" <<< "$report"; then
        echo "FAIL $label: report missing named message: $needle"
        echo "$report"
        FAILED=$((FAILED + 1))
    fi
}

good_report=$(bash "$SCRIPT" "$GOOD" 2>&1) && good_rc=0 || good_rc=$?
if [[ "$good_rc" -ne 0 ]]; then
    echo "FAIL good fixture: checker FAILED on the unmodified hook pair"
    echo "$good_report"
    FAILED=$((FAILED + 1))
fi

expect_fail "no-calibration" "$NO_CALIBRATION" "no longer requires a calibration entry"
expect_fail "no-scheduler" "$NO_SCHEDULER" "pattern list no longer covers"
expect_fail "mirror-drift" "$MIRROR_DRIFT" "drifted"

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_posture_m2_failing_test_first: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_posture_m2_failing_test_first: all fixtures behaved as documented"
