#!/usr/bin/env bash
# Fixture self-test for scripts/test_drug_reference_approval.sh.
#
# Stages tiny drug-reference entries under a tmpdir and asserts:
#   - no entries directory, or an empty one, passes (nothing shipped yet)
#   - an approved verbatim entry passes
#   - an approved summary with no dose numbers passes
#   - a missing approval block fails
#   - a pending decision fails
#   - a summary carrying a dose number fails
#   - a missing source URL fails
#   - a malformed date fails
#   - an unknown content kind fails
#   - the old data/drug-reference/entries path is NOT scanned (one path, no drift)
#
# Usage: bash scripts/test_test_drug_reference_approval.sh [repo-root]

set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_drug_reference_approval.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "FAIL fitness-function script not found at $SCRIPT"
  exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

entries="$tmpdir/androidApp/src/main/assets/drug-reference/entries"

reset_entries() {
  rm -rf "$tmpdir/androidApp"
  mkdir -p "$entries"
}

expect_pass() {
  local name="$1"
  if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "FAIL $name: expected exit 0"
    bash "$SCRIPT" "$tmpdir" 2>&1 | sed 's/^/    /' || true
    FAILED=$((FAILED + 1))
  fi
}

expect_fail() {
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
    echo "$report" | sed 's/^/    /'
    FAILED=$((FAILED + 1))
  fi
}

# 1. Nothing shipped yet: no entries directory at all.
rm -rf "$tmpdir/androidApp"
expect_pass "test 1 no entries directory"

# 2. Nothing shipped yet: empty entries directory.
reset_entries
expect_pass "test 2 empty entries directory"

# 3. Approved verbatim entry.
reset_entries
cat > "$entries/gabapentin.json" <<'JSON'
{
  "medication": "Gabapentin",
  "kind": "verbatim",
  "text": "Text reproduced word for word from the publisher.",
  "source": {
    "publisher": "Merck Veterinary Manual",
    "url": "https://www.merckvetmanual.com/example",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "approved",
    "rationale": "Read the source page in full; wording matches."
  }
}
JSON
expect_pass "test 3 approved verbatim"

# 4. Approved summary with no dose numbers.
reset_entries
cat > "$entries/methimazole.json" <<'JSON'
{
  "medication": "Methimazole",
  "kind": "summary",
  "text": "Used to manage an overactive thyroid in cats. Usually given with food.",
  "source": {
    "publisher": "Manufacturer label",
    "url": "https://example.com/label",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "approved",
    "rationale": "Summary avoids dosing; feeding note matches the label."
  }
}
JSON
expect_pass "test 4 approved summary without dose"

# 5. Approval block missing entirely.
reset_entries
cat > "$entries/no-approval.json" <<'JSON'
{
  "medication": "Amoxicillin",
  "kind": "verbatim",
  "text": "Copied text.",
  "source": {
    "publisher": "Manufacturer label",
    "url": "https://example.com/label",
    "retrieved": "2026-11-16"
  }
}
JSON
expect_fail "test 5 missing approval" "no-approval.json: missing approval block"

# 6. Decision still pending.
reset_entries
cat > "$entries/pending.json" <<'JSON'
{
  "medication": "Amoxicillin",
  "kind": "verbatim",
  "text": "Copied text.",
  "source": {
    "publisher": "Manufacturer label",
    "url": "https://example.com/label",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "pending",
    "rationale": "Not read yet."
  }
}
JSON
expect_fail "test 6 pending decision" "pending.json: decision is not approved"

# 7. Summary carrying a dose number.
reset_entries
cat > "$entries/dose.json" <<'JSON'
{
  "medication": "Prednisone",
  "kind": "summary",
  "text": "Give 2.5 mg twice daily with food.",
  "source": {
    "publisher": "Merck Veterinary Manual",
    "url": "https://www.merckvetmanual.com/example",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "approved",
    "rationale": "Looks fine."
  }
}
JSON
expect_fail "test 7 dose number in summary" "dose.json: summary contains a dose number"

# 8. Source URL missing.
reset_entries
cat > "$entries/no-url.json" <<'JSON'
{
  "medication": "Amoxicillin",
  "kind": "verbatim",
  "text": "Copied text.",
  "source": {
    "publisher": "Manufacturer label",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "approved",
    "rationale": "Fine."
  }
}
JSON
expect_fail "test 8 missing source url" "no-url.json: missing source.url"

# 9. Malformed approval date.
reset_entries
cat > "$entries/bad-date.json" <<'JSON'
{
  "medication": "Amoxicillin",
  "kind": "verbatim",
  "text": "Copied text.",
  "source": {
    "publisher": "Manufacturer label",
    "url": "https://example.com/label",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "16 Nov 2026",
    "decision": "approved",
    "rationale": "Fine."
  }
}
JSON
expect_fail "test 9 malformed date" "bad-date.json: approval.date is not YYYY-MM-DD"

# 10. Unknown content kind.
reset_entries
cat > "$entries/kind.json" <<'JSON'
{
  "medication": "Amoxicillin",
  "kind": "scraped",
  "text": "Copied text.",
  "source": {
    "publisher": "Manufacturer label",
    "url": "https://example.com/label",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "approved",
    "rationale": "Fine."
  }
}
JSON
expect_fail "test 10 unknown kind" "kind.json: kind is not summary or verbatim"

# 11. One path, no drift: a bad entry left in the OLD path must be ignored.
reset_entries
old="$tmpdir/data/drug-reference/entries"
mkdir -p "$old"
cat > "$old/orphan.json" <<'JSON'
{
  "medication": "Orphan",
  "kind": "scraped",
  "text": "Copied text.",
  "source": {
    "publisher": "Manufacturer label",
    "url": "https://example.com/label",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "pending",
    "rationale": "Fine."
  }
}
JSON
expect_pass "test 11 old path is not scanned"

if [[ "$FAILED" -eq 0 ]]; then
  echo "OK drug-reference approval fitness-function self-test passed."
  exit 0
fi

echo "FAIL $FAILED drug-reference approval self-test assertion(s)."
exit 1
