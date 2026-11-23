#!/usr/bin/env bash
# Fixture self-test for scripts/test_workflow_action_refs.sh.
#
# Runs entirely offline. A checked-in known-good ref table stands in for the
# live `gh api .../tags` lookup, so the parser and the failure path are tested
# without network access or GitHub credentials.
#
# Asserts:
#   1. all known-good refs in both workflow files pass
#   2. actions/dependency-review-action@v5 is rejected (no such tag)
#   3. a bad ref in pr-body-lint.yml is still caught (both files are scanned)
#   4. subpath actions such as gradle/actions/setup-gradle@v5 are parsed right
#   5. local ./ and docker:// uses lines are ignored
#   6. a commented uses: line is ignored
#   7. a missing workflow file fails loudly
#
# Usage: bash scripts/test_test_workflow_action_refs.sh [repo-root]

set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_workflow_action_refs.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "FAIL fitness-function script not found at $SCRIPT"
  exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

mkdir -p "$tmpdir/.github/workflows"

# Known-good ref table embedded here so the self-test needs no network.
cat > "$tmpdir/known-good-refs.txt" <<'REFS'
actions/checkout v7
actions/setup-java v5
gradle/actions v5
gitleaks/gitleaks-action v3
actions/dependency-review-action v5.0.1
REFS

write_pr_body_lint() {
  cat > "$tmpdir/.github/workflows/pr-body-lint.yml" <<'YAML'
name: pr-body-lint
on: pull_request
jobs:
  lint:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v7
YAML
}

run_script() {
  WORKFLOW_ACTION_REF_TABLE="$tmpdir/known-good-refs.txt" \
    bash "$SCRIPT" "$tmpdir"
}

expect_pass() {
  local name="$1"
  local output
  if output=$(run_script 2>&1); then
    return
  fi
  echo "FAIL $name: expected exit 0"
  echo "$output" | sed 's/^/    /'
  FAILED=$((FAILED + 1))
}

expect_fail() {
  local name="$1" needle="$2"
  local output
  if output=$(run_script 2>&1); then
    echo "FAIL $name: expected nonzero exit"
    FAILED=$((FAILED + 1))
    return
  fi
  if ! grep -qF "$needle" <<< "$output"; then
    echo "FAIL $name: output did not contain: $needle"
    echo "$output" | sed 's/^/    /'
    FAILED=$((FAILED + 1))
  fi
}

# 1. All known-good refs in both files.
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
      - uses: gitleaks/gitleaks-action@v3
      - uses: actions/dependency-review-action@v5.0.1
YAML
write_pr_body_lint
expect_pass "test 1 known-good refs pass"

# 2. The exact defect this fitness function exists to catch.
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/dependency-review-action@v5
YAML
write_pr_body_lint
expect_fail "test 2 missing v5 tag" \
  "unresolved action ref 'actions/dependency-review-action@v5'"

# 3. A bad ref in the second workflow file must be caught too.
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v7
YAML
cat > "$tmpdir/.github/workflows/pr-body-lint.yml" <<'YAML'
name: pr-body-lint
on: pull_request
jobs:
  lint:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v6
YAML
expect_fail "test 3 second workflow scanned" \
  "unresolved action ref 'actions/checkout@v6'"

# 4. Subpath actions resolve against the owning repository at the same tag.
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      - uses: gradle/actions/setup-gradle@v5
      - uses: gradle/actions/wrapper-validation@v5
YAML
write_pr_body_lint
expect_pass "test 4 subpath actions"

# 5. Local and container actions are out of scope.
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      - uses: ./local/action
      - uses: docker://alpine:3.20
      - uses: actions/checkout@v7
YAML
write_pr_body_lint
expect_pass "test 5 local and docker refs ignored"

# 6. A commented-out uses line is not an active ref.
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      # - uses: actions/checkout@v6
      - uses: actions/checkout@v7
YAML
write_pr_body_lint
expect_pass "test 6 commented uses ignored"

# 7. A missing workflow file fails with a clear message.
rm -f "$tmpdir/.github/workflows/pr-body-lint.yml"
expect_fail "test 7 missing workflow file" "missing workflow file"

# 8. Unsafe characters in a ref are rejected before they reach gh api.
write_pr_body_lint
cat > "$tmpdir/.github/workflows/ci.yml" <<'YAML'
name: ci
on: push
jobs:
  a:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v7?per_page=100
YAML
expect_fail "test 8 unsafe ref characters" "unsafe characters in action ref"

if [[ "$FAILED" -eq 0 ]]; then
  echo "OK workflow action refs fitness-function self-test passed."
  exit 0
fi

echo "FAIL $FAILED workflow action refs self-test assertion(s)."
exit 1
