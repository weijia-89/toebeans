#!/usr/bin/env bash
# Fitness function (proposed) — No PBKDF2 in the backup module after TBN2 lands.
#
# ADR-0018 (`docs/adr/0018-argon2id-backup-cipher-design.md`) records the
# Argon2id-based cipher design that activates when an ADR-0016 v2 trigger
# fires. The cipher envelope rotates from `TBN1` (PBKDF2-HMAC-SHA256) to
# `TBN2` (Argon2id). The TBN1 decrypt path stays in the codebase for
# legacy read-only support; the TBN1 encrypt path is removed.
#
# This fitness function enforces the property that, once TBN2 has landed,
# PBKDF2 references in the backup module are limited to the explicitly
# allow-listed TBN1 decrypt path. The check is gated on TBN2 presence:
# if no source file in the backup module contains a "TBN2" string literal,
# the script exits 0 with a no-op message. This makes the script safe to
# ship before D1 and useful immediately after D1 lands.
#
# Two allow mechanisms are supported (either suffices):
#
#   1. File-level allow. The file's top-of-file comment block contains
#      a line matching `// FITNESS-ALLOW-PBKDF2-FILE: <reason>`. This is
#      the right shape for a file that exists specifically to handle
#      legacy TBN1 decryption (e.g. `BackupCipherV1.kt`,
#      `LegacyBackupCipher.kt`, or a renamed `Tbn1Cipher.kt`).
#
#   2. Line-level allow. The specific line containing PBKDF2 has a
#      trailing comment matching `// FITNESS-ALLOW-PBKDF2: <reason>`.
#      Use this when PBKDF2 appears in a single line of a file whose
#      rest of the contents should not be allow-listed wholesale.
#
# Comment-only lines (lines whose first non-whitespace is `//`, `*`, or
# `/*`) are skipped: a KDoc that mentions PBKDF2 as a historical note
# does not constitute a usage. This matches the comment-stripping shape
# of `scripts/test_no_pii_in_crash_log.sh`.
#
# Wire-up to CI: this script is NOT yet referenced in `.github/workflows/`.
# D1 (the Argon2id implementation session, per ADR-0018 § Followups) adds
# the CI job that invokes it. Until then, the script is dormant and the
# self-test harness at `scripts/test_test_no_pbkdf2_in_backup_after_tbn2.sh`
# is the only automated runner.
#
# Reference shape: `scripts/test_no_pii_in_crash_log.sh` (ADR-0009 fitness
# function #5) and `scripts/test_no_duplicate_private_test_class_names.sh`
# (ADR-0017 § Lesson 2). This script borrows the comment-stripping awk
# pattern from the former and the multi-scan-root structure from the
# latter.

set -euo pipefail

ROOT="${1:-.}"

# Source trees that the backup cipher lives in. Match the structure
# named in ADR-0018 § Decision § Library choice: commonMain for the
# expect interface, jvmMain + androidMain for the actuals.
SCAN_ROOTS=(
    "$ROOT/shared/src/commonMain/kotlin/app/toebeans/core/backup"
    "$ROOT/shared/src/jvmMain/kotlin/app/toebeans/core/backup"
    "$ROOT/shared/src/androidMain/kotlin/app/toebeans/core/backup"
)

# Step 1 — gate on TBN2 presence. If no Kotlin file under any scan root
# mentions "TBN2", the cipher has not rotated yet and the check is a
# no-op. Exit 0 with a status message.
tbn2_present=0
for scan_root in "${SCAN_ROOTS[@]}"; do
    if [ ! -d "$scan_root" ]; then
        continue
    fi
    if grep -rlE '"TBN2"|TBN2[[:space:]]*=' "$scan_root" >/dev/null 2>&1; then
        tbn2_present=1
        break
    fi
done

if [ "$tbn2_present" -eq 0 ]; then
    echo "ℹ test_no_pbkdf2_in_backup_after_tbn2: TBN2 envelope not present yet; no-op."
    echo "  This check activates once D1 lands the Argon2id cipher per ADR-0018."
    exit 0
