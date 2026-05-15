#!/usr/bin/env bash
# Fitness function #1 — No network library may appear in shared/commonMain/ or shared/androidMain/
# or in the androidApp module's main source set.
#
# A v1 toebeans build that calls the network for ANY reason is a regression.
# See AGENTS.md § Posture.

set -euo pipefail

ROOT="${1:-.}"

FORBIDDEN_PATTERNS=(
    'okhttp3'
    'retrofit2'
    'io\.ktor\.'
    'java\.net\.URL'
    'java\.net\.HttpURLConnection'
    'javax\.net\.ssl\.HttpsURLConnection'
    'okio\.IO'
    'org\.apache\.http'
    'com\.google\.firebase'
    'com\.google\.android\.gms'
)

SEARCH_PATHS=(
    "$ROOT/shared/src/commonMain"
    "$ROOT/shared/src/androidMain"
    "$ROOT/androidApp/src/main"
)

violations=0

for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
    for path in "${SEARCH_PATHS[@]}"; do
        if [ ! -d "$path" ]; then
            continue
        fi
        # -F would be faster but we want regex for the dot-escape patterns.
        matches=$(grep -rEn --include='*.kt' --include='*.kts' --include='*.xml' "$pattern" "$path" || true)
        if [ -n "$matches" ]; then
            echo "::error::FITNESS-NO-NETWORK violation — forbidden pattern '$pattern' found:"
            echo "$matches"
            violations=$((violations+1))
        fi
    done
done

if [ "$violations" -gt 0 ]; then
    echo
    echo "Total violations: $violations"
    echo "If a network call is genuinely required, write a forcing-constraint ADR before adding it."
    exit 1
fi

echo "✓ No-network fitness function passed."
