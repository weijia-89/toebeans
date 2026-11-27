#!/usr/bin/env bash
# Fitness function - S5: every declarative ON DELETE clause must have a test
# assertion that names the table and at least one other FK identifier (the
# column, the action, or the referenced table).
#
# Expected names are derived from shared/src/commonMain/sqldelight/**/*.sq.
# No hard-coded table wish list. Comment text in either the schema or a test
# cannot satisfy a clause or an assertion.
#
# Usage: bash scripts/test_posture_s5_db_guarantees.sh [repo-root]

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

SQ_ROOT="$ROOT/shared/src/commonMain/sqldelight"
if [[ ! -d "$SQ_ROOT" ]]; then
    echo "FAIL missing required directory: $SQ_ROOT"
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

extract_fks() {
    awk '
    /^[[:space:]]*CREATE TABLE[[:space:]]+/ {
        tbl = $3
        gsub(/[^A-Za-z0-9_]/, "", tbl)
    }
    /^[[:space:]]*FOREIGN KEY[[:space:]]*\(/ {
        col = ""; ref = ""; act = ""
        if (match($0, /FOREIGN KEY \(([A-Za-z0-9_]+)\)/)) {
            col = substr($0, RSTART + 13, RLENGTH - 14)
        }
        if (match($0, /REFERENCES ([A-Za-z0-9_]+)/)) {
            ref = substr($0, RSTART + 11, RLENGTH - 11)
        }
        if (match($0, /ON DELETE (CASCADE|SET NULL|RESTRICT)/)) {
            act = substr($0, RSTART + 10, RLENGTH - 10)
        }
        gsub(/[,;]/, "", act)
        gsub(/[[:space:]]+/, " ", act)
        sub(/^[[:space:]]+/, "", act)
        sub(/[[:space:]]+$/, "", act)
        if (tbl != "" && col != "" && act != "") print tbl "|" col "|" ref "|" act
    }' "$@"
}

fks=""
while IFS= read -r -d '' sq; do
    fks+="$(strip_sql_comments "$sq" | extract_fks -)"$'\n'
done < <(find "$SQ_ROOT" -type f -name '*.sq' -print0)
fks=$(grep -v '^[[:space:]]*$' <<< "$fks" | sort -u)

if [[ -z "$fks" ]]; then
    echo "FAIL no ON DELETE clauses were found in .sq files under $SQ_ROOT"
    exit 1
fi

declare -a TEST_FILES=()
while IFS= read -r -d '' f; do
    case "$f" in
        *Test/*|*/test/*|*Test.kt|*Contract.kt) TEST_FILES+=("$f") ;;
    esac
done < <(find "$ROOT/shared/src" "$ROOT/androidApp/src/test" -type f -name '*.kt' -print0 2>/dev/null)

if [[ "${#TEST_FILES[@]}" -eq 0 ]]; then
    echo "FAIL no Kotlin test files found for FK assertion scanning"
    exit 1
fi

declare -a TEST_TEXTS=()
for f in "${TEST_FILES[@]}"; do
    TEST_TEXTS+=("$(strip_kotlin_comments "$f")")
done

fk_count=0
while IFS='|' read -r tbl col ref act; do
    [[ -z "$tbl" ]] && continue
    fk_count=$((fk_count + 1))
    found=0
    for idx in "${!TEST_TEXTS[@]}"; do
        text="${TEST_TEXTS[$idx]}"
        grep -qE "(^|[^A-Za-z0-9_])${tbl}([^A-Za-z0-9_]|$)" <<< "$text" || continue
        if ! grep -qE "(^|[^A-Za-z0-9_])${col}([^A-Za-z0-9_]|$)" <<< "$text" \
            && ! grep -qiF -- "$act" <<< "$text" \
            && ! grep -qE "(^|[^A-Za-z0-9_])${ref}([^A-Za-z0-9_]|$)" <<< "$text"; then
            continue
        fi
        grep -qE 'assert[A-Za-z]*[[:space:]]*\(' <<< "$text" || continue
        found=1
        ok "${TEST_FILES[$idx]} names FK ${tbl}.${col} (${act} to ${ref})"
        break
    done
    if [[ "$found" -eq 0 ]]; then
        fail "no test assertion names FK ${tbl}.${col} (${act} to ${ref})"
    fi
done <<< "$fks"

echo ""
echo "S5 scanned $fk_count declarative ON DELETE clause(s)."
if [[ "$FAILED" -ne 0 ]]; then
    echo "$FAILED S5 database-guarantee check(s) failed."
    exit 1
fi

echo "S5 database-guarantee fitness function passed."
