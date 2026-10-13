#!/usr/bin/env bash
# Fixture self-test for scripts/test_manual_qa_catalog.sh.
#
# Stages a tiny catalog under a tmpdir and asserts:
#   - automated Gradle / script lines do not require a case file
#   - a manual case (steps plus an expected result) fails when the case
#     file is missing or is not linked from docs/manual-qa.md
#   - an unchecked device step still requires a case file
#   - a linked case file passes, including when the box is unchecked
#
# Usage: bash scripts/test_test_manual_qa_catalog.sh [repo-root]

set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_manual_qa_catalog.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "FAIL fitness-function script not found at $SCRIPT"
  exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

write_catalog() {
  local link_known="${1:-yes}"
  local include_expected="${2:-yes}"
  local extra_index="${3:-}"
  rm -rf "$tmpdir/docs"
  mkdir -p "$tmpdir/docs/manual-qa/cases"
  if [[ "$include_expected" == yes ]]; then
    cat > "$tmpdir/docs/manual-qa/cases/known-case.md" <<'MD'
# Known case

## Steps

1. Open Today.

## Expected result

The tab loads.
MD
  else
    cat > "$tmpdir/docs/manual-qa/cases/known-case.md" <<'MD'
# Known case

## Steps

1. Open Today.
MD
  fi
  {
    echo "# Manual QA"
    echo
    if [[ "$link_known" == yes ]]; then
      echo "- [Known case](manual-qa/cases/known-case.md)"
    fi
    if [[ -n "$extra_index" ]]; then
      printf '%s\n' "$extra_index"
    fi
  } > "$tmpdir/docs/manual-qa.md"
}

write_body() {
  cat > "$tmpdir/pr-body.md"
}

expect_pass() {
  local name="$1"
  if ! bash "$SCRIPT" "$tmpdir" "$tmpdir/pr-body.md" >/dev/null 2>&1; then
    echo "FAIL $name: expected exit 0"
    FAILED=$((FAILED + 1))
  fi
}

expect_fail() {
  local name="$1"
  local needle="$2"
  local report
  if bash "$SCRIPT" "$tmpdir" "$tmpdir/pr-body.md" >/dev/null 2>&1; then
    echo "FAIL $name: expected nonzero exit"
    FAILED=$((FAILED + 1))
    return
  fi
  report=$(bash "$SCRIPT" "$tmpdir" "$tmpdir/pr-body.md" 2>&1 || true)
  if ! grep -qF "$needle" <<< "$report"; then
    echo "FAIL $name: report did not contain: $needle"
    echo "$report"
    FAILED=$((FAILED + 1))
  fi
}

expect_pass_catalog_only() {
  local name="$1"
  if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL $name: expected exit 0"
    FAILED=$((FAILED + 1))
  fi
}

expect_fail_catalog_only() {
  local name="$1"
  local needle="$2"
  local report
  if bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL $name: expected nonzero exit"
    FAILED=$((FAILED + 1))
    return
  fi
  report=$(bash "$SCRIPT" "$tmpdir" 2>&1 || true)
  if ! grep -qF "$needle" <<< "$report"; then
    echo "FAIL $name: report did not contain: $needle"
    echo "$report"
    FAILED=$((FAILED + 1))
  fi
}

# 1. Valid catalog, no pull request body.
write_catalog
expect_pass_catalog_only "test 1 catalog only"

# 2. Automated checklist lines are not manual cases.
write_catalog
write_body <<'MD'
## Summary

Catalog rule.

## Test plan

- [x] `./gradlew :shared:jvmTest`
- [x] `bash scripts/test_no_em_dash_in_docs.sh .`
MD
expect_pass "test 2 gradle checklist"

# 3. Steps plus expected result that are only Gradle.
write_catalog
write_body <<'MD'
## Summary

Unit tests.

## Test plan

### Unit tests

Steps:
1. `./gradlew :shared:jvmTest`

Expected result:
The task exits 0.
MD
expect_pass "test 3 automated steps block"

# 4. Manual case whose file exists and is linked.
write_catalog
write_body <<'MD'
## Summary

Device check.

## Test plan

### Today loads

Catalog: docs/manual-qa/cases/known-case.md

Steps:
1. Open Today on a phone.

Expected result:
The tab loads.
MD
expect_pass "test 4 linked manual case"

