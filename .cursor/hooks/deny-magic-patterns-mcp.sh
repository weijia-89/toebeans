#!/usr/bin/env bash
# toebeans: block Magic Patterns MCP (third-party cloud).
set -euo pipefail

input=$(cat)
python3 - <<'PY' "$input"
import json, sys

raw = sys.argv[1]
try:
    data = json.loads(raw)
except json.JSONDecodeError:
    sys.exit(0)

server = (
    data.get("server_name")
    or data.get("serverName")
    or data.get("mcp_server")
    or ""
)
if "magic-patterns" in str(server).lower() or "magicpatterns" in str(server).lower():
    print(json.dumps({
        "permission": "deny",
        "user_message": "toebeans: Magic Patterns MCP is disabled (local-only / no third-party cloud). Use docs/style-lab/ instead.",
        "agent_message": "Refuse magic-patterns MCP in toebeans. Use docs/style-lab/index.html and Compose tokens.",
    }))
    sys.exit(0)
print(json.dumps({"permission": "allow"}))
PY
