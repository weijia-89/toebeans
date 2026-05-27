#!/usr/bin/env bash
# Self-test for ci-trainer-pr-review-gate.sh (fixture mode, no gh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATE="$ROOT/scripts/ci-trainer-pr-review-gate.sh"
EXEMPT="$ROOT/scripts/ci-trainer-pr-review-gate-exempt.sh"
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

TRAINER_PR_REVIEW_DISABLE_EXEMPT=1 TRAINER_PR_REVIEW_FIXTURE="$good" \
  bash "$GATE" 99 805402b886b160d3acaf9130cba1363edb1a4d7e feat/style-lab-compose-alignment weijia-89/toebeans \
  && pass "valid marker + head + trainer notes"

bad_ped="$FIXTURE_DIR/bad-pedagogy.json"
echo '[{"body": "<!-- trainer-codereview-toebeans-feat-x -->\n<!-- head=abcdef0 verdict=APPROVE -->\n### Pedagogy\n\n1. x\n"}]' >"$bad_ped"
TRAINER_PR_REVIEW_DISABLE_EXEMPT=1 TRAINER_PR_REVIEW_FIXTURE="$bad_ped" \
  bash "$GATE" 1 abcdef0123456789abcdef0123456789abcdef0 feat/x weijia-89/toebeans \
  && fail "Pedagogy heading should fail" || pass "Pedagogy heading rejected"

stale="$FIXTURE_DIR/stale.json"
echo '[{"body": "<!-- trainer-codereview-toebeans-feat-x -->\n<!-- head=0000000 verdict=APPROVE -->\n### Trainer notes\n1. **Program notes:** x\n2. **Your form:** x\n3. **Next session:** x\n"}]' >"$stale"
TRAINER_PR_REVIEW_DISABLE_EXEMPT=1 TRAINER_PR_REVIEW_FIXTURE="$stale" \
  bash "$GATE" 1 abcdef0123456789abcdef0123456789abcdef0 feat/x weijia-89/toebeans \
  && fail "stale head should fail" || pass "stale head rejected"

empty="$FIXTURE_DIR/empty.json"
echo '[]' >"$empty"
TRAINER_PR_REVIEW_DISABLE_EXEMPT=1 TRAINER_PR_REVIEW_FIXTURE="$empty" \
  bash "$GATE" 1 abcdef0 feat/x weijia-89/toebeans \
  && fail "missing comment should fail" || pass "missing comment rejected"

docs_only="$FIXTURE_DIR/docs-only.txt"
printf '%s\n' 'docs/ROADMAP.md' >"$docs_only"
TRAINER_PR_REVIEW_FILES_FIXTURE="$docs_only" \
  bash "$EXEMPT" weijia-89/toebeans 62 \
  && pass "docs-only ROADMAP exempt"

mixed="$FIXTURE_DIR/mixed.txt"
printf '%s\n' 'docs/ROADMAP.md' 'androidApp/src/main/kotlin/Foo.kt' >"$mixed"
TRAINER_PR_REVIEW_FILES_FIXTURE="$mixed" \
  bash "$EXEMPT" weijia-89/toebeans 62 \
  && fail "mixed docs+code should not be exempt" || pass "mixed PR not exempt"

agents="$FIXTURE_DIR/agents.txt"
printf '%s\n' 'AGENTS.md' >"$agents"
TRAINER_PR_REVIEW_FILES_FIXTURE="$agents" \
  bash "$EXEMPT" weijia-89/toebeans 62 \
  && fail "AGENTS.md alone should not be exempt" || pass "AGENTS.md not exempt"

TRAINER_PR_REVIEW_FILES_FIXTURE="$docs_only" \
  bash "$GATE" 62 b344e9700000000000000000000000000000000 docs-roadmap weijia-89/toebeans \
  && pass "gate skips when docs-only exempt (no comment)"

echo "All ci-trainer-pr-review-gate self-tests passed."
