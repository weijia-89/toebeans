#!/usr/bin/env bash
# Install toebeans-specific git hooks by pointing core.hooksPath at scripts/git-hooks/.
#
# Idempotent: safe to run repeatedly. Records the previous hooksPath (if any) so you can
# revert with `git config --unset core.hooksPath`.

set -euo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)
HOOKS_DIR="$REPO_ROOT/scripts/git-hooks"

if [[ ! -d "$HOOKS_DIR" ]]; then
    echo "error: $HOOKS_DIR does not exist." >&2
    exit 1
fi

# Mark every hook script executable (in case the repo was cloned with non-exec perms).
chmod +x "$HOOKS_DIR"/* 2>/dev/null || true

CURRENT=$(git -C "$REPO_ROOT" config --get core.hooksPath 2>/dev/null || true)
if [[ "$CURRENT" == "scripts/git-hooks" ]]; then
    echo "Already installed: core.hooksPath = scripts/git-hooks"
    exit 0
fi

if [[ -n "$CURRENT" ]]; then
    echo "Warning: core.hooksPath was set to '$CURRENT'. Overwriting."
fi

git -C "$REPO_ROOT" config core.hooksPath scripts/git-hooks
echo "Installed: core.hooksPath = scripts/git-hooks"
echo ""
echo "Available hooks:"
ls -1 "$HOOKS_DIR" | sed 's/^/  /'
echo ""
echo "To uninstall: git config --unset core.hooksPath"
