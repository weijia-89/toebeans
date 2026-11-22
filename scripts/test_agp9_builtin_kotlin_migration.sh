#!/usr/bin/env bash
# Fitness function — AGP 9 built-in Kotlin migration (build/agp9-builtin-kotlin).
#
# Static guards for the AGP 9 migration decided in
# research/agp9-builtin-kotlin-migration.md:
#   - no android.builtInKotlin=false / android.newDsl=false opt-outs
#   - no org.jetbrains.kotlin.android plugin anywhere (catalog or build files)
#   - :shared on com.android.kotlin.multiplatform.library with kotlin { android { } }
#   - no androidTarget / androidUnitTest legacy wiring
#   - CI + pre-push call the real Android host-test task, not testDebugUnitTest
#   - dependency floors (ktlint-gradle, Kover, detekt)
#
# Comment lines are stripped from every input before matching. A comment that
# mentions an old flag or fakes a new marker cannot create a false result.
#
# Usage: bash scripts/test_agp9_builtin_kotlin_migration.sh [repo-root]

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

GRADLE_PROPS="$ROOT/gradle.properties"
CATALOG="$ROOT/gradle/libs.versions.toml"
CI="$ROOT/.github/workflows/ci.yml"
VERIFY="$ROOT/scripts/verify_before_push.sh"

for required in "$GRADLE_PROPS" "$CATALOG" "$CI" "$VERIFY"; do
    if [[ ! -f "$required" ]]; then
        echo "FAIL missing required file: $required"
        exit 1
    fi
done

# --- 1. gradle.properties opt-outs ---------------------------------------
props=$(strip_comments "$GRADLE_PROPS")
if grep -q '^android\.builtInKotlin[[:space:]]*=' <<< "$props"; then
    fail "gradle.properties still sets android.builtInKotlin"
else
    ok "gradle.properties has no android.builtInKotlin opt-out"
fi
if grep -q '^android\.newDsl[[:space:]]*=' <<< "$props"; then
    fail "gradle.properties still sets android.newDsl"
else
    ok "gradle.properties has no android.newDsl opt-out"
fi
if grep -q '^kotlin\.mpp\.androidGradlePluginCompatibility\.nowarn[[:space:]]*=' <<< "$props"; then
    fail "gradle.properties still sets ADR-0015 kotlin.mpp.androidGradlePluginCompatibility.nowarn suppression"
else
    ok "gradle.properties has no ADR-0015 suppression"
fi

# --- 2. all build files + catalog: no legacy Kotlin Android plugin --------
all_build_text=""
while IFS= read -r -d '' f; do
    all_build_text+="$(strip_comments "$f")"$'\n'
done < <(find "$ROOT" -type f \( -name '*.gradle.kts' -o -name 'libs.versions.toml' \) \
    -not -path '*/build/*' -not -path '*/.gradle/*' -print0)
catalog_text=$(strip_comments "$CATALOG")

if grep -qE 'org\.jetbrains\.kotlin\.android|kotlin-android|plugins\.kotlin\.android' <<< "$all_build_text"; then
    fail "a build file still applies or declares org.jetbrains.kotlin.android"
else
    ok "no build file applies or declares org.jetbrains.kotlin.android"
fi
if grep -qE 'io\.gitlab\.arturbosch\.detekt' <<< "$all_build_text"; then
    fail "a build file still uses the detekt 1.x plugin id io.gitlab.arturbosch.detekt"
else
    ok "no build file uses the detekt 1.x plugin id"
fi
if ! grep -q 'id = "dev.detekt"' <<< "$catalog_text"; then
    fail "libs.versions.toml does not declare the detekt 2 plugin id dev.detekt"
else
    ok "libs.versions.toml declares dev.detekt"
fi

# --- 3. new KMP Android library plugin alias ----------------------------
if ! grep -q 'android-kotlin-multiplatform-library.*com\.android\.kotlin\.multiplatform\.library' <<< "$catalog_text"; then
    fail "libs.versions.toml does not define android-kotlin-multiplatform-library = com.android.kotlin.multiplatform.library"
else
    ok "libs.versions.toml defines the KMP Android library plugin alias"
fi

SHARED="$ROOT/shared/build.gradle.kts"
shared_text=$(strip_comments "$SHARED")
if grep -q 'alias(libs.plugins.android.library)' <<< "$shared_text"; then
    fail ":shared still applies com.android.library (alias android.library)"
else
    ok ":shared no longer applies com.android.library"
fi
if ! grep -q 'alias(libs.plugins.android.kotlin.multiplatform.library)' <<< "$shared_text"; then
    fail ":shared does not apply com.android.kotlin.multiplatform.library"
else
    ok ":shared applies com.android.kotlin.multiplatform.library"
