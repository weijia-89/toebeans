#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH="$ROOT/scripts/trainer_pr_review_patch_head.sh"
[[ -x "$PATCH" ]] || chmod +x "$PATCH"

FIXTURE=$(mktemp)
cat >"$FIXTURE" <<'JSON'
[
  {
    "id": 1,
    "body": "<!-- trainer-codereview-toebeans-chore-pr-hygiene-hardening -->\n<!-- head=e14a5ae verdict=APPROVE round=2 -->\n### Trainer notes\n1. **Program notes:** x\n2. **Your form:** y\n3. **Next session:** z\n"
  }
]
JSON

export TRAINER_PATCH_HEAD_FIXTURE="$FIXTURE"
export TRAINER_PATCH_HEAD_PR_HEAD=8fc49951e0f259d6ae90abe63a1ab5fbf9b79f72
export TRAINER_PATCH_HEAD_BRANCH_SLUG=chore-pr-hygiene-hardening
export TRAINER_PATCH_HEAD_DRY_RUN=1

out=$(bash "$PATCH" 48 weijia-89/toebeans)
echo "$out" | grep -q 'dry-run would patch head=8fc4995' || {
  echo "expected dry-run patch message, got: $out" >&2
  exit 1
}

export TRAINER_PATCH_HEAD_PR_HEAD=e14a5aeeee9efb37c00f39f9a6ebb45145775780
out=$(bash "$PATCH" 48 weijia-89/toebeans)
echo "$out" | grep -q 'already e14a5ae' || {
  echo "expected already synced, got: $out" >&2
  exit 1
}

rm -f "$FIXTURE"
echo "trainer_pr_review_patch_head self-test passed"
