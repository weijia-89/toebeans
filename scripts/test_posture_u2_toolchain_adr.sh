#!/usr/bin/env bash
# Fitness function - U2: never bump a major toolchain version silently.
#
# The repository does not carry a per-tool ADR table in gradle.properties or in
# gradle/libs.versions.toml. This checker therefore keeps the small toolchain
# record next to the rule (TOOLCHAIN_RECORD below). Each row names a catalog
# key, the major version that was reviewed, the ADR that recorded the decision,
# and the tool token that ADR's revisit conditions must discuss.
#
# Observable properties:
#   - every recorded key still exists in the catalog with the recorded major
#   - no gradle.properties override changes a recorded toolchain major
#   - the named ADR file exists and has revisit conditions naming the tool
#
# A comment-only revisit heading does not count. HTML comments are removed.
#
# Usage: bash scripts/test_posture_u2_toolchain_adr.sh [repo-root]

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

CATALOG="$ROOT/gradle/libs.versions.toml"
if [[ ! -f "$CATALOG" ]]; then
    echo "FAIL missing required file: $CATALOG"
    exit 1
fi

# key | reviewed major | ADR id | token the revisit note must name
TOOLCHAIN_RECORD=(
    'agp|9|0015|AGP'
    'kotlin|2|0015|KGP'
)

catalog_text=$(awk '!/^[[:space:]]*#/ { print }' "$CATALOG")
props_text=""
if [[ -f "$ROOT/gradle.properties" ]]; then
    props_text=$(awk '!/^[[:space:]]*#/ { print }' "$ROOT/gradle.properties")
fi

catalog_version() {
    local key="$1"
    sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" <<< "$catalog_text" | head -n 1
}

strip_html_comments() {
    sed -E 's/<!--.*-->//g' "$1"
}

for row in "${TOOLCHAIN_RECORD[@]}"; do
    IFS='|' read -r key recorded_major adr_id token <<< "$row"

    found_version=$(catalog_version "$key")
    if [[ -z "$found_version" ]]; then
        fail "toolchain $key is recorded but missing from gradle/libs.versions.toml"
        continue
    fi
    found_major="${found_version%%.*}"
    if [[ "$found_major" != "$recorded_major" ]]; then
        fail "toolchain $key has a silent major version bump: recorded $recorded_major, catalog $found_major"
    else
        ok "toolchain $key major $found_major agrees with the record"
    fi

    if [[ -n "$props_text" ]]; then
        override=$(sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"?([0-9]+)[^\"]*/\1/p" <<< "$props_text" | head -n 1)
        if [[ -n "$override" && "$override" != "$recorded_major" ]]; then
            fail "toolchain $key has a silent major version bump in gradle.properties: $override"
        fi
    fi

    adr_file=$(find "$ROOT/docs/adr" -maxdepth 1 -type f -name "${adr_id}-*.md" -print -quit 2>/dev/null)
    if [[ -z "$adr_file" ]]; then
        fail "ADR $adr_id for toolchain $key is missing"
        continue
    fi

    clean_adr=$(strip_html_comments "$adr_file")
    revisit=$(awk '
        BEGIN { inside = 0 }
        tolower($0) ~ /^##[[:space:]]+revisit conditions/ { inside = 1; next }
        inside && /^##[[:space:]]/ { exit }
        inside { print }
    ' <<< "$clean_adr")

    if [[ -z "$(grep -v '^[[:space:]]*$' <<< "$revisit")" ]]; then
        fail "ADR $adr_file has no revisit conditions section for toolchain $key"
        continue
    fi
    if ! grep -qiF -- "$token" <<< "$revisit"; then
        fail "ADR $adr_file does not discuss $token in its revisit conditions for toolchain $key"
        continue
    fi
    ok "ADR $adr_file revisits $key via $token"
done

if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "$FAILED U2 toolchain-record check(s) failed."
    exit 1
fi

echo ""
echo "U2 toolchain-record fitness function passed."
