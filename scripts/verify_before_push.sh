#!/usr/bin/env bash
# scripts/verify_before_push.sh — deterministic local verification that mirrors CI.
#
# Runs every check that CI enforces on PRs, so that "green locally → green on CI"
# is the expected outcome. This is the script to run before opening a PR or
# pushing a branch that will become a PR.
#
# USAGE:
#   bash scripts/verify_before_push.sh          # full verify
#   bash scripts/verify_before_push.sh --fast   # skip Gradle tests (lint only)
#
# EXIT CODES:
#   0 — all checks passed
#   1 — one or more checks failed (see output for which)
#
# NOTE: This script does NOT replace the pre-commit hook (secrets, vibe-dangerous,
# calibration). Run both: pre-commit fires on every commit; verify_before_push
# fires before every push.

set -euo pipefail

RED=$'\033[1;31m'
GRN=$'\033[1;32m'
YEL=$'\033[1;33m'
NC=$'\033[0m'

FAST=0
if [[ "${1:-}" == "--fast" ]]; then
    FAST=1
fi

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

FAILED=0

run_check() {
    local name="$1"
    shift
    echo ""
    echo "==> $name"
    if "$@"; then
        echo -e "  ${GRN}PASS${NC} — $name"
    else
        echo -e "  ${RED}FAIL${NC} — $name"
        FAILED=$((FAILED + 1))
    fi
}

# ---------------------------------------------------------------------------
# 1. Fitness functions (lint-class CI gates) — fast, bash-based
# ---------------------------------------------------------------------------
run_check "Fitness: no-network"            bash scripts/test_no_network.sh .
run_check "Fitness: no-analytics"          bash scripts/test_no_analytics.sh .
run_check "Fitness: scheduler-purity"      bash scripts/test_scheduler_purity.sh .
run_check "Fitness: permission-allowlist"  bash scripts/test_permission_allowlist.sh .
run_check "Fitness: no-PII-in-crash-log"   bash scripts/test_no_pii_in_crash_log.sh .
run_check "Fitness: no-em-dash-in-docs"    bash scripts/test_no_em_dash_in_docs.sh .
run_check "Fitness: no-duplicate-private-test-class-names" \
    bash scripts/test_no_duplicate_private_test_class_names.sh .
run_check "Fitness: AGENTS/CLAUDE parity"  bash scripts/agents_claude_parity.sh .

# ---------------------------------------------------------------------------
# 2. Vibe-dangerous check (local mirror of CI gate)
#    Diff against origin/main to detect missing calibration entries.
# ---------------------------------------------------------------------------
if git rev-parse --verify origin/main >/dev/null 2>&1; then
    BASE=$(git merge-base HEAD origin/main)
    run_check "Vibe-dangerous review gate (local)" \
        bash scripts/ci-vibe-dangerous-check.sh "$BASE" HEAD "$(git branch --show-current)"
else
    echo -e "  ${YEL}SKIP${NC} — origin/main not found; cannot run vibe-dangerous diff"
fi

# ---------------------------------------------------------------------------
# 3. Gradle lint — must match CI exactly
#    CI runs: ./gradlew ktlintCheck detekt
# ---------------------------------------------------------------------------
run_check "Gradle lint (ktlint + detekt)" ./gradlew ktlintCheck detekt --console=plain

# ---------------------------------------------------------------------------
# 4. Gradle tests
#    CI runs: ./gradlew :shared:jvmTest :shared:testDebugUnitTest
# ---------------------------------------------------------------------------
if [[ "$FAST" -eq 0 ]]; then
    run_check "Shared JVM tests"          ./gradlew :shared:jvmTest --console=plain
    run_check "Android app unit tests"    ./gradlew :androidApp:testDebugUnitTest --console=plain
else
    echo ""
    echo "==> Gradle tests (skipped --fast)"
    echo "  ${YEL}SKIP${NC} — run without --fast to include tests"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
if [[ "$FAILED" -eq 0 ]]; then
    echo -e "${GRN}All checks passed.${NC} Safe to push."
    exit 0
else
    echo -e "${RED}$FAILED check(s) failed.${NC} Fix before pushing to avoid a red CI build."
    exit 1
fi
