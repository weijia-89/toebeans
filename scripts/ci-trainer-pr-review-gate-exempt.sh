#!/usr/bin/env bash
# Return whether a PR touches only trainer-gate-exempt paths (docs/research text).
#
# Exit 0 — exempt (ci-trainer-pr-review-gate.sh may skip).
# Exit 1 — not exempt (gate must run).
# Exit 2 — usage / tooling error.
#
# Fixture mode (no gh):
#   TRAINER_PR_REVIEW_FILES_FIXTURE=path/to/files.txt bash $0 owner/repo 1

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <gh_repo> <pr_num>" >&2
  exit 2
fi

GH_REPO=$1
PR_NUM=$2

if [[ -n "${TRAINER_PR_REVIEW_FILES_FIXTURE:-}" ]]; then
  FILES=$(cat "$TRAINER_PR_REVIEW_FILES_FIXTURE")
else
  if ! command -v gh >/dev/null 2>&1; then
    echo "ci-trainer-pr-review-gate-exempt: gh CLI required" >&2
    exit 2
  fi
  FILES=$(gh api "repos/${GH_REPO}/pulls/${PR_NUM}/files" --paginate \
    --jq '.[].filename' 2>/dev/null || true)
fi

export FILES
python3 - <<'PY'
import os, sys

files = [ln.strip() for ln in os.environ.get("FILES", "").splitlines() if ln.strip()]

# Never exempt: agent policy, automation, product code, calibration.
NEVER_EXEMPT_NAMES = frozenset({"AGENTS.md", "CLAUDE.md"})
NEVER_EXEMPT_PREFIXES = (
    ".github/",
    "scripts/",
    "shared/",
    "androidApp/",
    "gradle/",
    ".codeit/",
)

# Exempt when every changed file is under these trees with text-only extensions.
EXEMPT_PREFIXES = ("docs/", "research/")
TEXT_EXTENSIONS = frozenset({".md", ".txt"})


def is_exempt_path(path: str) -> bool:
    if path in NEVER_EXEMPT_NAMES:
        return False
    if any(path.startswith(p) for p in NEVER_EXEMPT_PREFIXES):
        return False
    if not any(path.startswith(p) for p in EXEMPT_PREFIXES):
        return False
    dot = path.rfind(".")
    ext = path[dot:].lower() if dot != -1 else ""
    return ext in TEXT_EXTENSIONS


if not files:
    print("trainer PR review gate: not exempt (no files in PR diff)")
    sys.exit(1)

non_exempt = [p for p in files if not is_exempt_path(p)]
if non_exempt:
    print("trainer PR review gate: not exempt (requires review comment)")
    for p in non_exempt[:8]:
        print(f"  - {p}")
    if len(non_exempt) > 8:
        print(f"  ... and {len(non_exempt) - 8} more")
    sys.exit(1)

print(
    "trainer PR review gate: exempt (docs/research .md|.txt only; "
    f"{len(files)} file(s))"
)
sys.exit(0)
PY
