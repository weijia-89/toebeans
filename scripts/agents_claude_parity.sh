#!/usr/bin/env bash
# Fitness function #6 (parity) — AGENTS.md and CLAUDE.md must hold the same contract.
# We compare the canonicalized contract block (after the divider that follows the file's preamble).

set -euo pipefail

ROOT="${1:-.}"
AGENTS="$ROOT/AGENTS.md"
CLAUDE="$ROOT/CLAUDE.md"

if [ ! -f "$AGENTS" ] || [ ! -f "$CLAUDE" ]; then
    echo "::error::AGENTS.md or CLAUDE.md missing."
    exit 1
fi

# Extract everything AFTER the first "## Posture" heading in each file, then compare hashes.
extract_contract() {
    awk '/^## Posture/{flag=1} flag' "$1"
}

agents_hash=$(extract_contract "$AGENTS" | shasum -a 256 | awk '{print $1}')
claude_hash=$(extract_contract "$CLAUDE" | shasum -a 256 | awk '{print $1}')

if [ "$agents_hash" != "$claude_hash" ]; then
    echo "::error::AGENTS.md and CLAUDE.md have drifted."
    echo "  AGENTS.md contract hash:  $agents_hash"
    echo "  CLAUDE.md contract hash:  $claude_hash"
    echo
    echo "Run 'diff <(awk \"/^## Posture/{flag=1} flag\" $AGENTS) <(awk \"/^## Posture/{flag=1} flag\" $CLAUDE)' to inspect."
    exit 1
fi

echo "✓ AGENTS.md / CLAUDE.md contract parity OK ($agents_hash)."
