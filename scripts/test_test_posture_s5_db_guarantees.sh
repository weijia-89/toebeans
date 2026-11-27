#!/usr/bin/env bash
# Fixture self-test for scripts/test_posture_s5_db_guarantees.sh.
#
# Seeds a synthetic schema with four ON DELETE clauses, including a derived
# Widget FK that exists only in the fixture. Clean fixture has an assertion
# naming every table, column and action. Defective fixtures drop one FK name,
# hide all names in comments, or add a new FK with no test.

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_posture_s5_db_guarantees.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

SQ_DIR="shared/src/commonMain/sqldelight/app/toebeans/core/db"
TEST_DIR="shared/src/jvmTest/kotlin/app/toebeans/core/data"

seed_schema() {
    local root="$1"
    mkdir -p "$root/$SQ_DIR" "$root/$TEST_DIR"
    cat > "$root/$SQ_DIR/Fixture.sq" <<'EOF'
CREATE TABLE Widget (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES Pet(id) ON DELETE RESTRICT
);
CREATE TABLE Gadget (
    id TEXT NOT NULL PRIMARY KEY,
    widget_id TEXT NOT NULL,
    FOREIGN KEY (widget_id) REFERENCES Widget(id) ON DELETE CASCADE
);
-- comment-only fake must not be read as a clause:
-- FOREIGN KEY (ghost_id) REFERENCES Pet(id) ON DELETE SET NULL
EOF
}

all_names='listOf("Widget", "owner_id", "RESTRICT", "Gadget", "widget_id", "CASCADE")'

seed_test() {
    local root="$1" body="$2"
    cat > "$root/$TEST_DIR/FixtureFkTest.kt" <<EOF
package app.toebeans.core.data

class FixtureFkTest {
    fun check() {
        val names = $body
        assertEquals(6, names.size)
    }
}
EOF
}

GOOD="$tmpdir/good"
MISSING_NAME="$tmpdir/missing-name"
COMMENT_ONLY="$tmpdir/comment-only"
NEW_FK="$tmpdir/new-fk"
for fixture in "$GOOD" "$MISSING_NAME" "$COMMENT_ONLY" "$NEW_FK"; do
    seed_schema "$fixture"
    seed_test "$fixture" "$all_names"
done

seed_test "$MISSING_NAME" 'listOf("Widget", "owner_id", "RESTRICT")'
seed_test "$COMMENT_ONLY" 'listOf() // Widget owner_id RESTRICT Gadget widget_id CASCADE'

cat >> "$NEW_FK/$SQ_DIR/Fixture.sq" <<'EOF'
CREATE TABLE Doohickey (
    id TEXT NOT NULL PRIMARY KEY,
    gadget_id TEXT NOT NULL,
    FOREIGN KEY (gadget_id) REFERENCES Gadget(id) ON DELETE SET NULL
);
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
    echo "FAIL good fixture: checker FAILED on fully named FK coverage"
    echo "$good_report"
    FAILED=$((FAILED + 1))
fi

expect_fail "missing-name" "$MISSING_NAME" "no test assertion names FK"
expect_fail "comment-only" "$COMMENT_ONLY" "no test assertion names FK"
expect_fail "new-fk" "$NEW_FK" "no test assertion names FK Doohickey.gadget_id"

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_posture_s5_db_guarantees: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_posture_s5_db_guarantees: all fixtures behaved as documented"
