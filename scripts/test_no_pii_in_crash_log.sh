#!/usr/bin/env bash
# Fitness function #6 — Local crash-log handler MUST NOT reference domain data.
#
# ADR-0009 commits to the property that the local crash-log file contains:
#   - The exception stack trace
#   - Build metadata (app version, Android SDK, device model)
#   - Thread name + exception class + message
#
# And explicitly does NOT contain:
#   - Pet, Medication, Schedule, SchedulePhase, or DoseEvent data
#   - Repository / DAO state
#   - SharedPreferences or SQLDelight contents
#
# That property is currently enforced by a humans-don't-write-bugs argument,
# which is not a fitness function. This script makes it mechanical: it greps
# the crash-handler source for any reference to domain-model package names,
# repository symbols, or DAO symbols, and fails the build if any appear.
#
# False-positive guard: we strip ALL comment lines (KDoc block bodies,
# leading // line comments) before grepping, so a doc comment that says
# "we do NOT write Pet.name" doesn't trip the check.
#
# This is the M1 follow-on to slice-4 of the cold-review sweep (1815d1c)
# and the named work item under ROADMAP § Milestone 1.

set -euo pipefail

ROOT="${1:-.}"

# The crash-handler source lives here. If you add another file to this
# subtree, add it to the array below. The whole point is that EVERY file
# that contributes to the crash-log surface is under this check.
HANDLER_SOURCES=(
    "$ROOT/androidApp/src/main/kotlin/app/toebeans/android/crash/LocalCrashLog.kt"
)

# Symbols whose presence in the crash-handler source would constitute a PII
# leak surface. These are deliberately broad: a defensive reviewer would
# rather false-positive on a benign reference (and explicitly suppress it
# with a justification) than miss a real leak.
#
# Each pattern is an extended regex. POSIX awk does NOT understand \b
# word boundaries, so we express them explicitly as
# (^|[^A-Za-z0-9_]) ... ([^A-Za-z0-9_]|$). Verbose, but portable across
# BSD awk on macOS and gawk on CI Ubuntu.
#
# They are matched against non-comment lines only (see strip_comments).
WB_START='(^|[^A-Za-z0-9_])'
WB_END='([^A-Za-z0-9_]|$)'

FORBIDDEN_PATTERNS=(
    # Domain model class names — exact-word match so we don't false-trip on
    # words that happen to contain "Pet" as a substring (e.g. "Petition").
    "${WB_START}Pet${WB_END}"
    "${WB_START}Medication${WB_END}"
    "${WB_START}Schedule${WB_END}"
    "${WB_START}SchedulePhase${WB_END}"
    "${WB_START}DoseEvent${WB_END}"
    "${WB_START}DoseStatus${WB_END}"
    "${WB_START}ScheduledDose${WB_END}"
    "${WB_START}BackupExport${WB_END}"
    # Repository / DAO surface — anything ending in Repository or Dao.
    # The leading [A-Za-z] keeps us from matching the bare word; we want
    # PetRepository, MedicationRepository, etc.
    "[A-Za-z]Repository${WB_END}"
    "[A-Za-z]Dao${WB_END}"
    # Persistence / state surface
    "${WB_START}SharedPreferences${WB_END}"
    "${WB_START}DataStore${WB_END}"
    "${WB_START}SQLDelight${WB_END}"
    "${WB_START}ToebeansDatabase${WB_END}"
    # Direct package references to the core models / data packages.
    'app\.toebeans\.core\.model\.'
    'app\.toebeans\.core\.data\.'
    # NOTE: a generic `\.toString\(\)` check was considered and dropped.
    # It false-positives on safe JVM-builtin .toString() calls (StringWriter,
    # Throwable, primitives) which the handler legitimately uses. The
    # domain-class word-boundary rules above are the actual enforcement:
    # you cannot .toString() a Pet without first referencing Pet by name,
    # which the per-class rules already block.
)

violations=0

# Strip Kotlin comments before grepping. We treat:
#   * Lines that are entirely whitespace + // ... as comment
#   * Lines that are entirely whitespace + * ... (KDoc body lines) as comment
#   * Lines that are entirely whitespace + /* ... or ... */ markers as comment
#
# We do NOT attempt to handle inline trailing comments on a code line. A
# line like `Pet.name // do not reference this` would still be a violation,
# which is what we want — if you ever write Pet.name in the handler you're
# already in trouble, even if you've left yourself a note saying you know.
strip_comments() {
    # sed reads stdin, removes:
    #   - lines whose first non-whitespace is //
    #   - lines whose first non-whitespace is *  (KDoc body)
    #   - lines whose first non-whitespace is /* (KDoc opening)
    #   - lines that consist of whitespace + */
    sed -E '/^[[:space:]]*\/\//d; /^[[:space:]]*\*([^\/]|$)/d; /^[[:space:]]*\/\*/d; /^[[:space:]]*\*\/[[:space:]]*$/d'
}

for src in "${HANDLER_SOURCES[@]}"; do
    if [ ! -f "$src" ]; then
        echo "::error::FITNESS-NO-PII-IN-CRASH-LOG configuration error — expected source file not found: $src"
        echo "If LocalCrashLog.kt has moved, update HANDLER_SOURCES in this script."
        exit 2
    fi

    # Strip comments to a temp file (rather than piping through grep, which
    # would lose original line numbers). We rebuild line numbers below using
    # awk so the violation report points at the real source.
    nocomments=$(mktemp)
    strip_comments < "$src" > "$nocomments"

    for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
        # grep -nE gives us "linenum:content" but those line numbers are
        # against the stripped file, not the original. To get real line
        # numbers we use awk over the original file with the same regex
        # and skip lines that look like comments.
        matches=$(awk -v pat="$pattern" '
            /^[[:space:]]*\/\// { next }
            /^[[:space:]]*\*([^\/]|$)/ { next }
            /^[[:space:]]*\/\*/ { next }
            /^[[:space:]]*\*\/[[:space:]]*$/ { next }
            $0 ~ pat { printf "%d:%s\n", NR, $0 }
        ' "$src")

        if [ -n "$matches" ]; then
            echo "::error::FITNESS-NO-PII-IN-CRASH-LOG violation in $src — pattern '$pattern' found:"
            echo "$matches" | sed 's/^/    /'
            violations=$((violations+1))
        fi
    done

    rm -f "$nocomments"
done

if [ "$violations" -gt 0 ]; then
    echo
    echo "Total violations: $violations"
    echo
    echo "Per ADR-0009, the local crash-log handler must not reference domain"
    echo "model or repository symbols. If a flagged reference is truly safe and"
    echo "necessary (rare — talk yourself out of it first), document the"
    echo "rationale in an ADR amendment and add the file/line to a per-file"
    echo "allowlist in this script. Do NOT silently delete the check."
    exit 1
fi

echo "✓ No-PII-in-crash-log fitness function passed."
