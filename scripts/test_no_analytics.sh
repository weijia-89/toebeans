#!/usr/bin/env bash
# Fitness function #2 — No analytics or telemetry SDK may appear in build files or sources.

set -euo pipefail

ROOT="${1:-.}"

FORBIDDEN_PATTERNS=(
    'com\.google\.firebase:firebase-analytics'
    'com\.google\.android\.gms:play-services-analytics'
    'com\.mixpanel\.android'
    'com\.amplitude\.android'
    'com\.segment\.analytics'
    'io\.sentry'
    'com\.bugsnag'
    'com\.datadoghq'
    'com\.posthog'
)

SEARCH_PATHS=(
    "$ROOT/build.gradle.kts"
    "$ROOT/settings.gradle.kts"
    "$ROOT/gradle/libs.versions.toml"
    "$ROOT/shared/build.gradle.kts"
    "$ROOT/androidApp/build.gradle.kts"
    "$ROOT/shared/src"
    "$ROOT/androidApp/src"
)

violations=0

for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
    for path in "${SEARCH_PATHS[@]}"; do
        if [ ! -e "$path" ]; then continue; fi
        matches=$(grep -rEn "$pattern" "$path" 2>/dev/null || true)
        if [ -n "$matches" ]; then
            echo "::error::FITNESS-NO-ANALYTICS violation — '$pattern' found:"
            echo "$matches"
            violations=$((violations+1))
        fi
    done
done

if [ "$violations" -gt 0 ]; then
    echo
    echo "Total violations: $violations"
    exit 1
fi

echo "✓ No-analytics fitness function passed."
