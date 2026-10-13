#!/usr/bin/env bash
# Fitness function: a pull request test plan that includes a manual case
# must add that case to docs/manual-qa/cases/ and link it from
# docs/manual-qa.md in the same change.
#
# A manual case is a test-plan block that has a Steps heading and an
# Expected result heading, when at least one step is not an automated
# command (Gradle, bash scripts/, git, cargo, npm, flutter, and similar).
# Automated-only blocks are not manual cases. An unchecked box still counts.
#
# The block must name the case file:
#   Catalog: docs/manual-qa/cases/<slug>.md
#
# With no pr-body argument, the script only checks the catalog on disk:
# every case file has ## Steps and ## Expected result, every case file is
# linked from docs/manual-qa.md, and every such index link resolves.
#
# Usage: bash scripts/test_manual_qa_catalog.sh [root] [pr-body]
#   root defaults to "."
# The paired self-test is scripts/test_test_manual_qa_catalog.sh.

set -euo pipefail

ROOT="${1:-.}"
BODY="${2:-}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "test_manual_qa_catalog.sh: python3 is required" >&2
  exit 1
fi

exec python3 - "$ROOT" "$BODY" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
body_path = sys.argv[2] if len(sys.argv) > 2 else ""

index_path = root / "docs" / "manual-qa.md"
cases_dir = root / "docs" / "manual-qa" / "cases"


def die(msg: str) -> None:
    print(f"test_manual_qa_catalog.sh: {msg}", file=sys.stderr)
    sys.exit(1)


if not index_path.is_file():
    die(f"missing {index_path}")

index_text = index_path.read_text(encoding="utf-8")


def is_linked(base: str) -> bool:
    needles = (
        f"](manual-qa/cases/{base})",
        f"](docs/manual-qa/cases/{base})",
        f"](cases/{base})",
    )
    return any(needle in index_text for needle in needles)


if cases_dir.is_dir():
    for path in sorted(cases_dir.glob("*.md")):
        text = path.read_text(encoding="utf-8")
        if not re.search(r"^## Steps\s*$", text, re.M):
            die(f"case file missing Steps heading: {path.name}")
        if not re.search(r"^## Expected result\s*$", text, re.M):
            die(f"case file missing Expected result heading: {path.name}")
        if not is_linked(path.name):
            die(f"case file not linked from docs/manual-qa.md: {path.name}")

link_re = re.compile(
    r"\]\((?:docs/)?(?:manual-qa/)?cases/([a-z0-9]+(?:-[a-z0-9]+)*)\.md\)"
)
for match in link_re.finditer(index_text):
    slug = match.group(1)
    if not (cases_dir / f"{slug}.md").is_file():
        die(f"index link has no case file: {slug}.md")

if not body_path:
    print("OK manual QA catalog fitness function passed.")
    sys.exit(0)

body_file = Path(body_path)
if not body_file.is_file():
    die(f"pr body not found: {body_path}")

raw = body_file.read_text(encoding="utf-8")
raw = raw.replace("\r\n", "\n").replace("\r", "\n")
marker = "CURSOR_AGENT_PR_BODY_END"
if marker in raw:
    raw = raw.split(marker, 1)[0]

plan_match = re.search(r"^## Test plan\s*$", raw, re.M)
if not plan_match:
    print("OK manual QA catalog fitness function passed.")
    sys.exit(0)

rest = raw[plan_match.end() :]
end = re.search(r"^## ", rest, re.M)
plan = rest[: end.start()] if end else rest

defenced = []
in_fence = False
for line in plan.splitlines():
    if line.startswith("```"):
        in_fence = not in_fence
        continue
    if not in_fence:
        defenced.append(line)
plan = "\n".join(defenced)

blocks: list[str] = []
current: list[str] = []
in_heading = False


def flush() -> None:
    if any(line.strip() for line in current):
        blocks.append("\n".join(current))


for line in plan.splitlines():
    if re.match(r"^### ", line):
        flush()
        current = [line]
        in_heading = True
    elif re.match(r"^- \[[ xX]\]", line) and not in_heading:
        flush()
        current = [line]
        in_heading = False
    else:
        current.append(line)
flush()

steps_header = re.compile(
    r"^\s*(?:#{1,6}\s+)?(?:\*\*)?Steps:(?:\*\*)?\s*$",
    re.I,
)
expected_header = re.compile(
    r"^\s*(?:#{1,6}\s+)?(?:\*\*)?Expected(?:\s+result)?:(?:\*\*)?\s*$",
    re.I,
)
inline_re = re.compile(
    r"Steps:\s*(.*?)\s*Expected(?:\s+result):\s*\S",
    re.I,
)
steps_inline_value = re.compile(
    r"^\s*(?:#{1,6}\s+)?(?:\*\*)?Steps:\s+(\S.*?)\s*$",
    re.I,
)
catalog_re = re.compile(
    r"(?:docs/)?manual-qa/cases/([a-z0-9]+(?:-[a-z0-9]+)*)\.md"
)
auto_re = re.compile(
    r"^(?:(?:cd\s+\S+\s+&&\s+)*)"
    r"(?:\./gradlew|gradlew\b|bash\s+scripts/|sh\s+scripts/|"
    r"git\s|cargo\s|npm\s|pnpm\s|yarn\s|pytest\b|flutter\s|\$)"
)


def strip_step(text: str) -> str:
    text = text.strip()
    text = re.sub(r"^([-*]|\d+[.)])\s+", "", text)
    text = re.sub(r"^[-*]\s*\[[ xX]\]\s+", "", text)
    text = text.replace("`", "")
    return text.strip()


def has_expected(block: str) -> bool:
    for line in block.splitlines():
        if expected_header.match(line):
            return True
        if re.search(r"Expected(?:\s+result):", line, re.I):
            return True
    return False


def step_lines(block: str) -> list[str] | None:
    lines = block.splitlines()
    for line in lines:
        inline = inline_re.search(line)
        if inline:
            return [inline.group(1)]
    start = None
    for index, line in enumerate(lines):
        if steps_header.match(line) or steps_inline_value.match(line):
            start = index
            break
    if start is None or not has_expected(block):
        return None
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if expected_header.match(lines[index]):
            end = index
            break
    collected: list[str] = []
    header_value = steps_inline_value.match(lines[start])
    if header_value and not re.search(r"Expected(?:\s+result):", lines[start], re.I):
        collected.append(header_value.group(1))
    for line in lines[start + 1 : end]:
        if line.strip():
            collected.append(line)
    return collected


def is_manual(block: str) -> bool:
    steps = step_lines(block)
    if steps is None:
        return False
    nonempty = [strip_step(step) for step in steps]
    nonempty = [step for step in nonempty if step]
    if nonempty and all(auto_re.match(step) for step in nonempty):
        return False
    return True


for block in blocks:
    if not is_manual(block):
        continue
    slugs = catalog_re.findall(block)
    title = next((line.strip() for line in block.splitlines() if line.strip()), "(untitled)")
    if not slugs:
        die(f"manual case missing from catalog: {title}")
    for slug in slugs:
        case_path = cases_dir / f"{slug}.md"
        if not case_path.is_file():
            die(f"manual case missing from catalog: docs/manual-qa/cases/{slug}.md")
        if not is_linked(f"{slug}.md"):
            die(f"case file not linked from docs/manual-qa.md: {slug}.md")

print("OK manual QA catalog fitness function passed.")
PY
