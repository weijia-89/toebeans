#!/usr/bin/env bash
# Negative-test harness for scripts/test_no_duplicate_private_test_class_names.sh.
#
# Mirrors the shape of scripts/test_pre_commit_hook.sh: stages synthetic
# fixtures in a temp directory, runs the fitness function against the
# fixtures, and asserts the script's exit code matches the expected
# behavior. Does NOT exercise the real test source roots; the fitness
# function under test takes its scan root as $1 so we can point it at the
# fixture tree.
#
# Fixture shape: the fitness function looks under <root>/shared/src/commonTest,
# <root>/shared/src/jvmTest, etc. The harness creates files under those
# subpaths inside a tmpdir.
#
# Usage: bash scripts/test_test_no_duplicate_private_test_class_names.sh [repo-root]
#   repo-root defaults to "." so the harness can locate the fitness-function
#   script regardless of CWD.

set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_no_duplicate_private_test_class_names.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "✗ fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

PKG_DIR="$tmpdir/shared/src/commonTest/kotlin/app/toebeans/synthetic"
OTHER_PKG_DIR="$tmpdir/shared/src/jvmTest/kotlin/app/toebeans/other"
mkdir -p "$PKG_DIR" "$OTHER_PKG_DIR"

write_alpha_with_pet_repo() {
    cat > "$PKG_DIR/AlphaTest.kt" <<'KT'
package app.toebeans.synthetic

class AlphaTest

private class SyntheticPetRepo
KT
}

write_beta_with_pet_repo() {
    cat > "$PKG_DIR/BetaTest.kt" <<'KT'
package app.toebeans.synthetic

class BetaTest

private class SyntheticPetRepo
KT
}

write_beta_with_med_repo() {
    cat > "$PKG_DIR/BetaTest.kt" <<'KT'
package app.toebeans.synthetic

class BetaTest

private class SyntheticMedRepo
KT
}

write_other_pkg_with_pet_repo() {
    cat > "$OTHER_PKG_DIR/GammaTest.kt" <<'KT'
package app.toebeans.other

class GammaTest

private class SyntheticPetRepo
KT
}

clean_fixtures() {
    rm -f "$PKG_DIR"/*.kt "$OTHER_PKG_DIR"/*.kt
}

# Test 1: two files in the same package with the same `private class` name
# MUST trigger a violation (nonzero exit).
clean_fixtures
write_alpha_with_pet_repo
write_beta_with_pet_repo
if bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test_test_no_duplicate_private: function FAILED to detect synthetic duplicate"
    FAILED=$((FAILED + 1))
fi

# Test 2: single file with the `private class` declaration MUST pass (exit 0).
clean_fixtures
write_alpha_with_pet_repo
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test_test_no_duplicate_private: function FALSE-POSITIVED on single declaration"
    FAILED=$((FAILED + 1))
fi

# Test 3: two files in the same package with DIFFERENT class names MUST pass.
clean_fixtures
write_alpha_with_pet_repo
write_beta_with_med_repo
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test_test_no_duplicate_private: function FALSE-POSITIVED on distinct classnames"
    FAILED=$((FAILED + 1))
fi

# Test 4: two files with the SAME class name in DIFFERENT packages MUST
# pass. The K2 collision is package-scoped, not global.
clean_fixtures
write_alpha_with_pet_repo
write_other_pkg_with_pet_repo
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test_test_no_duplicate_private: function FALSE-POSITIVED on same name in different packages"
    FAILED=$((FAILED + 1))
fi

# Test 5: a `private open class` MUST NOT trigger (out of scope per the
# fitness function's docstring; ADR-0017 documents `private class` only).
clean_fixtures
cat > "$PKG_DIR/AlphaTest.kt" <<'KT'
package app.toebeans.synthetic

class AlphaTest

private open class SyntheticPetRepo
KT
cat > "$PKG_DIR/BetaTest.kt" <<'KT'
package app.toebeans.synthetic

class BetaTest

private open class SyntheticPetRepo
KT
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test_test_no_duplicate_private: function over-matched on 'private open class' (out of documented scope)"
    FAILED=$((FAILED + 1))
fi

# Test 6: indented (nested) `private class` MUST NOT trigger. Only top-level
# declarations at column 0 are in scope.
clean_fixtures
cat > "$PKG_DIR/AlphaTest.kt" <<'KT'
package app.toebeans.synthetic

class AlphaTest {
    private class SyntheticPetRepo
}
KT
cat > "$PKG_DIR/BetaTest.kt" <<'KT'
package app.toebeans.synthetic

class BetaTest {
    private class SyntheticPetRepo
}
KT
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test_test_no_duplicate_private: function over-matched on nested 'private class' (out of scope)"
    FAILED=$((FAILED + 1))
fi

if [[ "$FAILED" -gt 0 ]]; then
    echo ""
    echo "✗ test_test_no_duplicate_private_test_class_names: $FAILED check(s) failed"
    exit 1
fi

echo "✓ test_test_no_duplicate_private_test_class_names: all fixtures behaved as documented"
exit 0
