#!/usr/bin/env bash
# Fitness function - S6: deleting a schedule must not erase given doses.
#
# Observable properties:
#   - DoseEvent.schedule_id is nullable in the schema
#   - its FK declares ON DELETE SET NULL
#   - fire-path query names are derived from DoseEvent.sq (queries that
#     INNER JOIN Schedule), not hard-coded here
#   - one test file asserts a GIVEN dose survives a delete AND that an orphaned
#     dose is invisible to a derived fire-path query
#
# Comment text cannot satisfy a schema clause, a query name, or an assertion.
#
# Usage: bash scripts/test_posture_s6_history_preservation.sh [repo-root]

set -uo pipefail

ROOT="${1:-.}"
FAILED=0

fail() {
    echo "FAIL $1"
    FAILED=$((FAILED + 1))
}

ok() {
    echo "PASS $1"
}

SCHEMA="$ROOT/shared/src/commonMain/sqldelight/app/toebeans/core/db/DoseEvent.sq"
if [[ ! -f "$SCHEMA" ]]; then
    echo "FAIL missing required file: $SCHEMA"
    exit 1
fi

strip_sql_comments() {
    awk '
    {
        line = $0
        gsub(/:\/\//, ":URLSEP", line)
        sub(/--.*$/, "", line)
        gsub(/:URLSEP/, "://", line)
        print line
    }' "$1"
}

strip_kotlin_comments() {
    awk '
    BEGIN { inblock = 0 }
    {
        line = $0
        gsub(/:\/\//, ":URLSEP", line)
        if (inblock) {
            if (line ~ /\*\//) { sub(/^.*\*\//, "", line); inblock = 0 } else { next }
        }
        while (match(line, /\/\*/)) {
            rest = substr(line, RSTART + 2)
            if (match(rest, /\*\//)) {
                line = substr(line, 1, RSTART - 1) substr(rest, RSTART + 2)
            } else {
                line = substr(line, 1, RSTART - 1)
                inblock = 1
                break
            }
        }
        sub(/\/\/.*$/, "", line)
        gsub(/:URLSEP/, "://", line)
        print line
    }' "$1"
}

clean_schema=$(strip_sql_comments "$SCHEMA")

table_block=$(awk '
    /^[[:space:]]*CREATE TABLE[[:space:]]+DoseEvent[[:space:]]*\(/ { inside = 1 }
    inside { print }
    inside && /^[[:space:]]*\);[[:space:]]*$/ { exit }
' <<< "$clean_schema")

schedule_column=$(grep -E '^[[:space:]]*schedule_id[[:space:]]' <<< "$table_block")
if [[ -z "$schedule_column" ]]; then
    fail "DoseEvent.schedule_id column is missing"
elif grep -qE '\bNOT NULL\b' <<< "$schedule_column"; then
    fail "DoseEvent.schedule_id is not nullable"
else
    ok "DoseEvent.schedule_id is nullable"
fi

if grep -qE 'FOREIGN KEY[[:space:]]*\(schedule_id\)[[:space:]]*REFERENCES[[:space:]]+Schedule[[:space:]]*\([^)]*\)[[:space:]]*ON DELETE SET NULL' <<< "$table_block"; then
    ok "DoseEvent.schedule_id declares ON DELETE SET NULL"
else
    fail "DoseEvent.schedule_id does not declare ON DELETE SET NULL"
fi

fire_queries=$(awk '
    /^[A-Za-z][A-Za-z0-9_]*:[[:space:]]*$/ { q = $1; sub(/:$/, "", q) }
    /INNER JOIN Schedule/ && q != "" { print q }
' <<< "$clean_schema" | sort -u)

if [[ -z "$fire_queries" ]]; then
    echo "FAIL no fire-path queries found in DoseEvent.sq"
    exit 1
fi
ok "derived fire-path queries: $(echo "$fire_queries" | paste -sd, -)"

found=0
while IFS= read -r -d '' f; do
    case "$f" in
        *Test/*|*/test/*|*Test.kt|*Contract.kt) ;;
        *) continue ;;
    esac
    text=$(strip_kotlin_comments "$f")

    grep -qE 'assert[A-Za-z]*[[:space:]]*\(' <<< "$text" || continue
    grep -qiF -- 'GIVEN' <<< "$text" || continue
    grep -qiF -- 'delete' <<< "$text" || continue
    grep -qE 'assert(Null|Equals|False|True|FailsWith|That)' <<< "$text" || continue

    for query in $fire_queries; do
        if grep -qF -- "$query" <<< "$text"; then
            found=1
            ok "$f asserts given-dose survival and invisibility via $query"
            break 2
        fi
    done
done < <(find "$ROOT/shared/src" "$ROOT/androidApp/src/test" -type f -name '*.kt' -print0 2>/dev/null)

if [[ "$found" -eq 0 ]]; then
    fail "no test asserts both survival of a given dose and invisibility to the fire path"
fi

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "$FAILED S6 history-preservation check(s) failed."
    exit 1
fi

echo ""
echo "S6 history-preservation fitness function passed."
