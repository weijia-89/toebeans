#!/usr/bin/env bash
# Fitness function #3 — Scheduler purity.
# No file in shared/.../scheduler/ may reference a platform clock other than the injected
# kotlinx.datetime.Clock. This forces testability and prevents "now()" leakage.

set -euo pipefail

ROOT="${1:-.}"
SCHEDULER_PATH="$ROOT/shared/src/commonMain/kotlin/app/toebeans/core/scheduler"

if [ ! -d "$SCHEDULER_PATH" ]; then
    echo "::error::Scheduler path not found: $SCHEDULER_PATH"
    exit 1
fi

FORBIDDEN_PATTERNS=(
    'System\.currentTimeMillis'
    'System\.nanoTime'
    'java\.time\.Instant\.now'
    'java\.time\.LocalDateTime\.now'
    'java\.util\.Date\(\)'
    'kotlinx\.datetime\.Clock\.System'
    '\.now\(\)'
)

violations=0

for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
    matches=$(grep -rEn --include='*.kt' "$pattern" "$SCHEDULER_PATH" || true)
    if [ -n "$matches" ]; then
        echo "::error::FITNESS-SCHEDULER-PURITY violation — forbidden clock access '$pattern' in scheduler:"
        echo "$matches"
        violations=$((violations+1))
    fi
done

if [ "$violations" -gt 0 ]; then
    echo
    echo "Total violations: $violations"
    echo "Scheduler logic MUST accept Clock as a parameter, not read it from the platform."
    exit 1
fi

echo "✓ Scheduler-purity fitness function passed."