fi
has_kotlin_android_block() {
    awk '
        /kotlin[[:space:]]*\{/ { in_kotlin = 1; next }
        in_kotlin && /^[[:space:]]*android[[:space:]]*\{/ { found = 1; exit }
        /^\}/ { in_kotlin = 0 }
        END { exit(found ? 0 : 1) }
    ' <<< "$1"
}
if ! has_kotlin_android_block "$shared_text"; then
    fail ":shared does not contain kotlin { android { } }"
else
    ok ":shared contains kotlin { android { } }"
fi
if ! grep -q 'withHostTest' <<< "$shared_text"; then
    fail ":shared does not enable withHostTest"
else
    ok ":shared enables withHostTest"
fi
if grep -qE 'androidUnitTest|androidTarget' <<< "$all_build_text"; then
    fail "a build file still references androidUnitTest or androidTarget"
else
    ok "no build file references androidUnitTest or androidTarget"
fi

# --- 3b. SQLDelight golden-schema drift gate ------------------------------
if ! grep -q 'verifyMigrations.set(true)' <<< "$shared_text"; then
    fail ":shared does not set verifyMigrations.set(true)"
else
    ok ":shared sets verifyMigrations.set(true)"
fi
if ! grep -q 'schemaOutputDirectory.set(' <<< "$shared_text"; then
    fail ":shared does not set schemaOutputDirectory"
else
    ok ":shared sets schemaOutputDirectory"
fi
golden_1db=""
while IFS= read -r -d '' db; do
    golden_1db="$db"
    break
done < <(find "$ROOT/shared/src/commonMain/sqldelight" -type f -name '1.db' -print0 2>/dev/null)
if [[ -z "$golden_1db" ]] || [[ ! -s "$golden_1db" ]] || [[ "$(head -c 16 "$golden_1db")" != "SQLite format 3"* ]]; then
    fail "a committed, non-empty SQLite 1.db golden fixture is missing"
else
    ok "committed SQLite 1.db golden fixture exists"
fi

# --- 4. host-test task in CI + pre-push, old task gone -------------------
ci_text=$(strip_comments "$CI")
verify_text=$(strip_comments "$VERIFY")
for label_file in "CI:$ci_text" "verify_before_push.sh:$verify_text"; do
    label="${label_file%%:*}"
    text="${label_file#*:}"
    if ! grep -q ':shared:testAndroidHostTest' <<< "$text"; then
        fail "$label does not call :shared:testAndroidHostTest"
    else
        ok "$label calls :shared:testAndroidHostTest"
    fi
    if grep -q ':shared:testDebugUnitTest' <<< "$text"; then
        fail "$label still calls :shared:testDebugUnitTest"
    else
        ok "$label does not call :shared:testDebugUnitTest"
    fi
done

# --- 5. dependency floors -----------------------------------------------
version_ge() {
    local have="$1" floor="$2"
    [[ "$(printf '%s\n%s\n' "$have" "$floor" | sort -V | head -n 1)" == "$floor" ]]
}

extract_version() {
    local key="$1"
    sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" <<< "$catalog_text" \
        | head -n 1
}

ktlint_ver=$(extract_version 'ktlint')
kover_ver=$(extract_version 'kover')
detekt_ver=$(extract_version 'detekt')

if [[ -z "$ktlint_ver" ]] || ! version_ge "$ktlint_ver" "14.1.0"; then
    fail "ktlint-gradle version ${ktlint_ver:-<missing>} is below the 14.1.0 floor"
else
    ok "ktlint-gradle $ktlint_ver meets the 14.1.0 floor"
fi
if [[ -z "$kover_ver" ]] || ! version_ge "$kover_ver" "0.9.9"; then
    fail "Kover version ${kover_ver:-<missing>} is below the 0.9.9 floor"
else
    ok "Kover $kover_ver meets the 0.9.9 floor"
fi
detekt_cmp=$(sed -E 's/-alpha\.?/-/' <<< "$detekt_ver" | tr '-' '.')
if [[ -z "$detekt_ver" ]] || ! version_ge "$detekt_cmp" "2.0.0.3"; then
    fail "detekt version ${detekt_ver:-<missing>} is below the 2.0.0-alpha.3 floor"
else
    ok "detekt $detekt_ver meets the 2.0.0-alpha.3 floor"
fi

# --- 6. detekt + configuration cache handled explicitly -----------------
for label_file in "CI:$ci_text" "verify_before_push.sh:$verify_text"; do
    label="${label_file%%:*}"
    text="${label_file#*:}"
    if grep -q 'detekt' <<< "$text" && ! grep -q -- '--no-configuration-cache' <<< "$text"; then
        fail "$label runs detekt without an explicit --no-configuration-cache choice"
    else
        ok "$label documents the detekt configuration-cache choice"
    fi
done

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "$FAILED AGP-9 built-in-Kotlin migration check(s) failed."
    exit 1
fi

echo ""
echo "AGP-9 built-in-Kotlin migration fitness function passed."
