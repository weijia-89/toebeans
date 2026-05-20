#!/usr/bin/env bash
# Negative-test harness for scripts/test_no_em_dash_in_docs.sh.
#
# Mirrors the shape of scripts/test_test_no_pbkdf2_in_backup_after_tbn2.sh.
# Stages synthetic Markdown / text fixtures under a tmpdir, runs the
# em-dash fitness function against that tmpdir, and asserts the exit
# code and report contents match the documented behavior.
#
# Fixtures cover:
#   1. Clean doc (no em-dashes): pass.
#   2. Doc with an em-dash, no allow marker: fail, file named in report.
#   3. Doc with em-dash plus EMDASH-ALLOWED marker in first 5 lines: pass.
#   4. Em-dash inside localonly/ subtree: pass (excluded from scope).
#   5. Mixed clean + dirty: fail, only dirty file named in report.
#   6. Em-dash in .txt file (not .md): fail (scope includes *.txt).
#   7. Em-dash in a non-scoped extension (.json): pass (out of scope).
#
# Usage: bash scripts/test_test_no_em_dash_in_docs.sh [repo-root]
#   repo-root defaults to "." so the harness can locate the fitness
#   function regardless of CWD.

set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_no_em_dash_in_docs.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

# U+2014 as literal bytes via bash ANSI-C quoting.
EMDASH=$'\xe2\x80\x94'

clean_fixtures() {
    find "$tmpdir" -mindepth 1 -delete
}

# ---- Fixture writers ---------------------------------------------------

write_clean_md() {
    cat > "$tmpdir/clean.md" <<'MD'
# Clean doc

This file has no em-dashes. Hyphens are fine, like "well-formed" and
the word "em-dash" itself. Periods. Commas. Parens (like this).
MD
}

write_emdash_md() {
    # Use printf so the EMDASH variable expands. Heredoc with 'MD' would
    # not interpolate, defeating the test.
    printf '%s\n' \
        "# Doc with em-dash" \
        "" \
        "This sentence has an em-dash: it reads ${EMDASH} like this." \
        > "$tmpdir/dirty.md"
}

write_emdash_with_allow_marker() {
    printf '%s\n' \
        "# Doc that discusses em-dashes" \
        "EMDASH-ALLOWED: this file is about the em-dash gate itself" \
        "" \
        "Example em-dash for documentation: ${EMDASH}" \
        > "$tmpdir/allowed.md"
}

write_emdash_in_localonly() {
    mkdir -p "$tmpdir/localonly"
    printf '%s\n' \
        "# Scratch note" \
        "" \
        "Free-form ${EMDASH} not gated." \
        > "$tmpdir/localonly/scratch.md"
}

write_emdash_txt() {
    printf '%s\n' "Plain text with em-dash: ${EMDASH}" > "$tmpdir/notes.txt"
}

write_emdash_json() {
    # JSON file: not in scope. Em-dashes inside should NOT trigger the
    # gate. This protects historical JSONL artifacts like calibration.jsonl.
    printf '%s\n' "{\"note\": \"em-dash here: ${EMDASH}\"}" > "$tmpdir/data.json"
}

# ---- Test cases --------------------------------------------------------

# Test 1: clean doc only -> exit 0.
clean_fixtures
write_clean_md
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 1: false-positive on a clean .md file"
    FAILED=$((FAILED + 1))
fi

# Test 2: em-dash present, no allow marker -> exit 1, file named.
clean_fixtures
write_emdash_md
if bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 2: missed em-dash in dirty.md (exit 0, should be nonzero)"
    FAILED=$((FAILED + 1))
fi
report=$(bash "$SCRIPT" "$tmpdir" 2>&1 || true)
if ! grep -qF "dirty.md" <<< "$report"; then
    echo "FAIL test 2: violation report did not name dirty.md"
    FAILED=$((FAILED + 1))
fi

# Test 3: EMDASH-ALLOWED marker in first 5 lines -> exit 0.
clean_fixtures
write_emdash_with_allow_marker
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 3: false-positive on a file with EMDASH-ALLOWED marker"
    FAILED=$((FAILED + 1))
fi

# Test 4: em-dash inside localonly/ -> exit 0 (excluded from scope).
clean_fixtures
write_emdash_in_localonly
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 4: false-positive on em-dash inside localonly/"
    FAILED=$((FAILED + 1))
fi

# Test 5: mixed clean + dirty -> exit 1, only dirty named.
clean_fixtures
write_clean_md
write_emdash_md
if bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 5: missed em-dash in mixed fixture"
    FAILED=$((FAILED + 1))
fi
report=$(bash "$SCRIPT" "$tmpdir" 2>&1 || true)
if grep -qF "clean.md" <<< "$report"; then
    echo "FAIL test 5: false-positive named clean.md"
    FAILED=$((FAILED + 1))
fi
if ! grep -qF "dirty.md" <<< "$report"; then
    echo "FAIL test 5: violation report did not name dirty.md in mixed fixture"
    FAILED=$((FAILED + 1))
fi

# Test 6: em-dash in .txt file -> exit 1 (scope includes *.txt).
clean_fixtures
write_emdash_txt
if bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 6: missed em-dash in .txt file (scope should include *.txt)"
    FAILED=$((FAILED + 1))
fi

# Test 7: em-dash in .json file -> exit 0 (out of scope).
clean_fixtures
write_emdash_json
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL test 7: false-positive on em-dash in .json (out of scope)"
    FAILED=$((FAILED + 1))
fi

if [[ "$FAILED" -gt 0 ]]; then
    echo ""
    echo "FAIL test_test_no_em_dash_in_docs: $FAILED check(s) failed"
    exit 1
fi

echo "OK test_test_no_em_dash_in_docs: all fixtures behaved as documented"
exit 0
