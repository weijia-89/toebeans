#!/usr/bin/env bash
# toebeans: nudge agent when editing Compose UI — use style-lab, not cloud design tools.
set -euo pipefail

input=$(cat)
python3 - <<'PY' "$input"
import json, sys, re

raw = sys.argv[1]
try:
    data = json.loads(raw)
except json.JSONDecodeError:
    sys.exit(0)

tool = data.get("tool_name") or data.get("toolName") or ""
if tool not in ("Write", "search_replace", "StrReplace", "edit_file"):
    sys.exit(0)

path = (
    data.get("tool_input", {}).get("path")
    or data.get("arguments", {}).get("path")
    or ""
)
if not re.search(r"(androidApp|shared)/src/main/.*\.(kt|kts)$", path):
    sys.exit(0)

msg = (
    "Design handoff (toebeans): local-only — update docs/style-lab/ and "
    "docs/style-lab/DECISIONS.md; no Magic Patterns / Framer cloud for ship path. "
    "Run: bash scripts/style_lab_handoff_check.sh"
)
print(json.dumps({"permission": "allow", "agent_message": msg}))
PY
