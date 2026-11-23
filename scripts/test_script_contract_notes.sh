#!/usr/bin/env bash
# Fitness function — scripts that carry prose references to the removed agent
# contract files must carry the one-line note at the top of the file.
#
# The note is a comment copied in meaning from the ADR top note. It explains
# that references to AGENTS.md / CLAUDE.md mean the local working copies kept
# in a contributor's checkout. Shebang stays on line 1; the note is line 2.

set -uo pipefail

NOTE="# Note (2026-11-22): the agent contract files were removed from this public repository; references to them mean the local working copies kept in a contributor's checkout."

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

SCRIPTS=(
    "scripts/agents_claude_parity.sh"
    "scripts/git-hooks/pre-commit"
    "scripts/git-hooks/pre-push"
    "scripts/test_no_network.sh"
    "scripts/test_permission_allowlist.sh"
    "scripts/test_test_agents_claude_parity.sh"
)

FAILED=0

for rel in "${SCRIPTS[@]}"; do
    path="$ROOT/$rel"
    if [ ! -f "$path" ]; then
        echo "FAIL — $rel (missing file)"
        FAILED=$((FAILED + 1))
        continue
    fi
    second=$(sed -n '2p' "$path")
    if [ "$second" = "$NOTE" ]; then
        echo "PASS — $rel"
    else
        echo "FAIL — $rel (line 2 was: $second)"
        FAILED=$((FAILED + 1))
    fi
done

if [ "$FAILED" -eq 0 ]; then
    echo "PASS — script contract-note header checker: all 6 scripts carry the note on line 2."
    exit 0
fi
echo "FAIL — $FAILED script(s) missing the contract note on line 2."
exit 1
