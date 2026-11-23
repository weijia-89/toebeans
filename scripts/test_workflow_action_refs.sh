#!/usr/bin/env bash
# Fitness function: every external `uses:` ref in the two CI workflow files must
# resolve to a git tag that really exists in the referenced repository.
#
# A missing tag is a startup failure: GitHub rejects the whole workflow before
# creating any job, so the red build carries no per-job log. The 2026-11-08
# Dependabot bump to actions/dependency-review-action@v5 was exactly this case;
# that repo publishes v5.0.1 and v5.0.0 but no v5 tag.
#
# Parsed per ref:
#   actions/checkout@v7                    -> owner=actions repo=checkout ref=v7
#   gradle/actions/setup-gradle@v5         -> owner=gradle repo=actions ref=v5
#   ./local/action                         -> ignored (local action)
#   docker://alpine:3.20                   -> ignored (container action)
#
# Resolution: by default `gh api --paginate repos/<owner>/<repo>/tags`.
# For offline self-tests, set WORKFLOW_ACTION_REF_TABLE to a text file whose
# lines are "<owner>/<repo> <ref>", for example "actions/checkout v7".
#
# Usage: bash scripts/test_workflow_action_refs.sh [root]
# Paired self-test: scripts/test_test_workflow_action_refs.sh

set -euo pipefail

ROOT="${1:-.}"
REF_TABLE="${WORKFLOW_ACTION_REF_TABLE:-}"

WORKFLOWS=(
  "$ROOT/.github/workflows/ci.yml"
  "$ROOT/.github/workflows/pr-body-lint.yml"
)

for workflow in "${WORKFLOWS[@]}"; do
  if [[ ! -f "$workflow" ]]; then
    echo "test_workflow_action_refs.sh: missing workflow file: $workflow" >&2
    exit 1
  fi
done

if [[ -z "$REF_TABLE" ]] && ! command -v gh >/dev/null 2>&1; then
  echo "test_workflow_action_refs.sh: gh is required unless WORKFLOW_ACTION_REF_TABLE is set" >&2
  exit 1
fi

if [[ -n "$REF_TABLE" && ! -f "$REF_TABLE" ]]; then
  echo "test_workflow_action_refs.sh: ref table not found: $REF_TABLE" >&2
  exit 1
fi

extract_refs() {
  grep -hE '^[[:space:]]*-?[[:space:]]*uses:' "${WORKFLOWS[@]}" \
    | sed -E 's/^[[:space:]]*-?[[:space:]]*uses:[[:space:]]*//' \
    | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//' \
    | grep -vE '^(\./|docker://)' \
    | sort -u
}

refs=$(extract_refs || true)

if [[ -z "$refs" ]]; then
  echo "OK workflow action refs: no external action refs found."
  exit 0
fi

ref_exists() {
  local owner="$1" repo="$2" wanted="$3"
  local tags

  if [[ -n "$REF_TABLE" ]]; then
    grep -qxF "$owner/$repo $wanted" "$REF_TABLE"
    return
  fi

  tags=$(gh api --paginate \
    -H "Accept: application/vnd.github+json" \
    "repos/$owner/$repo/tags?per_page=100" \
    --jq '.[].name' 2>/dev/null) || return 1
  printf '%s\n' "$tags" | grep -qxF "$wanted"
}

errors=0
checked=0

while IFS= read -r ref; do
  [[ -z "$ref" ]] && continue

  spec="${ref%@*}"
  if [[ "$spec" == "$ref" ]]; then
    echo "test_workflow_action_refs.sh: no @ref in action ref '$ref'" >&2
    errors=$((errors + 1))
    continue
  fi

  wanted="${ref##*@}"
  owner="${spec%%/*}"
  rest="${spec#*/}"
  repo="${rest%%/*}"

  if [[ -z "$owner" || -z "$repo" || "$owner" == "$spec" || -z "$wanted" ]]; then
    echo "test_workflow_action_refs.sh: cannot parse action ref '$ref'" >&2
    errors=$((errors + 1))
    continue
  fi

  # Workflow file content is untrusted. Keep parsed values inside the character
  # sets GitHub uses for owner names, repository names, and tags so they cannot
  # alter the gh api URL or the offline table lookup.
  if [[ ! "$owner" =~ ^[A-Za-z0-9_.-]+$ || ! "$repo" =~ ^[A-Za-z0-9_.-]+$ \
     || ! "$wanted" =~ ^[A-Za-z0-9_./-]+$ ]]; then
    echo "test_workflow_action_refs.sh: unsafe characters in action ref '$ref'" >&2
    errors=$((errors + 1))
    continue
  fi

  checked=$((checked + 1))
  if ref_exists "$owner" "$repo" "$wanted"; then
    echo "  OK $owner/$repo@$wanted"
  else
    echo "test_workflow_action_refs.sh: unresolved action ref '$ref' (tag '$wanted' not found in $owner/$repo)" >&2
    errors=$((errors + 1))
  fi
done <<< "$refs"

if [[ "$errors" -gt 0 ]]; then
  echo "test_workflow_action_refs.sh: $errors unresolved action ref(s) out of $checked checked." >&2
  exit 1
fi

echo "OK workflow action refs: $checked ref(s) resolve to existing tags."
