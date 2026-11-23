#!/usr/bin/env bash
# Fixture self-test for scripts/test_posture_u2_toolchain_adr.sh.
#
# Fixtures:
#   good       - catalog majors match the checked-in toolchain record; ADR has revisit conditions
#   bad-major  - agp bumped 9 -> 10 in the catalog without a record update
#   bad-adr    - ADR revisit section removed and replaced by a comment-only fake
#   bad-token  - revisit section kept but the tool token is gone

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_posture_u2_toolchain_adr.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

seed_fixture() {
    local root="$1"
    mkdir -p "$root/gradle" "$root/docs/adr"
    cat > "$root/gradle/libs.versions.toml" <<'EOF'
[versions]
agp = "9.5.1"
kotlin = "2.4.21"
sqldelight = "2.4.1"
EOF
    cat > "$root/docs/adr/0015-toolchain-compat.md" <<'EOF'
# ADR-0015: toolchain compatibility

## Revisit conditions

1. AGP advances to 9.x or beyond; re-evaluate the plugin contract.
2. KGP advances to a version where the tested matrix catches up.
EOF
}

GOOD="$tmpdir/good"
BAD_MAJOR="$tmpdir/bad-major"
BAD_ADR="$tmpdir/bad-adr"
BAD_TOKEN="$tmpdir/bad-token"
for fixture in "$GOOD" "$BAD_MAJOR" "$BAD_ADR" "$BAD_TOKEN"; do
    seed_fixture "$fixture"
done

sed -i '' 's/^agp = "9\.5\.1"/agp = "10.0.0"/' "$BAD_MAJOR/gradle/libs.versions.toml"

cat > "$BAD_ADR/docs/adr/0015-toolchain-compat.md" <<'EOF'
# ADR-0015: toolchain compatibility

<!-- ## Revisit conditions AGP KGP -->
The toolchain is fine forever.
EOF

sed -i '' 's/AGP advances to 9\.x or beyond; re-evaluate the plugin contract\./A new release arrives./' "$BAD_TOKEN/docs/adr/0015-toolchain-compat.md"

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
    echo "FAIL good fixture: checker FAILED on an aligned toolchain record"
    echo "$good_report"
    FAILED=$((FAILED + 1))
fi

expect_fail "bad-major" "$BAD_MAJOR" "silent major version bump"
expect_fail "bad-adr" "$BAD_ADR" "no revisit conditions section"
expect_fail "bad-token" "$BAD_TOKEN" "does not discuss"

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_posture_u2_toolchain_adr: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_posture_u2_toolchain_adr: all fixtures behaved as documented"
