#!/usr/bin/env bash
# Sync <!-- head=... --> on the canonical trainer PR review comment to the current PR HEAD.
# Rerunning the gate alone cannot pass while head= is stale (ci-trainer-pr-review-gate.sh).
#
# Usage:
#   bash scripts/trainer_pr_review_patch_head.sh <pr_num> <owner/repo>
#
# Fixture self-test (no network):
#   TRAINER_PATCH_HEAD_FIXTURE=comments.json TRAINER_PATCH_HEAD_PR_HEAD=abcdef0 \
#     bash scripts/trainer_pr_review_patch_head.sh 48 weijia-89/buds

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <pr_num> <gh_repo>" >&2
  exit 2
fi

PR_NUM=$1
GH_REPO=$2
REPO_SLUG="${GH_REPO##*/}"

if [[ -n "${TRAINER_PATCH_HEAD_FIXTURE:-}" ]]; then
  HEAD_SHA=${TRAINER_PATCH_HEAD_PR_HEAD:?TRAINER_PATCH_HEAD_PR_HEAD required with fixture}
  BRANCH_SLUG=${TRAINER_PATCH_HEAD_BRANCH_SLUG:-chore-pr-hygiene-hardening}
  COMMENTS_JSON=$(cat "$TRAINER_PATCH_HEAD_FIXTURE")
  DRY_RUN=${TRAINER_PATCH_HEAD_DRY_RUN:-0}
else
  if ! command -v gh >/dev/null 2>&1; then
    echo "trainer_pr_review_patch_head: gh CLI required" >&2
    exit 1
  fi
  HEAD_SHA=$(gh pr view "$PR_NUM" --repo "$GH_REPO" --json headRefOid -q .headRefOid)
  BRANCH=$(gh pr view "$PR_NUM" --repo "$GH_REPO" --json headRefName -q .headRefName)
  BRANCH_SLUG=$(printf '%s' "$BRANCH" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//')
  COMMENTS_JSON=$(gh api "repos/${GH_REPO}/issues/${PR_NUM}/comments" --paginate 2>/dev/null || echo "[]")
  DRY_RUN=0
fi

HEAD_SHORT=${HEAD_SHA:0:7}
export COMMENTS_JSON HEAD_SHORT BRANCH_SLUG REPO_SLUG PR_NUM DRY_RUN

RESULT=$(python3 - <<'PY'
import json, os, re, sys

comments = json.loads(os.environ["COMMENTS_JSON"])
head_short = os.environ["HEAD_SHORT"].lower()
repo = os.environ["REPO_SLUG"]
branch_slug = os.environ["BRANCH_SLUG"]
pr = os.environ["PR_NUM"]
dry_run = os.environ.get("DRY_RUN") == "1"

marker_res = [
    re.compile(rf"trainer-codereview-{re.escape(repo)}-{re.escape(branch_slug)}", re.I),
    re.compile(rf"trainer-codereview-{re.escape(repo)}", re.I),
]
meta_head = re.compile(r"<!--\s*head=([0-9a-f]{7,40})", re.I)
meta_replace = re.compile(r"(<!--\s*head=)[0-9a-f]{7,40}", re.I)

def matches(body: str) -> bool:
    return any(p.search(body) for p in marker_res)

candidates = [c for c in comments if matches(c.get("body") or "")]
if not candidates:
    print("NO_COMMENT")
    sys.exit(0)

# Prefer branch-specific marker; fall back to repo-wide.
best = candidates[0]
for c in candidates:
    body = c.get("body") or ""
    if f"trainer-codereview-{repo}-{branch_slug}" in body.lower():
        best = c
        break

body = best.get("body") or ""
m = meta_head.search(body)
if not m:
    print("NO_META")
    sys.exit(1)

current = m.group(1).lower()
if current.startswith(head_short) or head_short.startswith(current):
    print("ALREADY_SYNCED")
    sys.exit(0)

new_body = meta_replace.sub(rf"\g<1>{head_short}", body, count=1)
print(json.dumps({"id": best.get("id"), "body": new_body}))
PY
)

case "$RESULT" in
  NO_COMMENT)
    echo "trainer_pr_review_patch_head: no canonical comment on PR #${PR_NUM}; skip"
    exit 0
    ;;
  NO_META)
    echo "trainer_pr_review_patch_head: comment missing head= meta line" >&2
    exit 1
    ;;
  ALREADY_SYNCED)
    echo "trainer_pr_review_patch_head: head already ${HEAD_SHORT} on PR #${PR_NUM}"
    exit 0
    ;;
esac

if [[ "$DRY_RUN" == "1" ]]; then
  echo "trainer_pr_review_patch_head: dry-run would patch head=${HEAD_SHORT} on PR #${PR_NUM}"
  exit 0
fi

COMMENT_ID=$(echo "$RESULT" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
PATCHED_BODY=$(mktemp)
echo "$RESULT" | python3 -c 'import json,sys; print(json.load(sys.stdin)["body"])' >"$PATCHED_BODY"
jq -n --rawfile b "$PATCHED_BODY" '{body: $b}' \
  | gh api -X PATCH "repos/${GH_REPO}/issues/comments/${COMMENT_ID}" --input - >/dev/null
rm -f "$PATCHED_BODY"
echo "trainer_pr_review_patch_head: patched comment id=${COMMENT_ID} to head=${HEAD_SHORT}"
