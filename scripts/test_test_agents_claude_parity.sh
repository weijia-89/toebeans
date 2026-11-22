#!/usr/bin/env bash
# Self-test for scripts/agents_claude_parity.sh.
#
# The contract files AGENTS.md and CLAUDE.md are local-only working copies and
# are absent from a clean public checkout. The fitness function must enforce
# parity when both copies are present, and skip cleanly otherwise.
#
# Cases:
#   1. neither file present -> SKIP, exit 0
#   2. both present and identical -> parity OK, exit 0
#   3. both present and drifted -> blocked, exit 1
#   4. exactly one present -> SKIP, exit 0

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PARITY="$SCRIPT_DIR/agents_claude_parity.sh"

FAILED=0
pass() { echo "PASS — $1"; }
fail() { echo "FAIL — $1"; FAILED=$((FAILED + 1)); }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/scripts"
cp "$PARITY" "$TMP/scripts/agents_claude_parity.sh"

CONTRACT='## Posture

- Local-only. No network calls from v1 code.
'

run_case() {
    local label="$1" expect_code="$2" expect_text="$3"
    local out code
    out=$(bash "$TMP/scripts/agents_claude_parity.sh" "$TMP" 2>&1) && code=0 || code=$?
    if [[ "$code" -ne "$expect_code" ]]; then
        fail "$label (exit $code, expected $expect_code): $out"
    elif [[ -n "$expect_text" ]] && [[ "$out" != *"$expect_text"* ]]; then
        fail "$label (missing '$expect_text'): $out"
    else
        pass "$label"
    fi
}

# Case 1: clean public checkout, neither file present.
run_case "no contract files -> SKIP" 0 "SKIP"

# Case 2: local working copies present and in sync.
printf '%s\n' "$CONTRACT" >"$TMP/AGENTS.md"
printf '%s\n' "$CONTRACT" >"$TMP/CLAUDE.md"
run_case "identical pair -> parity OK" 0 "parity OK"

# Case 3: drifted pair must still be blocked.
printf '%s\n' "$CONTRACT" >"$TMP/AGENTS.md"
printf '%s\n' "${CONTRACT}- a drifted line" >"$TMP/CLAUDE.md"
run_case "drifted pair -> blocked" 1 "drifted"

# Case 4: partial local state must not fail a contributor checkout.
rm -f "$TMP/CLAUDE.md"
printf '%s\n' "$CONTRACT" >"$TMP/AGENTS.md"
run_case "single file -> SKIP" 0 "SKIP"

if [[ "$FAILED" -eq 0 ]]; then
    echo "PASS — agents_claude_parity self-test: all 4 cases green"
    exit 0
fi
echo "FAIL — $FAILED case(s) failed"
exit 1
