#!/usr/bin/env bash
# Fitness function - Decompose navigation migration (phase 24, impl-decompose-nav).
#
# This gate turns RED when the navigation model is still the bespoke shell and
# GREEN when the migration lands. It checks the outcomes that correspond to the
# contract plan in docs/adr/0023-decompose-navigation-forcing-constraint.md:
#   - the exact Decompose coordinate is declared and consumed
#   - a navigation component/stack model exists in shared/commonMain (iOS, D3)
#   - the named Kotlin contract test covers the three preserved behaviours
#   - compileSdk and targetSdk did not drift (Decompose needs no bump)
#   - no analytics, network, or AI artifact entered the catalog
#
# Comment lines are stripped from every input before matching. A comment that
# names a marker cannot create a false result.
#
# Usage: bash scripts/test_decompose_nav_migration.sh [repo-root]

set -uo pipefail

ROOT="${1:-.}"
FAILED=0

fail() {
    echo "FAIL $1"
    FAILED=$((FAILED + 1))
}

ok() {
    echo "PASS $1"
}

# Strip whole-line and inline comments. URLs are protected before // removal
# so an https:// string cannot be mistaken for a comment.
strip_comments() {
    awk '
    {
        line = $0
        gsub(/:\/\//, ":URLSEP", line)
        sub(/\/\/.*$/, "", line)
        gsub(/:URLSEP/, "://", line)
        if (line ~ /^[[:space:]]*#/) next
        if (line ~ /^[[:space:]]*\*/) next
        if (line ~ /^[[:space:]]*\/\*/) next
        if (line ~ /^[[:space:]]*\*\//) next
        print line
    }' "$1"
}

CATALOG="$ROOT/gradle/libs.versions.toml"
SHARED_NAV_DIR="$ROOT/shared/src/commonMain/kotlin/app/toebeans/core/navigation"
CONTRACT_TEST="$ROOT/androidApp/src/test/kotlin/app/toebeans/android/ui/nav/DecomposeNavigationContractTest.kt"
ADR="$ROOT/docs/adr/0023-decompose-navigation-forcing-constraint.md"

if [[ ! -f "$CATALOG" ]]; then
    echo "FAIL missing required file: $CATALOG"
    exit 1
fi

catalog_text=$(strip_comments "$CATALOG")

all_build_text=""
while IFS= read -r -d '' f; do
    all_build_text+="$(strip_comments "$f")"$'\n'
done < <(find "$ROOT" -type f -name '*.gradle.kts' \
    -not -path '*/build/*' -not -path '*/.gradle/*' -print0)

extract_version() {
    local key="$1"
    sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" \
        <<< "$catalog_text" | head -n 1
}

# --- 1. forcing-constraint ADR --------------------------------------------
if [[ -f "$ADR" ]]; then
    ok "forcing-constraint ADR 0023 exists"
else
    fail "forcing-constraint ADR 0023 is missing"
fi

# --- 2. exact Decompose coordinate is declared and consumed ---------------
if grep -qF 'com.arkivanov.decompose' <<< "$catalog_text"; then
    ok "version catalog declares the com.arkivanov.decompose group"
else
    fail "version catalog does not declare the com.arkivanov.decompose group"
fi

decompose_ver=$(extract_version 'decompose')
if [[ "$decompose_ver" == "3.6.2" ]]; then
    ok "Decompose version is pinned to 3.6.2"
else
    fail "Decompose version is ${decompose_ver:-<missing>}, expected 3.6.2"
fi

if grep -qi 'decompose' <<< "$all_build_text"; then
    ok "a build file consumes the Decompose dependency"
else
    fail "no build file consumes the Decompose dependency"
fi

# --- 3. navigation model moved to shared/commonMain (iOS, D3) -------------
if [[ -d "$SHARED_NAV_DIR" ]] \
    && find "$SHARED_NAV_DIR" -type f -name '*.kt' -print -quit | grep -q .; then
    shared_nav_text=$(cat "$SHARED_NAV_DIR"/*.kt)
    if grep -qE 'ComponentContext|ChildStack|Decompose|Navigation|Stack' <<< "$shared_nav_text"; then
        ok "a Decompose navigation model exists in shared/commonMain"
    else
        fail "the shared navigation model has no component or stack API"
    fi
else
    fail "no navigation model under shared/src/commonMain (iOS sharing goal unmet)"
fi

# --- 4. named Kotlin contract test covers the three behaviours ------------
if [[ -f "$CONTRACT_TEST" ]]; then
    ok "Decompose navigation contract test exists"
    test_text=$(strip_comments "$CONTRACT_TEST")
    for needle in allFiveTabsAreReachable doseLoggingRouteReachableFromToday \
        backStackAndTabStateAreRestored; do
        if grep -qF "$needle" <<< "$test_text"; then
            ok "contract test covers $needle"
        else
            fail "contract test is missing named case $needle"
        fi
    done
else
    fail "Decompose navigation contract test is missing"
fi

# --- 5. build level did not drift -----------------------------------------
sdk_ver=$(extract_version 'android-compile-sdk')
target_ver=$(extract_version 'android-target-sdk')
if [[ "$sdk_ver" == "35" ]]; then
    ok "compileSdk is unchanged at 35"
else
    fail "compileSdk changed to ${sdk_ver:-<missing>}; this slice keeps 35"
fi
if [[ "$target_ver" == "34" ]]; then
    ok "targetSdk is unchanged at 34"
else
    fail "targetSdk changed to ${target_ver:-<missing>}; this slice keeps 34"
fi

# --- 6. no analytics, network, or AI artifact entered the catalog ---------
if grep -qiE 'firebase|mixpanel|segment|google-analytics|material3-a2ui|mlkit|tensorflow|onnx' \
    <<< "$catalog_text"; then
    fail "the catalog contains a forbidden analytics, network, or AI artifact"
else
    ok "the catalog contains no analytics, network, or AI artifact"
fi

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "$FAILED Decompose navigation migration check(s) failed."
    exit 1
fi

echo ""
echo "Decompose navigation migration fitness function passed."
