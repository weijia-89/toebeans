#!/usr/bin/env bash
# Fixture self-test for scripts/test_posture_s6_history_preservation.sh.
#
# Seeds a minimal DoseEvent.sq with two INNER JOIN Schedule fire-path queries.
# Clean fixture has a test asserting both given-dose survival and invisibility.
# Defects: NOT NULL + CASCADE schema, missing fire-path query in the test,
# and missing GIVEN survival assertion.

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_posture_s6_history_preservation.sh"

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
    local root="$1" column="$2" action="$3"
    mkdir -p "$root/$SQ_DIR" "$root/$TEST_DIR"
    cat > "$root/$SQ_DIR/DoseEvent.sq" <<EOF
CREATE TABLE DoseEvent (
    id TEXT NOT NULL PRIMARY KEY,
    schedule_id $column,
    medication_id TEXT NOT NULL,
    status TEXT NOT NULL,
    FOREIGN KEY (schedule_id) REFERENCES Schedule(id) $action
);
-- comment-only fake must not count as a fire-path query:
-- selectFake: SELECT * FROM DoseEvent INNER JOIN Schedule s ON s.id = de.schedule_id;
selectEnrichedReminderById:
SELECT de.* FROM DoseEvent de
  INNER JOIN Schedule s ON s.id = de.schedule_id
WHERE de.id = ?;
selectAllDoseEvents:
SELECT * FROM DoseEvent;
EOF
}

seed_test() {
    local root="$1" body="$2"
    cat > "$root/$TEST_DIR/HistoryTest.kt" <<EOF
package app.toebeans.core.data

class HistoryTest {
    fun check() {
        $body
    }
}
EOF
}

GOOD="$tmpdir/good"
BAD_SCHEMA="$tmpdir/bad-schema"
NO_FIRE_QUERY="$tmpdir/no-fire-query"
NO_GIVEN="$tmpdir/no-given"
for fixture in "$GOOD" "$BAD_SCHEMA" "$NO_FIRE_QUERY" "$NO_GIVEN"; do
    seed_schema "$fixture" "TEXT" "ON DELETE SET NULL"
done
seed_schema "$BAD_SCHEMA" "TEXT NOT NULL" "ON DELETE CASCADE"

seed_test "$GOOD" 'val x = DoseStatus.GIVEN; scheduleRepository.delete("s"); assertNotNull(x); assertNull(db.doseEventQueries.selectEnrichedReminderById("d").executeAsOneOrNull())'
seed_test "$NO_FIRE_QUERY" 'val x = DoseStatus.GIVEN; scheduleRepository.delete("s"); assertNotNull(x); assertNull(db.doseEventQueries.selectAllDoseEvents().executeAsList())'
seed_test "$NO_GIVEN" 'scheduleRepository.delete("s"); assertNull(db.doseEventQueries.selectEnrichedReminderById("d").executeAsOneOrNull())'

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
    echo "FAIL good fixture: checker FAILED on full history-preservation evidence"
    echo "$good_report"
    FAILED=$((FAILED + 1))
fi

expect_fail "bad-schema" "$BAD_SCHEMA" "is not nullable"
expect_fail "no-fire-query" "$NO_FIRE_QUERY" "no test asserts both"
expect_fail "no-given" "$NO_GIVEN" "no test asserts both"

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_posture_s6_history_preservation: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_posture_s6_history_preservation: all fixtures behaved as documented"
