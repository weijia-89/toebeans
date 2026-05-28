#!/usr/bin/env bash
# CI: validate the pull request description on GitHub.
# Usage: scripts/ci_pr_body_lint.sh [PR_NUMBER]
# Defaults PR_NUMBER from GITHUB_EVENT_PULL_REQUEST_NUMBER or gh pr view.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PR_NUM="${1:-${GITHUB_EVENT_PULL_REQUEST_NUMBER:-}}"
if [[ -z "$PR_NUM" ]]; then
  PR_NUM="$(gh pr view --json number -q .number 2>/dev/null || true)"
fi
[[ -n "$PR_NUM" ]] || {
  echo "ci_pr_body_lint.sh: could not determine PR number" >&2
  exit 1
}

TMP="$(mktemp "${TMPDIR:-/tmp}/pr-body-lint.XXXXXX")"
trap 'rm -f "$TMP"' EXIT

gh pr view "$PR_NUM" --json body -q .body >"$TMP"
bash "$ROOT/scripts/pr_body_validate.sh" "$TMP"
echo "ci_pr_body_lint.sh: PR #${PR_NUM} description OK"
