#!/usr/bin/env bash
# Safe wrapper for `gh pr create` — always uses --body-file (never inline --body).
#
# Why: inline --body with literal \n renders as backslash-n on GitHub; backticks
# in --body execute command substitution and dump build logs into the PR.
#
# Usage:
#   scripts/gh_pr_create.sh --title "feat: foo" --body-file /tmp/pr-body.md
#   scripts/gh_pr_create.sh --title "feat: foo"   # writes body from stdin → temp file
#
# After create, post trainer review before expecting CI green (see AGENTS.md).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TITLE=""
BODY_FILE=""
BASE=""
DRAFT=0
EXTRA=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --title) TITLE="${2:?}"; shift 2 ;;
    --body-file) BODY_FILE="${2:?}"; shift 2 ;;
    --base) BASE="${2:?}"; shift 2 ;;
    --draft) DRAFT=1; shift ;;
    --) shift; EXTRA+=("$@"); break ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
    *) EXTRA+=("$1"); shift ;;
  esac
done

if [[ -z "$TITLE" ]]; then
  echo "gh_pr_create.sh: --title required" >&2
  exit 1
fi

TMP_BODY=""
cleanup() { [[ -n "$TMP_BODY" && -f "$TMP_BODY" ]] && rm -f "$TMP_BODY"; }
trap cleanup EXIT

if [[ -z "$BODY_FILE" ]]; then
  if [[ -t 0 ]]; then
    echo "gh_pr_create.sh: pass --body-file PATH or pipe markdown body on stdin" >&2
    exit 1
  fi
  TMP_BODY="$(mktemp "${TMPDIR:-/tmp}/toebeans-pr-body.XXXXXX")"
  cat >"$TMP_BODY"
  BODY_FILE="$TMP_BODY"
elif [[ ! -f "$BODY_FILE" ]]; then
  echo "gh_pr_create.sh: body file not found: $BODY_FILE" >&2
  exit 1
fi

if ! grep -q '^## Summary' "$BODY_FILE"; then
  echo "gh_pr_create.sh: body must contain a ## Summary section" >&2
  exit 1
fi
if ! grep -q '^## Test plan' "$BODY_FILE"; then
  echo "gh_pr_create.sh: body must contain a ## Test plan section" >&2
  exit 1
fi

bash "$ROOT/scripts/pr_body_validate.sh" "$BODY_FILE"

ARGS=(pr create --title "$TITLE" --body-file "$BODY_FILE")
[[ -n "$BASE" ]] && ARGS+=(--base "$BASE")
[[ "$DRAFT" -eq 1 ]] && ARGS+=(--draft)
[[ ${#EXTRA[@]} -gt 0 ]] && ARGS+=("${EXTRA[@]}")

gh "${ARGS[@]}"
