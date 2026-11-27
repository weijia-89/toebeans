#!/usr/bin/env bash
# Fixture self-test for scripts/test_posture_u6_additive_migration.sh.
#
# Fixtures:
#   good        - additive migrations, a recorded destructive migration, comment-only DROP
#   unrecorded  - a new destructive migration with no decision reference
#   stale-doc   - decision document no longer documents migration 4
#   stale-entry - decision record names a migration file that does not exist

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_posture_u6_additive_migration.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

MIG_DIR="shared/src/commonMain/sqldelight/app/toebeans/core/db"

seed_fixture() {
    local root="$1"
    mkdir -p "$root/$MIG_DIR" "$root/docs"
    cat > "$root/$MIG_DIR/4.sqm" <<'EOF'
-- Migration 4 to 5: rebuild to preserve history.
CREATE TABLE DoseEvent_new (
    id TEXT NOT NULL PRIMARY KEY,
    schedule_id TEXT,
    FOREIGN KEY (schedule_id) REFERENCES Schedule(id) ON DELETE SET NULL
);
INSERT INTO DoseEvent_new (id, schedule_id) SELECT id, schedule_id FROM DoseEvent;
DROP TABLE DoseEvent;
ALTER TABLE DoseEvent_new RENAME TO DoseEvent;
EOF
    cat > "$root/$MIG_DIR/6.sqm" <<'EOF'
-- Migration 6 to 7: additive only.
ALTER TABLE Pet ADD COLUMN home_timezone TEXT;
-- DROP TABLE Pet;
EOF
    cat > "$root/docs/ROADMAP.md" <<'EOF'
# Roadmap

| Topic | Status |
| --- | --- |
| Migration 4 keeps dose history. Operator decision recorded here. | shipped |
EOF
}

GOOD="$tmpdir/good"
UNRECORDED="$tmpdir/unrecorded"
STALE_DOC="$tmpdir/stale-doc"
STALE_ENTRY="$tmpdir/stale-entry"
for fixture in "$GOOD" "$UNRECORDED" "$STALE_DOC" "$STALE_ENTRY"; do
    seed_fixture "$fixture"
done

cat > "$UNRECORDED/$MIG_DIR/7.sqm" <<'EOF'
-- Migration 7 to 8: rewrite without a decision reference.
DROP TABLE Pet;
EOF

sed -i '' '/Migration 4 keeps dose history/d' "$STALE_DOC/docs/ROADMAP.md"

# The stale-entry fixture removes migration 4 and adds an unrecorded replacement,
# which makes the checked-in record point at a missing file.
rm "$STALE_ENTRY/$MIG_DIR/4.sqm"
cat > "$STALE_ENTRY/$MIG_DIR/6.sqm" <<'EOF'
ALTER TABLE Pet ADD COLUMN home_timezone TEXT;
EOF

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
    echo "FAIL good fixture: checker FAILED on a recorded destructive migration"
    echo "$good_report"
    FAILED=$((FAILED + 1))
fi

expect_fail "unrecorded" "$UNRECORDED" "has no decision reference"
expect_fail "stale-doc" "$STALE_DOC" "does not document"
expect_fail "stale-entry" "$STALE_ENTRY" "entry names a missing migration"

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_posture_u6_additive_migration: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_posture_u6_additive_migration: all fixtures behaved as documented"
