#!/usr/bin/env bash
# Smoke test for trainer_pr_review_gate_rerun.sh (no gh API calls).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RERUN="$ROOT/scripts/trainer_pr_review_gate_rerun.sh"

[[ -x "$RERUN" ]] || chmod +x "$RERUN"

export TRAINER_GATE_RERUN_SKIP=1
if ! bash "$RERUN" 1 "owner/repo" 2>&1 | grep -q 'TRAINER_GATE_RERUN_SKIP'; then
  echo "expected skip message" >&2
  exit 1
fi

export TRAINER_GATE_RERUN_SKIP=
export TRAINER_GATE_RERUN_DRY_RUN=1
if ! bash "$RERUN" 999999 "nonexistent/fake-repo" >/dev/null 2>&1; then
  :
fi

echo "trainer_pr_review_gate_rerun self-test passed"
