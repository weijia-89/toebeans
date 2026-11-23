#!/usr/bin/env bash
# Inject style-lab context at session start (toebeans).
set -euo pipefail

CTX="toebeans design: LOCAL ONLY. No Magic Patterns, no cloud design MCP. Use docs/style-lab/index.html + DECISIONS.md before Compose UI changes. bash scripts/style_lab_handoff_check.sh"

python3 - <<PY
import json
print(json.dumps({"additional_context": """${CTX}"""}))
PY
