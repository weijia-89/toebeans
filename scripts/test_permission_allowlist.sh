#!/usr/bin/env bash
# Fitness function #4 — AndroidManifest permission allowlist.
# Only the permissions in AGENTS.md may appear in any AndroidManifest.xml file
# in the repository.

set -euo pipefail

ROOT="${1:-.}"

ALLOWLIST=(
    'android.permission.POST_NOTIFICATIONS'
    'android.permission.SCHEDULE_EXACT_ALARM'
    'android.permission.USE_EXACT_ALARM'
    'android.permission.RECEIVE_BOOT_COMPLETED'
)

violations=0

while IFS= read -r manifest; do
    # Each line: extract the permission name from <uses-permission android:name="...">
    perms=$(grep -oE 'android:name="android\.permission\.[A-Z_]+"' "$manifest" | sed -E 's/^android:name="//;s/"$//')
    while IFS= read -r perm; do
        if [ -z "$perm" ]; then continue; fi
        ok=0
        for allowed in "${ALLOWLIST[@]}"; do
            if [ "$perm" = "$allowed" ]; then ok=1; break; fi
        done
        if [ "$ok" -eq 0 ]; then
            echo "::error::FITNESS-PERMISSION-ALLOWLIST violation in $manifest — permission '$perm' is not allowed."
            violations=$((violations+1))
        fi
    done <<< "$perms"
done < <(find "$ROOT" -path '*/src/main/AndroidManifest.xml' -type f)

if [ "$violations" -gt 0 ]; then
    echo
    echo "Total violations: $violations"
    echo "Add the permission to AGENTS.md (and this script's allowlist) ONLY after a human-reviewed ADR."
    exit 1
fi

echo "✓ Permission-allowlist fitness function passed."