# 5. Manual case with no case file.
write_catalog
write_body <<'MD'
## Summary

Missing case.

## Test plan

### Banner

Catalog: docs/manual-qa/cases/other-case.md

Steps:
1. Open Today on a phone with alarms off.

Expected result:
A banner is visible.
MD
expect_fail "test 5 missing file" "docs/manual-qa/cases/other-case.md"

# 6. Case file exists but the index does not link it.
write_catalog
cat > "$tmpdir/docs/manual-qa/cases/orphan.md" <<'MD'
# Orphan

## Steps

1. Open Today.

## Expected result

The tab loads.
MD
write_body <<'MD'
## Summary

Unlinked.

## Test plan

- [x] `./gradlew :shared:jvmTest`
MD
expect_fail "test 6 unlinked file" "case file not linked from docs/manual-qa.md: orphan.md"

# 7. Unchecked device steps still pass when the case file is linked.
write_catalog
write_body <<'MD'
## Summary

Not run yet.

## Test plan

- [ ] Catalog: docs/manual-qa/cases/known-case.md
  Steps:
  1. Open Today on a phone.
  Expected result:
  The tab loads.
MD
expect_pass "test 7 unchecked linked case"

# 8. Unchecked device steps fail when no case file is named.
write_catalog
write_body <<'MD'
## Summary

Unchecked and missing.

## Test plan

- [ ] Steps: open Today on a phone. Expected result: the tab loads.
MD
expect_fail "test 8 unchecked missing" "manual case missing from catalog"

# 9. Steps without an expected result are not a manual case.
write_catalog
write_body <<'MD'
## Summary

Half.

## Test plan

### Half

Steps:
1. Open Today on a phone.
MD
expect_pass "test 9 steps only"

# 10. Expected result without steps is not a manual case.
write_catalog
write_body <<'MD'
## Summary

Half.

## Test plan

### Half

Expected result:
The tab loads.
MD
expect_pass "test 10 expected only"

# 11. Index link with no case file.
write_catalog yes yes "- [Ghost](manual-qa/cases/ghost.md)"
write_body <<'MD'
## Summary

Ghost link.

## Test plan

- [x] `./gradlew :shared:jvmTest`
MD
expect_fail "test 11 ghost index link" "index link has no case file: ghost.md"

# 12. Case file missing the Expected result heading.
write_catalog yes no
write_body <<'MD'
## Summary

Bad case file.

## Test plan

- [x] `git diff --check`
MD
expect_fail "test 12 missing heading" "case file missing Expected result heading: known-case.md"

# 13. A device step next to Gradle is still a manual case.
write_catalog
write_body <<'MD'
## Summary

Mixed.

## Test plan

### Boot

Steps:
1. `./gradlew :androidApp:installDebug`
2. Open Today on the device.

Expected result:
The Today tab loads.
MD
expect_fail "test 13 mixed device step" "manual case missing from catalog"

# 14. The same mixed block passes when the catalog file is linked.
write_catalog
write_body <<'MD'
## Summary

Mixed linked.

## Test plan

### Boot

Catalog: docs/manual-qa/cases/known-case.md

Steps:
1. `./gradlew :androidApp:installDebug`
2. Open Today on the device.

Expected result:
The Today tab loads.
MD
expect_pass "test 14 mixed linked"

# 15. A fenced example is not a manual case.
write_catalog
write_body <<'MD'
## Summary

Fence.

## Test plan

- [x] `bash scripts/test_manual_qa_catalog.sh .`

```
### Example only

Steps:
1. Open Today on a phone.

Expected result:
The tab loads.
```
MD
expect_pass "test 15 fenced example"

# 16. Text after the agent footer is not part of the test plan.
write_catalog
write_body <<'MD'
## Summary

Footer.

## Test plan

- [x] `./gradlew :shared:jvmTest`

<!-- CURSOR_AGENT_PR_BODY_END -->

### Not part of the plan

Steps:
1. Open Today on a phone.

Expected result:
The tab loads.
MD
expect_pass "test 16 footer ignored"

if [[ "$FAILED" -eq 0 ]]; then
  echo "OK manual QA catalog fitness-function self-test passed."
  exit 0
fi

echo "FAIL $FAILED manual QA catalog self-test assertion(s)."
exit 1
