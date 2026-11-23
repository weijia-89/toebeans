#!/usr/bin/env bash
# Fitness function - U6: migrations stay additive; destructive rewrites need a
# recorded decision reference.
#
# Scans SQLDelight .sqm files. ALTER TABLE ADD COLUMN is additive and free.
# A destructive rewrite is DROP TABLE, DROP COLUMN, a *_new table plus RENAME
# TO (the 12-step rebuild shape), or a 12-step marker in live SQL text.
#
# A destructive file may carry its own ADR-<n> reference, or appear in the
# DESTRUCTIVE_RECORD table kept next to this rule. The table points at a
# written decision document and a topic token that document must still name.
# Today migration 4's decision is written in docs/ROADMAP.md because no
# dedicated ADR exists yet; the checker reports that as a record, not as a
# substitute ADR.
#
# Comment text never satisfies a destructive or approval check.
#
# Usage: bash scripts/test_posture_u6_additive_migration.sh [repo-root]

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

MIG_DIR="$ROOT/shared/src/commonMain/sqldelight/app/toebeans/core/db"
if [[ ! -d "$MIG_DIR" ]]; then
    echo "FAIL missing required directory: $MIG_DIR"
    exit 1
fi

# migration file | written decision document | topic the document must name
DESTRUCTIVE_RECORD=(
    '4.sqm|docs/ROADMAP.md|migration 4'
)

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

is_destructive() {
    local text="$1"
    if grep -qE '(^|[^A-Za-z_])DROP[[:space:]]+TABLE([[:space:]]|$)' <<< "$text"; then return 0; fi
    if grep -qE '(^|[^A-Za-z_])DROP[[:space:]]+COLUMN([[:space:]]|$)' <<< "$text"; then return 0; fi
    if grep -qE 'CREATE[[:space:]]+TABLE[[:space:]]+[A-Za-z0-9_]+_new' <<< "$text" \
        && grep -qE 'RENAME[[:space:]]+TO' <<< "$text"; then return 0; fi
    if grep -qiE '12[-[:space:]]step' <<< "$text"; then return 0; fi
    return 1
}

RECORDED_FILES=""
RECORDED_VALUES=""
for row in "${DESTRUCTIVE_RECORD[@]}"; do
    IFS='|' read -r file doc topic <<< "$row"
    RECORDED_FILES="$RECORDED_FILES$file"$'\n'
    RECORDED_VALUES="$RECORDED_VALUES$doc|$topic"$'\n'
    if [[ ! -f "$MIG_DIR/$file" ]]; then
        fail "destructive-record entry names a missing migration: $file"
    fi
done

migration_count=0
destructive_count=0
while IFS= read -r -d '' mig; do
    migration_count=$((migration_count + 1))
    rel=$(basename "$mig")
    text=$(strip_sql_comments "$mig")

    if ! is_destructive "$text"; then
        ok "$rel is additive; no decision reference needed"
        continue
    fi
    destructive_count=$((destructive_count + 1))

    adr_ids=$(grep -oE 'ADR-[0-9]+' <<< "$text" | sed 's/ADR-//' | sort -u)
    approved=0
    for id in $adr_ids; do
        adr=$(find "$ROOT/docs/adr" -maxdepth 1 -type f -name "${id}-*.md" -print -quit 2>/dev/null)
        if [[ -n "$adr" ]]; then
            approved=1
            ok "$rel carries ADR-$id, which exists"
        else
            fail "$rel cites ADR-$id but no matching ADR file exists"
        fi
    done

    if [[ "$approved" -eq 1 ]]; then
        continue
    fi

    idx=$(grep -nFx -- "$rel" <<< "$RECORDED_FILES" | head -n 1 | cut -d: -f1)
    if [[ -z "$idx" ]]; then
        fail "destructive migration $rel has no decision reference; add an ADR reference in the .sqm file or a record entry"
        continue
    fi
    entry=$(sed -n "${idx}p" <<< "$RECORDED_VALUES")
    IFS='|' read -r doc topic <<< "$entry"
    doc_path="$ROOT/$doc"
    if [[ ! -f "$doc_path" ]]; then
        fail "decision record $doc is missing for $rel"
        continue
    fi
    doc_text=$(awk '!/^[[:space:]]*<!--/ { print }' "$doc_path")
    if ! grep -qiF -- "$topic" <<< "$doc_text"; then
        fail "decision record $doc does not document $rel (expected topic: $topic)"
        continue
    fi
    if ! grep -qiE 'decision|operator' <<< "$doc_text"; then
        fail "decision record $doc does not record a human decision for $rel"
        continue
    fi
    ok "$rel is backed by written decision $doc"
done < <(find "$MIG_DIR" -maxdepth 1 -type f -name '*.sqm' -print0)

if [[ "$migration_count" -eq 0 ]]; then
    fail "no SQLDelight .sqm migration files found under $MIG_DIR"
fi
if [[ "$destructive_count" -eq 0 ]]; then
    ok "no destructive rewrites found in .sqm migrations"
fi

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "$FAILED U6 additive-migration check(s) failed."
    exit 1
fi

echo ""
echo "U6 additive-migration fitness function passed."
