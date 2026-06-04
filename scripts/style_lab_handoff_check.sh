#!/usr/bin/env bash
# Remind when Compose/UI sources change without style-lab refresh (toebeans, local-only).
# Usage: bash scripts/style_lab_handoff_check.sh [screen_id|all]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STYLE_LAB="${ROOT}/docs/style-lab/index.html"
MANIFEST="${ROOT}/docs/design-handoff/manifest.yaml"
SCREEN="${1:-all}"

if [[ ! -f "$STYLE_LAB" ]]; then
  echo "style_lab_handoff_check: missing docs/style-lab/index.html" >&2
  exit 1
fi

python3 - <<'PY' "$ROOT" "$STYLE_LAB" "$MANIFEST" "$SCREEN"
import pathlib, re, sys

root = pathlib.Path(sys.argv[1])
style_lab = pathlib.Path(sys.argv[2])
manifest_path = pathlib.Path(sys.argv[3])
screen_filter = sys.argv[4]
lab_mtime = style_lab.stat().st_mtime
issues = []
seen = set()

KOTLIN_ROOTS = [
    root / "androidApp" / "src" / "main" / "kotlin",
    root / "shared" / "src" / "commonMain" / "kotlin",
]

def kotlin_files_under(rel_paths):
    files = []
    for rel in rel_paths:
        p = root / rel
        if p.is_file() and p.suffix == ".kt":
            files.append(p)
        elif p.is_dir():
            files.extend(p.rglob("*.kt"))
    if not files:
        for kr in KOTLIN_ROOTS:
            if kr.is_dir():
                files.extend(kr.rglob("*.kt"))
    return files

def parse_manifest():
    if not manifest_path.is_file():
        return None
    text = manifest_path.read_text()
    blocks = re.split(r"\n  (?=[a-z_]+:\n)", "\n" + text)
    screens = {}
    for block in blocks:
        m = re.match(r"([a-z_]+):", block.strip())
        if not m:
            continue
        sid = m.group(1)
        compose = []
        for line in block.splitlines():
            if line.strip().startswith("- androidApp/") or line.strip().startswith("- shared/"):
                compose.append(line.strip()[2:].strip())
        screens[sid] = compose
    return screens

screens = parse_manifest()
if screen_filter != "all":
    if not screens or screen_filter not in screens:
        print(f"style_lab_handoff_check: unknown screen {screen_filter!r}", file=sys.stderr)
        sys.exit(1)
    scan_sets = {screen_filter: screens[screen_filter]}
elif screens:
    scan_sets = screens
else:
    scan_sets = {"all": []}

for sid, compose_paths in scan_sets.items():
    for f in kotlin_files_under(compose_paths):
        key = str(f.relative_to(root))
        if key in seen:
            continue
        seen.add(key)
        if f.stat().st_mtime > lab_mtime + 1:
            issues.append(f"{sid}: {key} newer than docs/style-lab/index.html")

if issues:
    print("Style-lab handoff — update docs/style-lab/ and DECISIONS.md before shipping UI.", file=sys.stderr)
    for i in issues:
        print(f"  • {i}", file=sys.stderr)
    print("  open docs/style-lab/index.html", file=sys.stderr)
    sys.exit(2)

print("style_lab_handoff_check: ok")
PY
