#!/usr/bin/env bash
# CI-side equivalent of scripts/git-hooks/pre-commit's vibe-dangerous rule.
# Survives `git commit --no-verify` because CI does not use local hooks.
#
# USAGE:
#   bash scripts/ci-vibe-dangerous-check.sh <BASE_SHA> <HEAD_SHA>
#
# In GitHub Actions:
#   BASE = github.event.pull_request.base.sha (PR) or github.event.before (push)
#   HEAD = github.sha
#
# EXIT CODES:
#   0 — no vibe-dangerous paths touched, OR vibe-dangerous touched AND
#       .codeit/calibration.jsonl gained at least one new entry.
#   1 — vibe-dangerous touched but no new calibration entry.
#   2 — invocation error (missing args, no git history).

set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "usage: $0 <base-sha> <head-sha>" >&2
    exit 2
fi

BASE_SHA=$1
HEAD_SHA=$2

# GitHub Actions reports `github.event.before` as the all-zeros SHA when the
# pushed branch did not previously exist on the remote (the canonical case is
# the very first push to a new repo). `git diff <zeros>` cannot resolve to a
# real object, so the script blew up with "fatal: bad object 000...". Treat
# this as a diff against the empty tree, which surfaces every committed file
# as "newly added." On the initial scaffold push, that means every
# vibe-dangerous path is "touched"; the calibration-entry check below then
# validates that the scaffold itself shipped with calibration entries — which
# is exactly the invariant the rule is meant to defend.
ZERO_SHA="0000000000000000000000000000000000000000"
EMPTY_TREE_SHA="4b825dc642cb6eb9a060e54bf8d69288fbee4904"
if [[ "$BASE_SHA" == "$ZERO_SHA" ]]; then
    echo "ci-vibe-dangerous-check: first push detected (before=zeros) — using empty-tree as base"
    BASE_SHA="$EMPTY_TREE_SHA"
fi

# Same patterns as scripts/git-hooks/pre-commit. KEEP IN SYNC.
PATTERNS=(
    '^shared/src/commonMain/kotlin/app/toebeans/core/scheduler/'
    '^shared/src/commonMain/kotlin/app/toebeans/core/backup/'
    '^shared/src/jvmMain/kotlin/app/toebeans/core/backup/'
    '^shared/src/androidMain/kotlin/app/toebeans/core/backup/'
    '^shared/src/commonTest/kotlin/app/toebeans/core/scheduler/'
    '^shared/src/commonTest/kotlin/app/toebeans/core/backup/'
    '^shared/src/jvmTest/kotlin/app/toebeans/core/backup/'
    '^androidApp/src/main/kotlin/app/toebeans/android/notifications/'
    '^androidApp/src/test/kotlin/app/toebeans/android/notifications/'
    '^shared/src/commonMain/sqldelight/'
    '^androidApp/src/main/AndroidManifest\.xml$'
    '^.*/build\.gradle\.kts$'
    '^gradle/libs\.versions\.toml$'
    '^scripts/git-hooks/'
    '^scripts/ci-vibe-dangerous-check\.sh$'
    '^scripts/test_.*\.sh$'
    '^scripts/agents_claude_parity\.sh$'
    '^\.github/workflows/'
    '^AGENTS\.md$'
    '^CLAUDE\.md$'
)

CHANGED=$(git diff --name-only "$BASE_SHA" "$HEAD_SHA")
if [[ -z "$CHANGED" ]]; then
    echo "ci-vibe-dangerous-check: no files changed between $BASE_SHA..$HEAD_SHA"
    exit 0
fi

PATTERN=$(printf '%s\n' "${PATTERNS[@]}" | paste -sd '|' -)
VIBE_HITS=$(echo "$CHANGED" | grep -E "$PATTERN" || true)

if [[ -z "$VIBE_HITS" ]]; then
    echo "ci-vibe-dangerous-check: ✓ no vibe-dangerous paths touched"
    exit 0
fi

echo "ci-vibe-dangerous-check: vibe-dangerous paths touched in $BASE_SHA..$HEAD_SHA:"
echo "$VIBE_HITS" | sed 's/^/  /'
echo ""

# A new entry to calibration.jsonl is required. Check diff added at least one line
# beginning with { and ending with }.
NEW_ENTRIES=$(git diff --no-color "$BASE_SHA" "$HEAD_SHA" -- .codeit/calibration.jsonl \
    | grep -cE '^\+\{.*\}$' || true)

if [[ "$NEW_ENTRIES" -lt 1 ]]; then
    echo "BLOCKED: vibe-dangerous paths touched but .codeit/calibration.jsonl gained no new entries."
    echo "Per AGENTS.md § Confidence-score rule, every change to a vibe-dangerous surface"
    echo "must be scored and logged. This gate survives \`git commit --no-verify\`."
    echo ""
    echo "If you intentionally bypassed the local hook, retroactively add the missing"
    echo "calibration entry (or remove the vibe-dangerous change) and re-push."
    exit 1
fi

echo "ci-vibe-dangerous-check: ✓ $NEW_ENTRIES new calibration entries"
exit 0
