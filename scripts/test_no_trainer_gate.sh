#!/usr/bin/env bash
# Guard that the legacy trainer review requirement stays removed.
# It checks exact tokens so the check itself does not trip its own assertions.

set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
FAILED=0

pass() { echo "PASS - $1"; }
fail() { echo "FAIL - $1"; FAILED=$((FAILED + 1)); }

for rel in \
    .github/workflows/trainer-gate-rerun.yml \
    scripts/ci-trainer-pr-review-gate.sh \
    scripts/ci-trainer-pr-review-gate-exempt.sh \
    scripts/trainer_pr_review_post.sh \
    scripts/trainer_pr_review_patch_head.sh \
    scripts/trainer_pr_review_gate_rerun.sh \
    scripts/test_ci_trainer_pr_review_gate.sh \
    scripts/test_trainer_pr_review_gate_rerun.sh \
    scripts/test_trainer_pr_review_patch_head.sh; do
    if [[ -e "$ROOT/$rel" ]]; then
        fail "path still present: $rel"
    else
        pass "path absent: $rel"
    fi
done

for token in \
    'trainer-pr-review-gate' \
    'Trainer PR review comment gate' \
    'scripts/test_ci_trainer_pr_review_gate.sh' \
    'scripts/test_trainer_pr_review_gate_rerun.sh' \
    'scripts/test_trainer_pr_review_patch_head.sh' \
    'scripts/trainer_pr_review_patch_head.sh' \
    'scripts/ci-trainer-pr-review-gate.sh' \
    'post trainer review'; do
    hits=$(grep -rFn -- "$token" "$ROOT/.github/workflows/ci.yml" "$ROOT/scripts/gh_pr_create.sh" 2>/dev/null || true)
    if [[ -n "$hits" ]]; then
        fail "token still present: $token"
    else
        pass "token absent: $token"
    fi
done

if [[ "$FAILED" -eq 0 ]]; then
    echo "PASS - legacy review gate absence: all checks green"
    exit 0
fi
echo "FAIL - $FAILED check(s) failed"
exit 1
