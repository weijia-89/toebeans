#!/usr/bin/env bash
# CI: open PRs must have a canonical trainer (or SDK) code-review comment on the
# current HEAD with ### Trainer notes (forbids ### Pedagogy). Survives agents skipping rules.
#
# Usage:
#   bash scripts/ci-trainer-pr-review-gate.sh <pr_num> <head_sha> <branch> <gh_repo>
#
# Fixture self-test (no network):
#   TRAINER_PR_REVIEW_FIXTURE=path/to/comments.json \
#     bash scripts/ci-trainer-pr-review-gate.sh 1 abcdef0 feat/x owner/repo
#
# Exit 0 = gate pass; 1 = missing/stale review; 2 = usage error.

set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "usage: $0 <pr_num> <head_sha> <branch> <gh_repo>" >&2
  exit 2
fi

PR_NUM=$1
HEAD_SHA=$2
BRANCH=$3
GH_REPO=$4
BRANCH_SLUG=${BRANCH//\//-}
HEAD_SHORT=${HEAD_SHA:0:7}

if [[ -n "${TRAINER_PR_REVIEW_FIXTURE:-}" ]]; then
  COMMENTS_JSON=$(cat "$TRAINER_PR_REVIEW_FIXTURE")
else
  if ! command -v gh >/dev/null 2>&1; then
    echo "FAIL  gh CLI required for trainer PR review gate" >&2
    exit 1
  fi
  COMMENTS_JSON=$(gh api "repos/${GH_REPO}/issues/${PR_NUM}/comments" --paginate 2>/dev/null || echo "[]")
fi

export COMMENTS_JSON HEAD_SHORT BRANCH_SLUG GH_REPO PR_NUM
python3 - <<'PY'
import json, os, re, sys

comments = json.loads(os.environ["COMMENTS_JSON"])
head_short = os.environ["HEAD_SHORT"].lower()
branch_slug = os.environ["BRANCH_SLUG"]
repo = os.environ["GH_REPO"].split("/")[-1]
pr = os.environ["PR_NUM"]

marker_res = [
    re.compile(rf"trainer-codereview-{re.escape(repo)}-{re.escape(branch_slug)}", re.I),
    re.compile(rf"sdk-codereview-{re.escape(repo)}-{re.escape(branch_slug)}", re.I),
    re.compile(rf"trainer-codereview-{re.escape(repo)}", re.I),
    re.compile(rf"sdk-codereview-{re.escape(repo)}", re.I),
]
meta_head = re.compile(r"head=([0-9a-f]{7,40})", re.I)
meta_verdict = re.compile(r"verdict=(APPROVE|REQUEST_CHANGES|BLOCK)", re.I)
trainer_notes = re.compile(r"^###\s+Trainer notes\b", re.M | re.I)
forbidden_pedagogy = re.compile(r"^###\s+Pedagogy", re.M | re.I)
required_note_labels = ("Program notes", "Your form", "Next session")

def matches_marker(body: str) -> bool:
    return any(p.search(body) for p in marker_res)

candidates = [c for c in comments if matches_marker(c.get("body") or "")]

if not candidates:
    print(f"FAIL  PR #{pr}: no trainer/sdk codereview marker for branch {branch_slug}")
    print(f"      Required: <!-- trainer-codereview-{repo}-{branch_slug} -->")
    print(f"      Post review per trainer.skill/references/trainer-github-pr-commentary.md")
    sys.exit(1)

# Prefer comment whose meta head= matches current PR HEAD (short or full).
best = None
for c in candidates:
    body = c.get("body") or ""
    m = meta_head.search(body)
    if m and m.group(1).lower().startswith(head_short):
        best = c
        break

if best is None:
    got = []
    for c in candidates:
        m = meta_head.search(c.get("body") or "")
        got.append(m.group(1) if m else "?")
    print(f"FAIL  PR #{pr}: review comment stale (need head={head_short}, found {got})")
    print("      PATCH the canonical comment after push (trainer-github-pr-commentary.md)")
    sys.exit(1)

body = best.get("body") or ""
if forbidden_pedagogy.search(body):
    print(f"FAIL  PR #{pr}: review uses forbidden '### Pedagogy' heading; use '### Trainer notes'")
    sys.exit(1)
if not trainer_notes.search(body):
    print(f"FAIL  PR #{pr}: review comment missing '### Trainer notes' section")
    sys.exit(1)
missing = [label for label in required_note_labels if label not in body]
if missing:
    print(f"FAIL  PR #{pr}: Trainer notes missing labels: {', '.join(missing)}")
    sys.exit(1)
if not meta_verdict.search(body):
    print(f"FAIL  PR #{pr}: review comment missing verdict= in meta line")
    sys.exit(1)

verdict = meta_verdict.search(body).group(1)
print(f"PASS  trainer PR review gate: PR #{pr} head={head_short} verdict={verdict}")
PY
