#!/usr/bin/env bash
# scripts/agent_hooks_layout_check.sh — layout guard for editor-agnostic agent hooks.
#
# Asserts the load-bearing hook scripts and the UI-design-handoff policy live under
# scripts/agent-hooks/, that the thin Cursor registration points there, and that
# docs + .gitignore references name the new locations.
#
# NOTE: deliberately NOT named scripts/test_*.sh. That prefix is vibe-dangerous per
# scripts/git-hooks/pre-commit and would require a calibration entry; this is a careful-tier
# layout check only.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fails=0
pass() { printf '  ok   - %s\n' "$1"; }
fail() { printf '  FAIL - %s\n' "$1"; fails=$((fails + 1)); }

HOOKS_DIR="scripts/agent-hooks"
HOOKS=(
    style-lab-session-context.sh
    deny-magic-patterns-mcp.sh
    style-lab-reminder.sh
)

echo "agent_hooks_layout_check: checking layout"

# 1. Three reusable hook scripts exist with mode 755.
for h in "${HOOKS[@]}"; do
    p="$HOOKS_DIR/$h"
    if [[ -f "$p" ]]; then
        pass "$p exists"
    else
        fail "$p exists"
        continue
    fi
    mode=$(stat -f '%Lp' "$p" 2>/dev/null || stat -c '%a' "$p" 2>/dev/null || echo "unknown")
    if [[ "$mode" == "755" ]]; then
        pass "$p mode 755"
    else
        fail "$p mode 755 (got '$mode')"
    fi
done

# 2. Editor-independent policy file exists.
if [[ -f "$HOOKS_DIR/ui-design-handoff.md" ]]; then
    pass "$HOOKS_DIR/ui-design-handoff.md exists"
else
    fail "$HOOKS_DIR/ui-design-handoff.md exists"
fi

# 3. Thin Cursor registration points at the new paths, with no stale old command paths.
if [[ -f .cursor/hooks.json ]]; then
    for h in "${HOOKS[@]}"; do
        if grep -qF "scripts/agent-hooks/$h" .cursor/hooks.json; then
            pass "hooks.json registers scripts/agent-hooks/$h"
        else
            fail "hooks.json registers scripts/agent-hooks/$h"
        fi
    done
    if grep -qF '.cursor/hooks/' .cursor/hooks.json; then
        fail "hooks.json has no stale .cursor/hooks/ command paths"
    else
        pass "hooks.json has no stale .cursor/hooks/ command paths"
    fi
else
    fail ".cursor/hooks.json exists"
fi

# 4. Old content folder must no longer hold the scripts.
for h in "${HOOKS[@]}"; do
    if [[ ! -e ".cursor/hooks/$h" ]]; then
        pass ".cursor/hooks/$h removed"
    else
        fail ".cursor/hooks/$h removed"
    fi
done

# 5. Docs + .gitignore references name the new locations.
if grep -qF 'scripts/agent-hooks/ui-design-handoff.md' docs/design-handoff.md; then
    pass "docs/design-handoff.md names new policy path"
else
    fail "docs/design-handoff.md names new policy path"
fi
if grep -qF 'scripts/agent-hooks/' docs/design-handoff.md; then
    pass "docs/design-handoff.md names scripts/agent-hooks/"
else
    fail "docs/design-handoff.md names scripts/agent-hooks/"
fi
if grep -qF 'scripts/agent-hooks/' CHANGELOG.md; then
    pass "CHANGELOG.md names scripts/agent-hooks/"
else
    fail "CHANGELOG.md names scripts/agent-hooks/"
fi
if grep -qF 'scripts/agent-hooks/state/continual-learning.json' .gitignore; then
    pass ".gitignore names new state path"
else
    fail ".gitignore names new state path"
fi

if [[ "$fails" -gt 0 ]]; then
    echo "agent_hooks_layout_check: FAIL ($fails assertion(s))"
    exit 1
fi
echo "agent_hooks_layout_check: ok"
