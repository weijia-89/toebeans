#!/usr/bin/env bash
# Post or update the canonical trainer code-review comment on the open PR for
# the current branch. Mechanical companion to ci-trainer-pr-review-gate.sh.
#
# Usage (repo root):
#   bash scripts/trainer_pr_review_post.sh <pr_num> <verdict> <round> <body.md>
#
# verdict: APPROVE | REQUEST_CHANGES | BLOCK
# body.md must include ### Findings and ### Pedagogy (trainer-github-pr-commentary.md).

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

MARKER="<!-- trainer-codereview-toebeans-${BRANCH_SLUG} -->"
META="<!-- head=${HEAD_SHORT} verdict=${VERDICT} round=${ROUND} -->"

OUT=$(mktemp)
{
  echo "$MARKER"
  echo "$META"
  cat "$BODY_FILE"
} >"$OUT"

COMMENT_ID=$(gh api "repos/${GH_REPO}/issues/${PR_NUM}/comments" --paginate \
  -q ".[] | select(.body | test(\"trainer-codereview-toebeans-${BRANCH_SLUG}\")) | .id" 2>/dev/null | head -1)

if [[ -n "$COMMENT_ID" ]]; then
  gh api -X PATCH "repos/${GH_REPO}/issues/comments/${COMMENT_ID}" \
    -f body=@"$OUT" >/dev/null
  echo "PATCHED comment id=${COMMENT_ID} on PR #${PR_NUM} (${GH_REPO}) head=${HEAD_SHORT} verdict=${VERDICT}"
else
  gh pr comment "$PR_NUM" --repo "$GH_REPO" --body-file "$OUT"
  echo "POSTED new comment on PR #${PR_NUM} (${GH_REPO}) head=${HEAD_SHORT} verdict=${VERDICT}"
fi

rm -f "$OUT"