fi

# Step 2 — enforce. Scan every .kt file under the scan roots; flag any
# PBKDF2 occurrence that is not allow-listed.
violations=0
flagged_files=()

# Awk script: emit "linenum:content" for every line that:
#   - Matches /pbkdf2/i (case-insensitive)
#   - AND is NOT a comment-only line
#   - AND does NOT contain a line-level allow marker
# Plus: skip the entire file if its top-of-file comment block contains
# a file-level allow marker. The file-level check happens BEFORE awk
# (it's a single grep). Awk handles the per-line logic.

# Returns 0 if the file has a file-level allow marker, 1 otherwise.
has_file_level_allow() {
    local file="$1"
    # Scan the top-of-file comment block: contiguous lines from line 1
    # whose first non-whitespace is // or * or /*. Stop at the first
    # non-comment line. If any of those header lines contains the
    # marker, the file is allow-listed.
    awk '
        BEGIN { in_header = 1 }
        in_header == 0 { exit }
        /^[[:space:]]*\/\/|^[[:space:]]*\*|^[[:space:]]*\/\*/ {
            if ($0 ~ /FITNESS-ALLOW-PBKDF2-FILE:/) {
                print "allow"
                exit
            }
            next
        }
        { in_header = 0 }
    ' "$file" | grep -q '^allow$'
}

for scan_root in "${SCAN_ROOTS[@]}"; do
    if [ ! -d "$scan_root" ]; then
        continue
    fi

    # Find all .kt files under this scan root. -print0 + while-read-d
    # is the safest pattern; the backup module has no paths with
    # spaces today but defensive habits cost nothing.
    while IFS= read -r -d '' kt_file; do
        if has_file_level_allow "$kt_file"; then
            continue
        fi

        # Per-line check. The same comment-stripping awk pattern from
        # test_no_pii_in_crash_log.sh, plus the line-level allow marker.
        matches=$(awk '
            /^[[:space:]]*\/\// { next }
            /^[[:space:]]*\*([^\/]|$)/ { next }
            /^[[:space:]]*\/\*/ { next }
            /^[[:space:]]*\*\/[[:space:]]*$/ { next }
            /FITNESS-ALLOW-PBKDF2:/ { next }
            tolower($0) ~ /pbkdf2/ { printf "%d:%s\n", NR, $0 }
        ' "$kt_file")

        if [ -n "$matches" ]; then
            echo "::error::FITNESS-NO-PBKDF2-AFTER-TBN2 violation in $kt_file:"
            echo "$matches" | sed 's/^/    /'
            flagged_files+=("$kt_file")
            violations=$((violations + 1))
        fi
    done < <(find "$scan_root" -type f -name '*.kt' -print0)
done

if [ "$violations" -gt 0 ]; then
    echo
    echo "Total violations: $violations across ${#flagged_files[@]} file(s)."
    echo
    echo "Per ADR-0018, PBKDF2 references in the backup module are limited"
    echo "to the explicitly allow-listed TBN1 decrypt path. To resolve:"
    echo
    echo "  1. If the flagged file is the TBN1 read-only decrypt path,"
    echo "     add a file-level marker comment to the top of the file:"
    echo "       // FITNESS-ALLOW-PBKDF2-FILE: TBN1 legacy decrypt only"
    echo
    echo "  2. If a single line legitimately needs PBKDF2 in an otherwise"
    echo "     TBN2-clean file (rare), add a trailing line-level marker:"
    echo "       <code> // FITNESS-ALLOW-PBKDF2: <reason>"
    echo
    echo "  3. If neither applies, remove the PBKDF2 reference. The TBN2"
    echo "     cipher uses Argon2id (RFC 9106) per ADR-0018."
    exit 1
fi

echo "✓ No-PBKDF2-after-TBN2 fitness function passed."
