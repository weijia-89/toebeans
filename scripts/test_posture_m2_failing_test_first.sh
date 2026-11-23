#!/usr/bin/env bash
# Fitness function - M2: a vibe-dangerous change still needs a scored failing
# test first, paired with a calibration entry.
#
# The pre-commit hook's own VIBE_DANGEROUS array is the source of truth. This
# checker parses that array, proves it still covers every required surface,
# proves the hook still blocks on a missing calibration entry, and proves the
# CI mirror has not drifted from the hook list.
#
# Usage: bash scripts/test_posture_m2_failing_test_first.sh [repo-root]

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

HOOK="$ROOT/scripts/git-hooks/pre-commit"
MIRROR="$ROOT/scripts/ci-vibe-dangerous-check.sh"
for required in "$HOOK" "$MIRROR"; do
    if [[ ! -f "$required" ]]; then
        echo "FAIL missing required file: $required"
        exit 1
    fi
done

parse_patterns() {
    local file="$1" marker="$2"
    awk -v marker="$marker" '
        index($0, marker) { inside = 1; next }
        inside && /^[[:space:]]*\)/ { exit }
        inside && /^[[:space:]]*#/ { next }
        inside { print }
    ' "$file" | sed -nE "s/^[[:space:]]*'([^']+)'.*/\1/p"
}

HOOK_PATTERNS=$(parse_patterns "$HOOK" 'VIBE_DANGEROUS=(')
MIRROR_PATTERNS=$(parse_patterns "$MIRROR" 'PATTERNS=(')

if [[ -z "$(grep -v '^[[:space:]]*$' <<< "$HOOK_PATTERNS")" ]]; then
    echo "FAIL could not parse VIBE_DANGEROUS from the pre-commit hook"
    exit 1
fi
if [[ -z "$(grep -v '^[[:space:]]*$' <<< "$MIRROR_PATTERNS")" ]]; then
    echo "FAIL could not parse PATTERNS from the CI mirror"
    exit 1
fi

# label | literal substring that must appear in a hook path pattern.
# The hook stores regexes with escaped dots, so compare as fixed text.
CATEGORIES=(
    'scheduler|scheduler'
    'backup|backup'
    'notifications|notifications'
    'schema migrations|sqldelight'
    'manifest permissions|AndroidManifest'
    'dependency additions|libs\.versions'
    'dependency additions (Gradle)|build\.gradle\.kts'
    'scripts|scripts/'
    'workflows|workflows'
)

for row in "${CATEGORIES[@]}"; do
    label="${row%%|*}"
    needle="${row#*|}"
    if grep -qF -- "$needle" <<< "$HOOK_PATTERNS"; then
        ok "hook pattern list covers $label"
    else
        fail "hook pattern list no longer covers $label"
    fi
done

hook_code=$(awk '!/^[[:space:]]*#/ { print }' "$HOOK")

if ! grep -qE 'git diff --cached' <<< "$hook_code"; then
    fail "hook no longer inspects the staged diff"
else
    ok "hook inspects the staged diff"
fi
if ! grep -q 'calibration\.jsonl' <<< "$hook_code"; then
    fail "hook no longer requires a calibration entry"
else
    ok "hook requires a calibration entry"
fi
if ! grep -qE 'grep -qx[^#]*calibration' <<< "$hook_code"; then
    fail "hook no longer checks that the calibration entry is staged"
else
    ok "hook checks that the calibration entry is staged"
fi
if ! grep -qF '^\+\{' <<< "$hook_code"; then
    fail "hook no longer checks for a new JSONL calibration entry"
else
    ok "hook checks for a new JSONL calibration entry"
fi

while IFS= read -r pattern; do
    [[ -z "$pattern" ]] && continue
    if ! grep -qF -- "$pattern" <<< "$MIRROR_PATTERNS"; then
        fail "CI mirror drifted; hook pattern missing from mirror: $pattern"
    fi
done <<< "$HOOK_PATTERNS"

while IFS= read -r pattern; do
    [[ -z "$pattern" ]] && continue
    if ! grep -qF -- "$pattern" <<< "$HOOK_PATTERNS"; then
        fail "CI mirror drifted; mirror pattern missing from hook: $pattern"
    fi
done <<< "$MIRROR_PATTERNS"

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "$FAILED M2 failing-test-first check(s) failed."
    exit 1
fi

echo ""
echo "M2 failing-test-first fitness function passed."
