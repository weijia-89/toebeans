#!/usr/bin/env bash
# Fitness function: verify the scripts/git-hooks/pre-commit secrets-scan and
# vibe-dangerous rules trigger correctly against known-bad fixtures.
#
# Does NOT test the actual git-commit flow (would require a sandbox repo).
# Tests only the REGEX correctness of the secrets patterns and the vibe-dangerous
# pattern list.
#
# Usage: bash scripts/test_pre_commit_hook.sh <repo-root>

set -euo pipefail

REPO_ROOT=${1:-.}
HOOK="$REPO_ROOT/scripts/git-hooks/pre-commit"

if [[ ! -f "$HOOK" ]]; then
    echo "✗ hook not found at $HOOK"
    exit 1
fi

# Extract the SECRET_PATTERNS array literal from the hook and replay it.
# This is more brittle than sourcing the hook, but the hook has side effects on
# stdin/git so sourcing would be wrong.
PATTERNS_RAW=$(awk '
    /^    SECRET_PATTERNS=\(/ {in_arr=1; next}
    in_arr && /^    \)/ {in_arr=0; exit}
    in_arr {print}
' "$HOOK" | sed -E "s/^ *'([^']+)'.*/\\1/")

if [[ -z "$PATTERNS_RAW" ]]; then
    echo "✗ could not parse SECRET_PATTERNS from $HOOK"
    exit 1
fi

REGEX=$(echo "$PATTERNS_RAW" | paste -sd '|' -)
REGEX="($REGEX)"

# Positive fixtures (must match). Assembled at runtime so the SOURCE of this
# file never contains a credential-shaped token adjacent on one line — that
# would trip the hook's own secrets-scan against this file.
mk() { printf '%s%s' "$1" "$2"; }
FAKE_AWS=$(mk "AKIA" "IOSFODNN7EXAMPLE")
FAKE_GH=$(mk "ghp_" "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
FAKE_SLACK=$(mk "xoxb-" "12345678901-12345678901-aBcDeFgHiJkLmNoPqRsTuVwX")
FAKE_PEM=$(mk "-----BEGIN " "RSA PRIVATE KEY-----")
FAKE_OAI=$(mk "sk-" "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
FAKE_GLPAT=$(mk "glpat-" "aaaaaaaaaaaaaaaaaaaa")

FAILED=0
for fixture in "$FAKE_AWS" "$FAKE_GH" "$FAKE_SLACK" "$FAKE_PEM" "$FAKE_OAI" "$FAKE_GLPAT"; do
    if ! echo "$fixture" | grep -qE "$REGEX"; then
        echo "✗ secrets regex MISSED a known-bad fixture: $fixture"
        FAILED=$((FAILED + 1))
    fi
done

# Negative fixtures (must NOT match). Pattern-shaped but not credential-shaped.
NEG_AKIA_TYPE="\`AKIA[0-9A-Z]{16}\`  in a doc"   # the pattern as docs
NEG_GH_LOW="ghp_lowercase"                       # too short
NEG_SLACK_SHORT="xoxb-12345"                     # too short
NEG_BEGIN_LOWER="begin private key"              # wrong case

for fixture in "$NEG_AKIA_TYPE" "$NEG_GH_LOW" "$NEG_SLACK_SHORT" "$NEG_BEGIN_LOWER"; do
    if echo "$fixture" | grep -qE "$REGEX"; then
        echo "✗ secrets regex FALSE-POSITIVED on: $fixture"
        FAILED=$((FAILED + 1))
    fi
done

# Vibe-dangerous pattern list must include all expected categories.
# Distinctive substrings to grep for. Use forms that appear verbatim in the
# hook source (where dots are regex-escaped as `\.`). Each substring is
# unique enough to avoid false-positive matches with other comment text.
EXPECTED_VIBE=(
    'scheduler'
    'backup'
    'notifications'
    'sqldelight'
    'AndroidManifest'
    'gradle\.kts'      # build.gradle.kts (escaped in source)
    'libs\.versions'   # gradle/libs.versions.toml
    'git-hooks'
    'workflows'
    'AGENTS\.md'
    'CLAUDE\.md'
)

for needle in "${EXPECTED_VIBE[@]}"; do
    if ! grep -qF "$needle" "$HOOK"; then
        echo "✗ vibe-dangerous list in $HOOK is missing pattern containing: $needle"
        FAILED=$((FAILED + 1))
    fi
done

# Mirror script must keep its pattern list in sync.
MIRROR="$REPO_ROOT/scripts/ci-vibe-dangerous-check.sh"
if [[ -f "$MIRROR" ]]; then
    for needle in "${EXPECTED_VIBE[@]}"; do
        if ! grep -qF "$needle" "$MIRROR"; then
            echo "✗ vibe-dangerous list in $MIRROR (CI mirror) drifted; missing: $needle"
            FAILED=$((FAILED + 1))
        fi
    done
fi

if [[ "$FAILED" -gt 0 ]]; then
    echo ""
    echo "✗ test_pre_commit_hook: $FAILED check(s) failed"
    exit 1
fi

echo "✓ test_pre_commit_hook: secrets-regex + vibe-dangerous pattern list OK"
exit 0
