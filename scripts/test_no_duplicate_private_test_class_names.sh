#!/usr/bin/env bash
# Fitness function #7 — No duplicate top-level `private class` names within a
# single Kotlin package across the test source roots.
#
# Why this exists: under Kotlin K2, two files in the same package that each
# declare a top-level `private class <Name>` with identical <Name> cause the
# compiler to emit "Cannot access ... private in file" on every usage site,
# even though file-level `private` is documented as file-local. The pattern
# fires regardless of whether the usage site is in the declaring file or in
# another file in the same package.
#
# Empirical reference: ADR-0017 § Lesson 2. The original incident was
# `BackupImporterTest.kt` and `BackupAggregatorTest.kt` (both landed in commit
# 4ee14a2) each declaring `InMemoryPetRepo`, `InMemoryMedRepo`,
# `InMemoryScheduleRepo`, and `InMemoryDoseEventRepo` as top-level
# `private class` in package `app.toebeans.core.backup`. CI run 26039386098
# reported all 24 usage sites in `BackupImporterTest.kt` as "Cannot access
# ... private in file." Commit fa61a40 fixed it by renaming the importer
# fakes to `Importer*Repo`.
#
# How to fix a violation: rename one of the colliding classes with a
# containing-test-name prefix in the shape <TestName><Role>. For the
# original incident, `Aggregator*Repo` and `Importer*Repo` would each have
# worked; only one side needs to move.
#
# Scope: scans every .kt file under the Kotlin test source roots that exist
# in this repo. The roots are listed below; missing roots are skipped
# silently (KMP source-set layout is not guaranteed to include every
# possible target).
#
# Usage: bash scripts/test_no_duplicate_private_test_class_names.sh [repo-root]
#   repo-root defaults to "." so the script can run from the worktree root.

set -euo pipefail

ROOT="${1:-.}"

# Test source roots scanned for the duplicate-private pattern. Order does
# not matter; the script sorts before detecting duplicates.
TEST_ROOTS=(
    "shared/src/commonTest"
    "shared/src/jvmTest"
    "shared/src/androidTest"
    "androidApp/src/test"
    "androidApp/src/androidTest"
)

# Working files. Cleaned up by the EXIT trap below so that an interrupt
# (Ctrl-C, CI step kill) does not leak temp files into /tmp.
rows=$(mktemp)
sorted=$(mktemp)
trap 'rm -f "$rows" "$sorted"' EXIT

# Per .kt file: emit one TSV row per top-level `private class <Name>` with
# the file's package declaration and the file path.
#
# Pattern notes:
#   - `^private[[:space:]]+class[[:space:]]+[A-Za-z_]` anchors at column 0,
#     allows whitespace variation between the modifier, the `class` keyword,
#     and the identifier, and excludes nested classes (which would have
#     leading indentation).
#   - The class name itself is the contiguous identifier characters that
#     follow `class ` (Kotlin: `[A-Za-z_][A-Za-z0-9_]*`). Anything trailing
#     the name (type parameters, primary constructors, supertypes, opening
#     brace) is dropped by truncating at the first non-identifier character.
#   - `^private[[:space:]]+open[[:space:]]+class` and other modifier
#     combinations are NOT matched on purpose. The K2 collision documented
#     in ADR-0017 was specifically about `private class`. If a future
#     incident shows that `private open class` collides similarly, extend
#     the pattern and update ADR-0017.
extract_rows() {
    local dir="$1"
    [ -d "$dir" ] || return 0

    while IFS= read -r kt; do
        local pkg
        pkg=$(awk 'BEGIN{p="<no-package>"} /^package[[:space:]]+/ {p=$2; gsub(/;[[:space:]]*$/, "", p); print p; exit} END{if (NR==0) print p}' "$kt")
        [ -z "$pkg" ] && pkg="<no-package>"

        awk -v pkg="$pkg" -v file="$kt" '
            /^private[[:space:]]+class[[:space:]]+[A-Za-z_]/ {
                if (match($0, /^private[[:space:]]+class[[:space:]]+[A-Za-z_][A-Za-z0-9_]*/)) {
                    seg = substr($0, RSTART, RLENGTH)
                    n = split(seg, parts, /[[:space:]]+/)
                    name = parts[n]
                    if (name != "") {
                        printf "%s\t%s\t%s\n", pkg, name, file
                    }
                }
            }
        ' "$kt"
    done < <(find "$dir" -name '*.kt' -type f)
}

for root in "${TEST_ROOTS[@]}"; do
    extract_rows "$ROOT/$root"
done > "$rows"

# Sort by (package, classname, file). Adjacent rows that share (package,
# classname) become groups; each group of size > 1 is a violation.
sort "$rows" > "$sorted"

# awk emits violations to stderr and exits 1 on any duplicate. The output
# shape mirrors test_no_pii_in_crash_log.sh: one "::error::" annotation per
# duplicate pair so the GitHub Actions log surfaces each violation as a
# distinct entry, then the participating file paths indented under it.
awk -F'\t' '
    {
        key = $1 SUBSEP $2
        if (key == prev_key) {
            if (!(key in seen)) {
                printf "::error::FITNESS-NO-DUPLICATE-PRIVATE-TEST-CLASS-NAMES duplicate (package=%s, classname=%s)\n", $1, $2 > "/dev/stderr"
                printf "    - %s\n", prev_file > "/dev/stderr"
                seen[key] = 1
                violations++
            }
            printf "    - %s\n", $3 > "/dev/stderr"
        }
        prev_key = key
        prev_file = $3
    }
    END {
        if (violations > 0) {
            printf "\nTotal duplicate (package, classname) pairs: %d\n", violations > "/dev/stderr"
            printf "\nUnder Kotlin K2 the duplicate-top-level-private-class-name pattern emits\n" > "/dev/stderr"
            printf "\"Cannot access ... private in file\" on every usage site even though\n" > "/dev/stderr"
            printf "file-level private is documented as file-local. See ADR-0017 (Lesson 2)\n" > "/dev/stderr"
            printf "and the original ADR-0016 CI run 26039386098 for the incident shape.\n" > "/dev/stderr"
            printf "\nTo fix, rename one of the colliding classes with a containing-test-name\n" > "/dev/stderr"
            printf "prefix in the shape <TestName><Role> (for example, ExportInMemoryPetRepo\n" > "/dev/stderr"
            printf "and ImportInMemoryPetRepo when the colliding files are ExportBackupViewModelTest\n" > "/dev/stderr"
            printf "and ImportBackupViewModelTest). Only one side of each pair needs to move.\n" > "/dev/stderr"
            exit 1
        }
    }
' "$sorted"

echo "✓ No-duplicate-private-test-class-names fitness function passed."
