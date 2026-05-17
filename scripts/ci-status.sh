#!/usr/bin/env bash
# scripts/ci-status.sh — opt-in CI status check for the toebeans solo workflow.
#
# Closes the F1-corrected hazard from the 2026-05-17 adversarial review:
# a previous broken-on-main commit (c7f5eff) shipped because the author had
# no in-shell signal that CI went red. This script fetches the latest GitHub
# Actions workflow run status for the current HEAD (or a named ref) and
# reports it in a format that's hard to ignore.
#
# REQUIRES: gh CLI (`brew install gh`), `gh auth login` once.
#
# USAGE:
#   bash scripts/ci-status.sh                  # status for HEAD
#   bash scripts/ci-status.sh <sha-or-ref>     # status for that ref
#   bash scripts/ci-status.sh --watch          # poll until conclusion is non-null
#
# EXIT CODES:
#   0  — latest workflow run for the ref completed with conclusion=success
#   1  — completed with conclusion in {failure, cancelled, timed_out, action_required, neutral}
#   2  — still running (status in {queued, in_progress, waiting})
#   3  — no workflow runs found for the ref (push may not have triggered yet)
#   4  — pre-flight check failed (gh missing, not authed, not in a git repo, etc.)
#
# This script is NOT wired into any commit hook or CI gate. It is intentionally
# opt-in — the author runs it after pushing, before walking away from the
# session. Wiring it into pre-push is the natural next step if the M1.2
# soak surfaces another broken-on-main incident.

set -euo pipefail

RED=$'\033[1;31m'
YEL=$'\033[1;33m'
GRN=$'\033[1;32m'
DIM=$'\033[2m'
NC=$'\033[0m'

# ---- Pre-flight ----------------------------------------------------------
if ! command -v gh >/dev/null 2>&1; then
    echo "${RED}error${NC}: gh CLI not installed. Run: brew install gh" >&2
    exit 4
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "${RED}error${NC}: gh CLI not authenticated. Run: gh auth login" >&2
    exit 4
fi

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "${RED}error${NC}: not inside a git repository." >&2
    exit 4
fi

# ---- Argument parsing ----------------------------------------------------
WATCH=0
REF=""
for arg in "$@"; do
    case "$arg" in
        --watch) WATCH=1 ;;
        -h|--help)
            sed -n '2,30p' "$0"
            exit 0
            ;;
        *)  REF="$arg" ;;
    esac
done

if [[ -z "$REF" ]]; then
    REF=$(git rev-parse HEAD)
fi
SHORT=$(git rev-parse --short "$REF" 2>/dev/null || echo "$REF")

# ---- Helpers -------------------------------------------------------------
fetch_runs() {
    # gh run list scoped to the current branch returns most-recent first; we
    # filter by head_sha to get only runs for the specified ref.
    gh run list --limit 20 --json \
        databaseId,name,status,conclusion,headSha,event,createdAt,url \
        --jq ".[] | select(.headSha | startswith(\"$REF\") or .headSha == \"$REF\")"
}

format_run() {
    local json="$1"
    local name status conclusion url created
    name=$(echo "$json" | jq -r '.name')
    status=$(echo "$json" | jq -r '.status')
    conclusion=$(echo "$json" | jq -r '.conclusion // ""')
    url=$(echo "$json" | jq -r '.url')
    created=$(echo "$json" | jq -r '.createdAt')

    local label color
    case "$status:$conclusion" in
        completed:success)         label="PASS"        color="$GRN" ;;
        completed:failure)         label="FAIL"        color="$RED" ;;
        completed:cancelled)       label="CANCELLED"   color="$YEL" ;;
        completed:timed_out)       label="TIMEOUT"     color="$RED" ;;
        completed:action_required) label="ACTION-REQD" color="$YEL" ;;
        completed:neutral)         label="NEUTRAL"     color="$YEL" ;;
        completed:skipped)         label="SKIPPED"     color="$DIM" ;;
        queued:*)                  label="QUEUED"      color="$YEL" ;;
        in_progress:*)             label="RUNNING"     color="$YEL" ;;
        waiting:*)                 label="WAITING"     color="$YEL" ;;
        *)                         label="$status"     color="$DIM" ;;
    esac

    printf '  %b%-12s%b %-32s %s\n' "$color" "$label" "$NC" "$name" "$created"
    printf '  %b%s%b\n' "$DIM" "$url" "$NC"
}

worst_exit_code() {
    local runs="$1"
    local has_running=0 has_failed=0 has_success=0
    while IFS= read -r run; do
        [[ -z "$run" ]] && continue
        local status conclusion
        status=$(echo "$run" | jq -r '.status')
        conclusion=$(echo "$run" | jq -r '.conclusion // ""')
        case "$status:$conclusion" in
            completed:success) has_success=1 ;;
            completed:failure|completed:timed_out|completed:cancelled) has_failed=1 ;;
            completed:action_required|completed:neutral) has_failed=1 ;;
            queued:*|in_progress:*|waiting:*) has_running=1 ;;
        esac
    done <<< "$runs"

    if (( has_failed )); then return 1; fi
    if (( has_running )); then return 2; fi
    if (( has_success )); then return 0; fi
    return 3
}

# ---- Main ----------------------------------------------------------------
report() {
    local runs
    runs=$(fetch_runs)

    if [[ -z "$runs" ]]; then
        echo "${YEL}no CI runs found for ${SHORT}${NC}"
        echo "${DIM}  push the commit and wait a few seconds for the workflow to trigger.${NC}"
        return 3
    fi

    echo "${DIM}CI status for ${SHORT}:${NC}"
    while IFS= read -r run; do
        [[ -z "$run" ]] && continue
        format_run "$run"
    done <<< "$runs"

    worst_exit_code "$runs"
}

if (( WATCH == 0 )); then
    report
    exit $?
fi

# --- Watch mode -----------------------------------------------------------
# Poll until no run is in_progress/queued/waiting. Bail out on Ctrl-C.
echo "${DIM}watching CI for ${SHORT} (Ctrl-C to stop)...${NC}"
while true; do
    clear
    report
    ec=$?
    if (( ec != 2 )); then
        exit $ec
    fi
    sleep 15
done
