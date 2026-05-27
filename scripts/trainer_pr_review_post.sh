#!/usr/bin/env bash
# Post or PATCH the canonical trainer code-review comment on an open PR.
# Canonical copy for product repos (toebeans, buds): install under <repo>/scripts/.
#
# Usage (product repo root):
#   bash scripts/trainer_pr_review_post.sh <pr_num> <verdict> <round> <body.md>
#
# verdict: APPROVE | REQUEST_CHANGES | BLOCK
#
# Order: post/PATCH before push when CI should pass on that SHA (trainer-codereview-gate.md).
# After posting on an already-pushed PR, re-run the failed "Trainer PR review comment gate"
# workflow (or push an empty commit) so CI picks up the comment without waiting on Gradle.

set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "usage: $0 <pr_num> <verdict> <round> <body.md>" >&2
  exit 2
fi

PR_NUM=$1
VERDICT=$2
ROUND=$3
BODY_FILE=$4
[[ -f "$BODY_FILE" ]] || { echo "missing body file: $BODY_FILE" >&2; exit 2; }

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
HEAD_SHA=$(git rev-parse HEAD)
HEAD_SHORT=${HEAD_SHA:0:7}
BRANCH_SLUG=${BRANCH//\//-}
REMOTE=$(git remote get-url origin 2>/dev/null || true)
if [[ "$REMOTE" =~ github\.com[:/]([^/]+/[^/.]+) ]]; then
  GH_REPO="${BASH_REMATCH[1]%.git}"
else
  echo "cannot infer gh repo from origin: $REMOTE" >&2
  exit 2
fi
REPO_SLUG="${GH_REPO##*/}"

MARKER="<!-- trainer-codereview-${REPO_SLUG}-${BRANCH_SLUG} -->"
META="<!-- head=${HEAD_SHORT} verdict=${VERDICT} round=${ROUND} -->"

OUT=$(mktemp)
{
  echo "$MARKER"
  echo "$META"
  cat "$BODY_FILE"
} >"$OUT"

COMMENT_ID=$(gh api "repos/${GH_REPO}/issues/${PR_NUM}/comments" --paginate \
  --jq ".[] | select(.body | contains(\"trainer-codereview-${REPO_SLUG}-${BRANCH_SLUG}\")) | .id" 2>/dev/null | head -1)

if [[ -n "$COMMENT_ID" ]]; then
  jq -n --rawfile b "$OUT" '{body: $b}' \
    | gh api -X PATCH "repos/${GH_REPO}/issues/comments/${COMMENT_ID}" --input - >/dev/null
  echo "PATCHED comment id=${COMMENT_ID} on PR #${PR_NUM} (${GH_REPO}) head=${HEAD_SHORT} verdict=${VERDICT}"
else
  gh pr comment "$PR_NUM" --repo "$GH_REPO" --body-file "$OUT"
  echo "POSTED new comment on PR #${PR_NUM} (${GH_REPO}) head=${HEAD_SHORT} verdict=${VERDICT}"
fi

rm -f "$OUT"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RERUN="${SCRIPT_DIR}/trainer_pr_review_gate_rerun.sh"
if [[ -f "$RERUN" ]]; then
  bash "$RERUN" "$PR_NUM" "$GH_REPO" || {
    echo "trainer_pr_review_post: comment posted; gate rerun failed (re-run: bash scripts/trainer_pr_review_gate_rerun.sh ${PR_NUM} ${GH_REPO})" >&2
  }
fi
