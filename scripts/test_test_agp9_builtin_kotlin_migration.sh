#!/usr/bin/env bash
# Fixture self-test for scripts/test_agp9_builtin_kotlin_migration.sh.
#
# Seeds an OLD layout (must FAIL with named messages), a NEW layout (must PASS),
# and a NEW layout with an old detekt pin (must FAIL on the version floor).
# Both fixtures carry comment-only markers that would fake the opposite result
# if the checker did not strip comments first.

set -uo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_agp9_builtin_kotlin_migration.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "FAIL fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

seed_scaffold() {
    local root="$1"
    mkdir -p "$root/gradle" "$root/.github/workflows" "$root/scripts" "$root/shared" \
        "$root/shared/src/commonMain/sqldelight"
}

seed_old() {
    local root="$1"
    seed_scaffold "$root"

    cat > "$root/gradle.properties" <<'EOF'
# OLD LAYOUT fixture
# comment-only fake: # PASS gradle.properties has no android.builtInKotlin opt-out
android.builtInKotlin=false
android.newDsl=false
kotlin.mpp.androidGradlePluginCompatibility.nowarn=true
EOF

    cat > "$root/gradle/libs.versions.toml" <<'EOF'
[versions]
ktlint = "13.0.0"
kover = "0.9.1"
detekt = "1.23.8"

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
# comment-only new markers must be stripped:
# detekt = { id = "dev.detekt", version.ref = "detekt" }
EOF

    cat > "$root/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.io.gitlab.arturbosch.detekt) apply false
}
// comment-only new markers must be stripped:
// alias(libs.plugins.android.kotlin.multiplatform.library)
// withHostTest
// kotlin { android {
// :shared:testAndroidHostTest
EOF

    cat > "$root/shared/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    androidTarget { }
}
android {
    namespace = "app.toebeans.core"
}
val androidUnitTest by getting
EOF

    cat > "$root/.github/workflows/ci.yml" <<'EOF'
jobs:
  build:
    steps:
      - run: ./gradlew :shared:jvmTest :shared:testDebugUnitTest --stacktrace
      - run: ./gradlew ktlintCheck detekt --stacktrace
      # comment-only new marker must be stripped:
      # - run: ./gradlew :shared:testAndroidHostTest
EOF

    cat > "$root/scripts/verify_before_push.sh" <<'EOF'
./gradlew :shared:jvmTest --console=plain
./gradlew :androidApp:testDebugUnitTest --console=plain
./gradlew ktlintCheck detekt --console=plain
# :shared:testAndroidHostTest
EOF
}

seed_new() {
    local root="$1"
    seed_scaffold "$root"

    cat > "$root/gradle.properties" <<'EOF'
# NEW LAYOUT fixture
# comment-only old markers must be stripped:
# android.builtInKotlin=false
# android.newDsl=false
# kotlin.mpp.androidGradlePluginCompatibility.nowarn=true
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=fail
EOF

    cat > "$root/gradle/libs.versions.toml" <<'EOF'
[versions]
agp = "9.5.1"
ktlint = "14.2.0"
kover = "0.9.11"
detekt = "2.0.0-alpha.6"

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
detekt = { id = "dev.detekt", version.ref = "detekt" }
# comment-only old markers must be stripped:
# kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
# detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
EOF

    cat > "$root/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.detekt) apply false
}
// comment-only old markers must be stripped:
// alias(libs.plugins.kotlin.android)
// io.gitlab.arturbosch.detekt
EOF

    printf 'SQLite format 3\0' > "$root/shared/src/commonMain/sqldelight/1.db"

    cat > "$root/shared/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
}
sqldelight {
    databases {
        create("ToebeansDatabase") {
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}
kotlin {
    android {
        withHostTest { }
    }
    jvm()
}
// comment-only old markers must be stripped:
// androidUnitTest
// androidTarget
// alias(libs.plugins.android.library)
EOF

    cat > "$root/.github/workflows/ci.yml" <<'EOF'
jobs:
  build:
    steps:
      - run: ./gradlew :shared:jvmTest :shared:testAndroidHostTest --stacktrace
      - run: ./gradlew ktlintCheck detekt --no-configuration-cache --stacktrace
      # comment-only old marker must be stripped:
      # - run: ./gradlew :shared:testDebugUnitTest
EOF

    cat > "$root/scripts/verify_before_push.sh" <<'EOF'
./gradlew :shared:jvmTest --console=plain
./gradlew :shared:testAndroidHostTest --console=plain
./gradlew ktlintCheck detekt --no-configuration-cache --console=plain
# :shared:testDebugUnitTest
EOF
}

OLD="$tmpdir/old"
NEW="$tmpdir/new"
NEW_OLD_DETEKT="$tmpdir/new-old-detekt"
seed_old "$OLD"
seed_new "$NEW"
seed_new "$NEW_OLD_DETEKT"
sed -i '' 's/detekt = "2.0.0-alpha.6"/detekt = "1.23.8"/' "$NEW_OLD_DETEKT/gradle/libs.versions.toml"

# --- Test 1: old layout must FAIL with named messages --------------------
old_report=$(bash "$SCRIPT" "$OLD" 2>&1) && old_rc=0 || old_rc=$?
if [[ "$old_rc" -eq 0 ]]; then
    echo "FAIL test 1: checker PASSED on the old-layout fixture"
    FAILED=$((FAILED + 1))
fi
for needle in \
    "still sets android.builtInKotlin" \
    "still sets android.newDsl" \
    "still sets ADR-0015" \
    "still applies or declares org.jetbrains.kotlin.android" \
    "still uses the detekt 1.x plugin id" \
    "still applies com.android.library" \
    "still references androidUnitTest or androidTarget" \
    "does not set verifyMigrations.set(true)" \
    "does not set schemaOutputDirectory" \
    "committed, non-empty SQLite 1.db golden fixture is missing" \
    "still calls :shared:testDebugUnitTest" \
    "below the 2.0.0-alpha.3 floor" \
    "below the 14.1.0 floor" \
    "below the 0.9.9 floor"; do
    if ! grep -qF "$needle" <<< "$old_report"; then
        echo "FAIL test 1: old-layout report missing named message: $needle"
        FAILED=$((FAILED + 1))
    fi
done

# --- Test 2: new layout must PASS (comments cannot fake failure) ---------
new_report=$(bash "$SCRIPT" "$NEW" 2>&1) && new_rc=0 || new_rc=$?
if [[ "$new_rc" -ne 0 ]]; then
    echo "FAIL test 2: checker FAILED on the new-layout fixture"
    echo "$new_report"
    FAILED=$((FAILED + 1))
fi

# --- Test 3: old detekt pin in an otherwise new layout must FAIL ---------
detekt_report=$(bash "$SCRIPT" "$NEW_OLD_DETEKT" 2>&1) && detekt_rc=0 || detekt_rc=$?
if [[ "$detekt_rc" -eq 0 ]]; then
    echo "FAIL test 3: checker PASSED with detekt 1.23.8 in the catalog"
    FAILED=$((FAILED + 1))
fi
if ! grep -qF "below the 2.0.0-alpha.3 floor" <<< "$detekt_report"; then
    echo "FAIL test 3: report missing detekt floor message"
    FAILED=$((FAILED + 1))
fi

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "FAIL test_test_agp9_builtin_kotlin_migration: $FAILED check(s) failed"
    exit 1
fi

echo "PASS test_test_agp9_builtin_kotlin_migration: all fixtures behaved as documented"
