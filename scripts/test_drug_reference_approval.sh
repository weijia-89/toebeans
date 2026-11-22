#!/usr/bin/env bash
# Fitness function: every shipped drug-reference entry carries a complete,
# approved provenance record.
#
# Backs ADR-0019 Amendment 2026-11-16. Content summarised or copied from a
# publicly accessible publisher is permitted only when a human read the source
# and approved the text BEFORE it entered the app. This gate makes that a
# mechanical check instead of a promise.
#
# Scanned path: <root>/data/drug-reference/entries/*.json
# One file per medication reference item. Text and approval travel together.
#
# Required shape:
#   medication  non-empty string
#   kind        "summary" or "verbatim"
#   text        non-empty string
#   source      { publisher, url, retrieved }   url is http(s), retrieved YYYY-MM-DD
#   approval    { approver, date, decision, rationale }  date YYYY-MM-DD,
#               decision must be "approved"
#
# Additional rule for kind "summary": the text must not carry a dose number
# (a number followed by a dose unit). Dose amounts stay with the vet's
# prescription and never enter a summary.
#
# No entries directory, or an empty one, passes: nothing is shipped yet.
#
# Usage: bash scripts/test_drug_reference_approval.sh [root]
# The paired self-test is scripts/test_test_drug_reference_approval.sh.

set -euo pipefail

ROOT="${1:-.}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "test_drug_reference_approval.sh: python3 is required" >&2
  exit 1
fi

exec python3 - "$ROOT" <<'PY'
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
entries_dir = root / "data" / "drug-reference" / "entries"

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
URL_RE = re.compile(r"^https?://\S+$")
DOSE_RE = re.compile(
    r"\b\d+(?:[.,]\d+)?\s*"
    r"(?:mg|mcg|µg|ug|gm|g|kg|ml|cc|tsp|tbsp|"
    r"tablet|tablets|tab|tabs|capsule|capsules|cap|caps|"
    r"iu|unit|units|drop|drops)\b",
    re.I,
)

errors: list[str] = []


def fail(name: str, message: str) -> None:
    errors.append(f"{name}: {message}")


def require_text(name: str, block: dict, key: str, path: str) -> str:
    value = block.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(name, f"missing {path}")
        return ""
    return value


if entries_dir.is_dir():
    for path in sorted(entries_dir.glob("*.json")):
        name = path.name
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            fail(name, f"is not valid JSON ({exc})")
            continue

        if not isinstance(data, dict):
            fail(name, "top level is not an object")
            continue

        require_text(name, data, "medication", "medication")
        text = require_text(name, data, "text", "text")

        kind = data.get("kind")
        if kind not in ("summary", "verbatim"):
            fail(name, "kind is not summary or verbatim")

        source = data.get("source")
        if not isinstance(source, dict):
            fail(name, "missing source block")
        else:
            require_text(name, source, "publisher", "source.publisher")
            url = require_text(name, source, "url", "source.url")
            if url and not URL_RE.match(url):
                fail(name, "source.url is not an http(s) URL")
            retrieved = require_text(name, source, "retrieved", "source.retrieved")
            if retrieved and not DATE_RE.match(retrieved):
                fail(name, "source.retrieved is not YYYY-MM-DD")

        approval = data.get("approval")
        if not isinstance(approval, dict):
            fail(name, "missing approval block")
        else:
            require_text(name, approval, "approver", "approval.approver")
            require_text(name, approval, "rationale", "approval.rationale")
            approved_on = require_text(name, approval, "date", "approval.date")
            if approved_on and not DATE_RE.match(approved_on):
                fail(name, "approval.date is not YYYY-MM-DD")
            decision = approval.get("decision")
            if decision is not None and decision != "approved":
                fail(name, "decision is not approved")

        if kind == "summary" and text and DOSE_RE.search(text):
            fail(name, "summary contains a dose number")

if errors:
    for error in errors:
        print(f"test_drug_reference_approval.sh: {error}", file=sys.stderr)
    print(
        f"test_drug_reference_approval.sh: {len(errors)} violation(s) "
        "in data/drug-reference/entries",
        file=sys.stderr,
    )
    sys.exit(1)

print("OK drug-reference approval fitness function passed.")
PY
