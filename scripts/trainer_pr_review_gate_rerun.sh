#!/usr/bin/env bash
# Idempotently rerun the "Trainer PR review comment gate" job for a PR.
# Canonical copy for product repos: install beside trainer_pr_review_post.sh.
#
# Usage (repo root or from post script):
#   bash scripts/trainer_pr_review_gate_rerun.sh <pr_num> <owner/repo>
#
# Optional: omit both args on a PR branch — resolves PR via `gh pr view`.
# Env: TRAINER_GATE_RERUN_SKIP=1 — no-op; TRAINER_GATE_RERUN_DRY_RUN=1 — print only.

set -euo pipefail

GATE_JOB_NAME="Trainer PR review comment gate"
WORKFLOW_NAME="ci"

if [[ "${TRAINER_GATE_RERUN_SKIP:-}" == "1" ]]; then
  echo "trainer_pr_review_gate_rerun: skip (TRAINER_GATE_RERUN_SKIP=1)"
  exit 0
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "trainer_pr_review_gate_rerun: gh CLI not found; skip rerun" >&2
  exit 0
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "trainer_pr_review_gate_rerun: jq not found; skip rerun" >&2
  exit 0
fi

PR_NUM=${1:-}
GH_REPO=${2:-}

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "$GH_REPO" ]]; then
  REMOTE=$(git remote get-url origin 2>/dev/null || true)
  if [[ "$REMOTE" =~ github\.com[:/]([^/]+/[^/.]+) ]]; then
    GH_REPO="${BASH_REMATCH[1]%.git}"
  else
    echo "trainer_pr_review_gate_rerun: cannot infer gh repo from origin" >&2
    exit 0
  fi
fi

if [[ -z "$PR_NUM" ]]; then
  if ! PR_NUM=$(gh pr view --repo "$GH_REPO" --json number -q .number 2>/dev/null); then
    echo "trainer_pr_review_gate_rerun: no open PR for current branch; skip" >&2
    exit 0
  fi
fi

if ! BRANCH=$(gh pr view "$PR_NUM" --repo "$GH_REPO" --json headRefName -q .headRefName 2>/dev/null); then
  echo "trainer_pr_review_gate_rerun: cannot resolve headRefName for PR #${PR_NUM}; skip" >&2
  exit 0
fi
if ! HEAD_SHA=$(gh pr view "$PR_NUM" --repo "$GH_REPO" --json headRefOid -q .headRefOid 2>/dev/null); then
  echo "trainer_pr_review_gate_rerun: cannot resolve headRefOid for PR #${PR_NUM}; skip" >&2
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PATCH_HEAD="${SCRIPT_DIR}/trainer_pr_review_patch_head.sh"
if [[ -f "$PATCH_HEAD" ]]; then
  bash "$PATCH_HEAD" "$PR_NUM" "$GH_REPO" || {
    echo "trainer_pr_review_gate_rerun: head sync failed; fix comment then retry" >&2
  }
fi

RUN_IDS=$(gh run list --repo "$GH_REPO" --branch "$BRANCH" --workflow "$WORKFLOW_NAME" \
  --limit 50 --json databaseId,headSha,event,status \
  | jq -r --arg sha "$HEAD_SHA" '
      [
        .[] | select(.headSha == $sha and (.event == "pull_request" or .event == "pull_request_target"))
      ]
      + [.[] | select(.headSha == $sha)]
      | unique_by(.databaseId)
      | sort_by(.databaseId)
      | reverse
      | .[].databaseId
    ')

if [[ -z "$RUN_IDS" ]]; then
  echo "trainer_pr_review_gate_rerun: no ci workflow runs for PR #${PR_NUM} (${BRANCH}) head=${HEAD_SHA:0:7}; skip"
  exit 0
fi

RUN_ID=""
JOB_JSON=""
while IFS= read -r candidate; do
  [[ -z "$candidate" ]] && continue
  candidate_job=$(gh run view "$candidate" --repo "$GH_REPO" --json jobs --jq \
    ".jobs[] | select(.name == \"${GATE_JOB_NAME}\") | {id: .databaseId, status: .status, conclusion: .conclusion}" \
    2>/dev/null | head -1 || true)
  if [[ -n "$candidate_job" ]]; then
    RUN_ID="$candidate"
    JOB_JSON="$candidate_job"
    break
  fi
done <<< "$RUN_IDS"

if [[ -z "$RUN_ID" || -z "$JOB_JSON" ]]; then
  echo "trainer_pr_review_gate_rerun: gate job \"${GATE_JOB_NAME}\" not found in recent runs for PR #${PR_NUM}; skip"
  exit 0
fi

JOB_ID=$(echo "$JOB_JSON" | jq -r '.id // empty')
JOB_STATUS=$(echo "$JOB_JSON" | jq -r '.status // empty')
JOB_CONCLUSION=$(echo "$JOB_JSON" | jq -r '.conclusion // empty')

if [[ -z "$JOB_ID" ]]; then
  echo "trainer_pr_review_gate_rerun: job \"${GATE_JOB_NAME}\" not in run ${RUN_ID}; skip"
  exit 0
fi

if [[ "$JOB_CONCLUSION" == "success" ]]; then
  echo "trainer_pr_review_gate_rerun: gate already green (run ${RUN_ID}); skip"
  exit 0
fi

if [[ "$JOB_STATUS" == "in_progress" || "$JOB_STATUS" == "queued" ]]; then
  echo "trainer_pr_review_gate_rerun: gate ${JOB_STATUS} (run ${RUN_ID}); skip"
  exit 0
fi

RUN_STATUS=$(gh run view "$RUN_ID" --repo "$GH_REPO" --json status -q .status)
if [[ "$RUN_STATUS" == "in_progress" || "$RUN_STATUS" == "queued" || "$RUN_STATUS" == "pending" ]]; then
  echo "trainer_pr_review_gate_rerun: workflow run ${RUN_ID} still ${RUN_STATUS}; retry after it finishes:"
  echo "  bash scripts/trainer_pr_review_gate_rerun.sh ${PR_NUM} ${GH_REPO}"
  exit 0
fi

if [[ "${TRAINER_GATE_RERUN_DRY_RUN:-}" == "1" ]]; then
  echo "trainer_pr_review_gate_rerun: dry-run would rerun run=${RUN_ID} job=${JOB_ID} (${GATE_JOB_NAME})"
  exit 0
fi

if gh run rerun "$RUN_ID" --repo "$GH_REPO" --job "$JOB_ID" 2>/dev/null; then
  echo "trainer_pr_review_gate_rerun: rerun triggered run=${RUN_ID} job=${JOB_ID} (${GATE_JOB_NAME})"
elif gh run rerun "$RUN_ID" --repo "$GH_REPO" --failed 2>/dev/null; then
  echo "trainer_pr_review_gate_rerun: rerun triggered failed jobs on run=${RUN_ID}"
else
  echo "trainer_pr_review_gate_rerun: could not rerun (run may be too old or locked); push empty commit or re-run gate from Actions UI" >&2
  exit 0
fi
