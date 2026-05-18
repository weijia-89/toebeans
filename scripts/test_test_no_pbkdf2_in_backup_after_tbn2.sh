#!/usr/bin/env bash
# Negative-test harness for scripts/test_no_pbkdf2_in_backup_after_tbn2.sh.
#
# Mirrors the shape of scripts/test_test_no_duplicate_private_test_class_names.sh
# (ADR-0017 § Lesson 2 self-test). Stages synthetic Kotlin files under a
# tmpdir that matches the scan-root layout the fitness function expects,
# runs the function with the tmpdir as its scan root, and asserts the
# exit code matches the documented behavior.
#
# Does NOT exercise the real backup module. The fitness function under
# test takes its scan root as $1, so this harness points it at fixtures.
#
# Usage: bash scripts/test_test_no_pbkdf2_in_backup_after_tbn2.sh [repo-root]
#   repo-root defaults to "." so the harness can locate the fitness
#   function regardless of CWD.

set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_no_pbkdf2_in_backup_after_tbn2.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo "✗ fitness-function script not found at $SCRIPT"
    exit 1
fi

FAILED=0

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

COMMON_DIR="$tmpdir/shared/src/commonMain/kotlin/app/toebeans/core/backup"
JVM_DIR="$tmpdir/shared/src/jvmMain/kotlin/app/toebeans/core/backup"
mkdir -p "$COMMON_DIR" "$JVM_DIR"

clean_fixtures() {
    rm -f "$COMMON_DIR"/*.kt "$JVM_DIR"/*.kt
}

# ---- Fixture writers ---------------------------------------------------

# Fixture: a TBN2-cipher source file. Presence of this file is what
# activates the fitness function (TBN2-gate).
write_tbn2_cipher() {
    cat > "$COMMON_DIR/BackupCipherV2.kt" <<'KT'
package app.toebeans.core.backup

public interface BackupCipherV2 {
    public companion object {
        public const val MAGIC: String = "TBN2"
    }
}
KT
}

# Fixture: a stale file that uses PBKDF2 without any allow marker.
# This is the failure mode the fitness function exists to catch.
write_stale_pbkdf2_user() {
    cat > "$COMMON_DIR/StaleCipher.kt" <<'KT'
package app.toebeans.core.backup

internal class StaleCipher {
    private val keyFactory = "PBKDF2WithHmacSHA256"
}
KT
}

# Fixture: a TBN1 legacy decrypt file with the file-level allow marker.
# The function MUST treat this as allow-listed.
write_tbn1_legacy_with_file_marker() {
    cat > "$JVM_DIR/BackupCipherV1.kt" <<'KT'
// FITNESS-ALLOW-PBKDF2-FILE: TBN1 legacy decrypt only per ADR-0018.
package app.toebeans.core.backup

import javax.crypto.SecretKeyFactory

internal class BackupCipherV1 {
    private val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
}
KT
}

# Fixture: a file with a line-level allow marker on the PBKDF2 line.
write_pbkdf2_with_line_marker() {
    cat > "$COMMON_DIR/MixedCipher.kt" <<'KT'
package app.toebeans.core.backup

internal class MixedCipher {
    val legacyAlgo = "PBKDF2WithHmacSHA256" // FITNESS-ALLOW-PBKDF2: TBN1 envelope decrypt
}
KT
}

# Fixture: a file that only mentions PBKDF2 in a comment line. Should
# NOT trigger (comment lines are stripped before the regex check).
write_pbkdf2_in_comment_only() {
    cat > "$COMMON_DIR/CommentCipher.kt" <<'KT'
package app.toebeans.core.backup

// Historical note: the v1 envelope used PBKDF2-HMAC-SHA256. See ADR-0018.
internal class CommentCipher
KT
}

# Fixture: an Argon2id-only file, no PBKDF2 anywhere. Trivially clean.
write_argon2id_clean() {
    cat > "$COMMON_DIR/Argon2idKdf.kt" <<'KT'
package app.toebeans.core.backup

public interface Argon2idKdf {
    public fun derive(passphrase: CharArray, salt: ByteArray): ByteArray
}
KT
}

# ---- Test cases --------------------------------------------------------

# Test 1: no TBN2 anywhere — function MUST be a no-op (exit 0), even if
# PBKDF2 references exist in the tree.
clean_fixtures
write_stale_pbkdf2_user
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test 1: function fired despite TBN2 not being present (should be no-op)"
    FAILED=$((FAILED + 1))
fi

# Test 2: TBN2 present + a stale PBKDF2 user with no allow marker
# — function MUST detect the violation (nonzero exit).
clean_fixtures
write_tbn2_cipher
write_stale_pbkdf2_user
if bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test 2: function FAILED to detect a stale PBKDF2 user when TBN2 is present"
    FAILED=$((FAILED + 1))
fi

# Test 3: TBN2 present + a file-level-allowed TBN1 legacy file
# — function MUST pass.
clean_fixtures
write_tbn2_cipher
write_tbn1_legacy_with_file_marker
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test 3: function FALSE-POSITIVED on file-level-allowed TBN1 legacy file"
    FAILED=$((FAILED + 1))
fi

# Test 4: TBN2 present + a line-level-allowed PBKDF2 use — function
# MUST pass.
clean_fixtures
write_tbn2_cipher
write_pbkdf2_with_line_marker
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test 4: function FALSE-POSITIVED on line-level-allowed PBKDF2 use"
    FAILED=$((FAILED + 1))
fi

# Test 5: TBN2 present + a comment-only PBKDF2 mention — function
# MUST pass (comment lines stripped before the check).
clean_fixtures
write_tbn2_cipher
write_pbkdf2_in_comment_only
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test 5: function FALSE-POSITIVED on PBKDF2 mention in a comment-only line"
    FAILED=$((FAILED + 1))
fi

# Test 6: TBN2 present + an Argon2id-only world — function MUST pass.
clean_fixtures
write_tbn2_cipher
write_argon2id_clean
if ! bash "$SCRIPT" "$tmpdir" >/dev/null 2>&1; then
    echo "✗ test 6: function FALSE-POSITIVED on an Argon2id-only fixture"
    FAILED=$((FAILED + 1))
fi

# Test 7: TBN2 present + multiple files with mixed states (one bad +
# one allow-listed) — function MUST detect the unmarked violation
# but report it only for the unmarked file.
clean_fixtures
write_tbn2_cipher
write_tbn1_legacy_with_file_marker
write_stale_pbkdf2_user
report=$(bash "$SCRIPT" "$tmpdir" 2>&1 || true)
if ! grep -q "StaleCipher.kt" <<< "$report"; then
    echo "✗ test 7: function did not name StaleCipher.kt in the violation report"
    FAILED=$((FAILED + 1))
fi
if grep -q "BackupCipherV1.kt" <<< "$report"; then
    echo "✗ test 7: function flagged BackupCipherV1.kt despite its file-level allow marker"
    FAILED=$((FAILED + 1))
fi

if [[ "$FAILED" -gt 0 ]]; then
    echo ""
    echo "✗ test_test_no_pbkdf2_in_backup_after_tbn2: $FAILED check(s) failed"
    exit 1
fi

echo "✓ test_test_no_pbkdf2_in_backup_after_tbn2: all fixtures behaved as documented"
exit 0
