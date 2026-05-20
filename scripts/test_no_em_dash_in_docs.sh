#!/usr/bin/env bash
# Fitness function: no em-dash (U+2014) in toebeans documentation.
#
# Implements the project-wide em-dash iron rule (Wei voice MEMORY[39c0f94f])
# as a mechanical CI + pre-commit gate. The iron rule bans U+2014 from
# hand-authored docs because em-dashes are an LLM-prose tell that leaks
# into Wei's voice when documentation is drafted with AI assistance under
# time pressure. This script makes the ban testable rather than
# reviewer-attention-bound. Closes the rubric gap surfaced by the
# 2026-05-19 meta-v1 calibration recalibration report: em-dashes landed
# in PRs #21, #25, #26 the day before, and the 9-component calibration
# rubric did not see the slip class.
#
# File scope (the prose surface this gate enforces):
#   *.md, *.txt anywhere under the scan root.
#   Excludes:
#     localonly/      (gitignored; no enforcement)
#     .git/           (object store; not source)
#     .gradle/        (Gradle cache)
#     .worktrees/     (sibling git worktrees; their own commits, not ours)
#     build/          (build output)
#     node_modules/   (if it ever appears)
#
# Allow-list (use sparingly, name in calibration):
#   A file may opt out of the gate by placing the literal token
#   EMDASH-ALLOWED anywhere in its first 5 lines. Intended for files that
#   legitimately need to discuss the em-dash character as a topic (e.g. a
#   doc about the gate itself, or a corpus of LLM-style fixtures). Every
#   new addition to the allow-list should be named in the corresponding
#   calibration entry so the exception is visible at review time.
#
# Detection mechanism:
#   Byte-level match against the U+2014 UTF-8 sequence (E2 80 94),
#   locale-independent via LC_ALL=C and bash ANSI-C $'...' quoting. This
#   shape works on both BSD grep (macOS local dev) and GNU grep (Ubuntu
#   CI), which differ on -P (Perl regex) support. Reference shape: the
#   ADR-0018 fitness function at scripts/test_no_pbkdf2_in_backup_after_tbn2.sh
#   for the find + per-file scan structure.
#
# Output:
#   On clean: exit 0, no output.
#   On violation: exit 1, one line per match in "file:linenum:snippet"
#   form where snippet is the first 80 chars of the offending line, plus
#   a footer naming the iron rule and the repair options.
#
# Usage: bash scripts/test_no_em_dash_in_docs.sh [root]
#   root defaults to "." (the repo root). The harness at
#   scripts/test_test_no_em_dash_in_docs.sh exercises the script against
#   tmpdir fixtures.

set -euo pipefail

ROOT="${1:-.}"

# U+2014 EM DASH. UTF-8 bytes: E2 80 94. Use bash ANSI-C quoting so the
# literal bytes land in the variable regardless of source-file locale.
EMDASH=$'\xe2\x80\x94'

violations=0
violation_lines=""

while IFS= read -r -d '' file; do
    # Allow-list check: skip files whose first 5 lines contain the
    # literal token EMDASH-ALLOWED.
    if head -n 5 "$file" 2>/dev/null \
        | LC_ALL=C grep -qF -- 'EMDASH-ALLOWED'; then
        continue
    fi

    # Per-line scan. --binary-files=without-match: silently skip any
    # file grep heuristically classifies as binary (e.g. a PDF that
    # happens to be named with a .md or .txt extension).
    matches=$(LC_ALL=C grep -nF --binary-files=without-match \
        -- "$EMDASH" "$file" 2>/dev/null || true)

    if [ -n "$matches" ]; then
        violations=$((violations + 1))
        # Reformat each grep hit as "file:linenum:snippet" with the
        # snippet truncated to 80 chars so the report stays scannable
        # when a long sentence trips the gate.
        while IFS= read -r match; do
            [ -z "$match" ] && continue
            linenum="${match%%:*}"
            content="${match#*:}"
            snippet="${content:0:80}"
            violation_lines+="${file}:${linenum}:${snippet}"$'\n'
        done <<< "$matches"
    fi
done < <(find "$ROOT" \
    -path '*/localonly' -prune -o \
    -path '*/.git' -prune -o \
    -path '*/.gradle' -prune -o \
    -path '*/.worktrees' -prune -o \
    -path '*/build' -prune -o \
    -path '*/node_modules' -prune -o \
    -type f \( -name '*.md' -o -name '*.txt' \) \
    -print0)

if [ "$violations" -gt 0 ]; then
    printf '%s' "$violation_lines"
    echo
    echo "Total: $violations file(s) contain em-dash (U+2014)."
    echo
    echo "Per the em-dash iron rule (Wei voice MEMORY[39c0f94f]), em-dashes"
    echo "are banned from toebeans documentation. Repair options:"
    echo "  1. Replace with a colon, period, comma, or parens."
    echo "  2. Rewrite the sentence to drop the parenthetical aside."
    echo "  3. If the file legitimately needs to discuss em-dashes as a"
    echo "     topic, add the literal token EMDASH-ALLOWED to the first"
    echo "     5 lines and name the exception in the calibration entry."
    exit 1
fi

echo "OK No-em-dash-in-docs fitness function passed."
exit 0
