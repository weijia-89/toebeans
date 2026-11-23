#!/usr/bin/env bash
# Fixture self-test for scripts/test_decompose_nav_migration.sh.
#
# Seeds three trees:
#   OLD        no migration (ADR present) - must FAIL on the five migration gates
#   NEW        migration landed - must PASS
#   NEW_BUMPED migration landed but compileSdk raised - must FAIL on the SDK gate
# Fixtures carry comment-only markers that would fake the opposite result if
# the checker did not strip comments first.

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_decompose_nav_migration.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

seed_scaffold() {
    local root="$1"
    mkdir -p "$root/gradle" "$root/androidApp" \
        "$root/androidApp/src/test/kotlin/app/toebeans/android/ui/nav" \
        "$root/shared/src/commonMain/kotlin/app/toebeans/core/navigation" \
        "$root/docs/adr"
    : > "$root/docs/adr/0023-decompose-navigation-forcing-constraint.md"
}

seed_old() {
    local root="$1"
    seed_scaffold "$root"

    cat > "$root/gradle/libs.versions.toml" <<'EOF'
[versions]
kotlin = "2.4.21"
android-compile-sdk = "35"
android-target-sdk = "34"
# comment-only migration markers must be stripped:
# decompose = "3.6.2"

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "androidx-navigation" }
EOF

    cat > "$root/androidApp/build.gradle.kts" <<'EOF'
dependencies {
    implementation(libs.androidx.navigation.compose)
    // implementation(libs.decompose)
}
EOF
}

seed_new() {
    local root="$1"
    seed_scaffold "$root"

    cat > "$root/gradle/libs.versions.toml" <<'EOF'
[versions]
kotlin = "2.4.21"
android-compile-sdk = "35"
android-target-sdk = "34"
decompose = "3.6.2"

[libraries]
decompose = { group = "com.arkivanov.decompose", name = "decompose", version.ref = "decompose" }
# comment-only forbidden artifact must be stripped:
# firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics", version.ref = "x" }
EOF

    cat > "$root/androidApp/build.gradle.kts" <<'EOF'
dependencies {
    implementation(libs.decompose)
    // implementation(libs.firebase.analytics)
}
EOF

    cat > "$root/shared/src/commonMain/kotlin/app/toebeans/core/navigation/ToebeansRootComponent.kt" <<'EOF'
package app.toebeans.core.navigation

interface ToebeansRootComponent {
    val childStack: Value<ChildStack<*, *>>
}
EOF

    cat > "$root/androidApp/src/test/kotlin/app/toebeans/android/ui/nav/DecomposeNavigationContractTest.kt" <<'EOF'
package app.toebeans.android.ui.nav

class DecomposeNavigationContractTest {
    fun allFiveTabsAreReachable() {}
    fun doseLoggingRouteReachableFromToday() {}
    fun backStackAndTabStateAreRestored() {}
}
EOF
}

OLD="$tmpdir/old"
NEW="$tmpdir/new"
NEW_BUMPED="$tmpdir/new-bumped"
seed_old "$OLD"
seed_new "$NEW"
seed_new "$NEW_BUMPED"
sed -i '' 's/android-compile-sdk = "35"/android-compile-sdk = "36"/' \
    "$NEW_BUMPED/gradle/libs.versions.toml"

# --- Test 1: old tree must FAIL with named messages -----------------------
old_report=$(bash "$SCRIPT" "$OLD" 2>&1) && old_rc=0 || old_rc=$?
if [[ "$old_rc" -eq 0 ]]; then
    echo "FAIL test 1: checker PASSED on the old-tree fixture"
    FAILED=$((FAILED + 1))
fi
for needle in \
    "version catalog does not declare the com.arkivanov.decompose group" \
    "Decompose version is <missing>, expected 3.6.2" \
    "no build file consumes the Decompose dependency" \
    "no navigation model under shared/src/commonMain" \
    "Decompose navigation contract test is missing"; do
    if ! grep -qF "$needle" <<< "$old_report"; then
        echo "FAIL test 1: old-tree report missing named message: $needle"
        FAILED=$((FAILED + 1))
    fi
done
if ! grep -qF "PASS forcing-constraint ADR 0023 exists" <<< "$old_report"; then
    echo "FAIL test 1: old-tree report should pass the ADR gate"
    FAILED=$((FAILED + 1))
fi

# --- Test 2: migrated tree must PASS (comments cannot fake a result) ------
new_report=$(bash "$SCRIPT" "$NEW" 2>&1) && new_rc=0 || new_rc=$?
if [[ "$new_rc" -ne 0 ]]; then
    echo "FAIL test 2: checker FAILED on the migrated-tree fixture"
    echo "$new_report"
    FAILED=$((FAILED + 1))
fi

# --- Test 3: compileSdk bump in an otherwise migrated tree must FAIL ------
bumped_report=$(bash "$SCRIPT" "$NEW_BUMPED" 2>&1) && bumped_rc=0 || bumped_rc=$?
if [[ "$bumped_rc" -eq 0 ]]; then
    echo "FAIL test 3: checker PASSED with compileSdk 36"
    FAILED=$((FAILED + 1))
fi
if ! grep -qF "compileSdk changed to 36; this slice keeps 35" <<< "$bumped_report"; then
    echo "FAIL test 3: report missing compileSdk drift message"
    FAILED=$((FAILED + 1))
fi

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_decompose_nav_migration: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_decompose_nav_migration: all fixtures behaved as documented"
