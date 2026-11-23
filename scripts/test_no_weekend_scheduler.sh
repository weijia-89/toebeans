#!/usr/bin/env bash
# Guard that the weekend scheduler kit stays removed from the public repo.

set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
FAILED=0

pass() { echo "PASS - $1"; }
fail() { echo "FAIL - $1"; FAILED=$((FAILED + 1)); }

for rel in \
    docs/orchestration/sprint-orchestrator.md \
    scripts/scheduler \
    scripts/scheduler/tests/test-scheduler.sh \
    scripts/scheduler/sprint-run.sh \
    scripts/scheduler/preflight.sh \
    scripts/scheduler/install-weekend-sprints.sh \
    scripts/scheduler/prompts/toebeans-sat.txt \
    scripts/scheduler/prompts/toebeans-sun.txt \
    scripts/scheduler/prompts/toebeans-gradle9.txt \
    scripts/scheduler/prompts/baby-app-audit-sat.txt \
    scripts/scheduler/prompts/baby-app-audit-sun.txt; do
    if [[ -e "$ROOT/$rel" ]]; then
        fail "path still present: $rel"
    else
        pass "path absent: $rel"
    fi
done

for token in \
    'scripts/scheduler/tests/test-scheduler.sh' \
    'Sprint scheduler kit tests' \
    'scripts/scheduler/prompts/'; do
    hits=$(grep -rFn -- "$token" "$ROOT/.github/workflows/ci.yml" "$ROOT/CHANGELOG.md" 2>/dev/null || true)
    if [[ -n "$hits" ]]; then
        fail "token still present: $token"
    else
        pass "token absent: $token"
    fi
done

if [[ "$FAILED" -eq 0 ]]; then
    echo "PASS - weekend scheduler absence: all checks green"
    exit 0
fi
echo "FAIL - $FAILED check(s) failed"
exit 1
