#!/usr/bin/env bash
# Self-test for ci-trainer-pr-review-gate.sh (fixture mode, no gh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATE="$ROOT/scripts/ci-trainer-pr-review-gate.sh"
FIXTURE_DIR=$(mktemp -d)
trap 'rm -rf "$FIXTURE_DIR"' EXIT

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; exit 1; }

good="$FIXTURE_DIR/good.json"
cat >"$good" <<'JSON'
[
  {
    "body": "<!-- trainer-codereview-toebeans-feat-style-lab-compose-alignment -->\n<!-- head=805402b verdict=APPROVE round=1 -->\n\n### Trainer notes\n\n1. **Program notes:** test\n2. **Your form:** test\n3. **Next session:** test\n"
  }
]
JSON

TRAINER_PR_REVIEW_FIXTURE="$good" \
  bash "$GATE" 99 805402b886b160d3acaf9130cba1363edb1a4d7e feat/style-lab-compose-alignment weijia-89/toebeans \
  && pass "valid marker + head + trainer notes"

bad_ped="$FIXTURE_DIR/bad-pedagogy.json"
echo '[{"body": "<!-- trainer-codereview-toebeans-feat-x -->\n<!-- head=abcdef0 verdict=APPROVE -->\n### Pedagogy\n\n1. x\n"}]' >"$bad_ped"
TRAINER_PR_REVIEW_FIXTURE="$bad_ped" \
  bash "$GATE" 1 abcdef0123456789abcdef0123456789abcdef0 feat/x weijia-89/toebeans \
  && fail "Pedagogy heading should fail" || pass "Pedagogy heading rejected"

stale="$FIXTURE_DIR/stale.json"
echo '[{"body": "<!-- trainer-codereview-toebeans-feat-x -->\n<!-- head=0000000 verdict=APPROVE -->\n### Trainer notes\n1. **Program notes:** x\n2. **Your form:** x\n3. **Next session:** x\n"}]' >"$stale"
TRAINER_PR_REVIEW_FIXTURE="$stale" \
  bash "$GATE" 1 abcdef0123456789abcdef0123456789abcdef0 feat/x weijia-89/toebeans \
  && fail "stale head should fail" || pass "stale head rejected"

empty="$FIXTURE_DIR/empty.json"
echo '[]' >"$empty"
TRAINER_PR_REVIEW_FIXTURE="$empty" \
  bash "$GATE" 1 abcdef0 feat/x weijia-89/toebeans \
  && fail "missing comment should fail" || pass "missing comment rejected"

echo "All ci-trainer-pr-review-gate self-tests passed."
