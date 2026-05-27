#!/usr/bin/env bash
# Validate a PR description markdown file before gh pr create/edit.
# Usage: scripts/pr_body_validate.sh PATH
set -euo pipefail

BODY_FILE="${1:?path to pr body markdown}"
MAX_BODY_BYTES=16384
MAX_LINE_LEN=280

die() {
  echo "pr_body_validate.sh: $*" >&2
  exit 1
}

[[ -f "$BODY_FILE" ]] || die "file not found: $BODY_FILE"

bytes=$(wc -c <"$BODY_FILE" | tr -d ' ')
(( bytes <= MAX_BODY_BYTES )) || die "body too large (${bytes} bytes; max ${MAX_BODY_BYTES})"

grep -q '^## Summary' "$BODY_FILE" || die 'missing "## Summary" section'
grep -q '^## Test plan' "$BODY_FILE" || die 'missing "## Test plan" section'

# Literal backslash-n (agent used "\\n" inside --body strings)
if grep -q '\\n' "$BODY_FILE"; then
  die 'literal \\n sequences — write real newlines in a body file instead'
fi

# Captured build/test log heuristics (buds verify_buds.sh dump pattern)
LOG_MARKERS=(
  'BUILD SUCCESSFUL'
  'json_serializable on'
  'Resolving dependencies'
  'Got dependencies!'
  'All tests passed!'
  'Running Gradle task'
  'Built with build_runner'
  'Analyzing app'
)
for m in "${LOG_MARKERS[@]}"; do
  if grep -qF "$m" "$BODY_FILE"; then
    die "looks like captured command output (found: $m)"
  fi
done
if grep -Eq '^[0-9]{2}:[0-9]{2} \+[0-9]+:' "$BODY_FILE"; then
  die 'looks like flutter test timing output in the body'
fi

line_num=0
while IFS= read -r line || [[ -n "$line" ]]; do
  line_num=$((line_num + 1))
  stripped="${line#"${line%%[![:space:]]*}"}"

  [[ -z "$stripped" ]] && continue
  [[ "$stripped" == \#\#* ]] && continue

  if [[ ${#stripped} -gt $MAX_LINE_LEN ]]; then
    die "line ${line_num} too long (${#stripped} chars; max ${MAX_LINE_LEN})"
  fi

  # Checklist command references are OK; bare shell lines at column 0 are not.
  if [[ "$stripped" =~ ^-[[:space:]] ]]; then
    if [[ "$stripped" =~ ^-[[:space:]]\[[[:space:]]?[xX]?[[:space:]]?\] ]]; then
      continue
    fi
    if [[ "$stripped" =~ ^-[[:space:]] ]]; then
      continue
    fi
  fi

  if [[ "$stripped" =~ ^(\$|./gradlew|flutter[[:space:]]|bash[[:space:]]+scripts/|\./scripts/) ]]; then
    die "line ${line_num}: raw shell command — use a checklist item (- [ ] \`cmd\`) instead"
  fi
done <"$BODY_FILE"
