#!/usr/bin/env bash
# Boot Toebeans on an emulator/device for hands-on QA. Automates emulator wait,
# installDebug, optional fresh state, and launch; prints navigation-only steps.
#
# Usage (from repo root):
#   bash scripts/manual_qa_boot.sh [fresh|warm] [--boot-avd NAME] [--open-style-lab]
#
# Env:
#   TOEBEANS_AVD          — emulator AVD name (default: toebeans-pixel7)
#   ANDROID_SERIAL        — adb serial when multiple devices
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCENARIO="fresh"
BOOT_AVD=0
AVD_NAME="${TOEBEANS_AVD:-toebeans-pixel7}"
OPEN_STYLE_LAB=0

for arg in "$@"; do
  case "$arg" in
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
  esac
done

while [[ $# -gt 0 ]]; do
  case "$1" in
    --boot-avd) BOOT_AVD=1; [[ $# -gt 1 && ! "$2" =~ ^-- ]] && { AVD_NAME="$2"; shift; }; shift ;;
    --open-style-lab) OPEN_STYLE_LAB=1; shift ;;
    fresh|warm) SCENARIO="$1"; shift ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
PKG="app.toebeans.android"
ACTIVITY="app.toebeans.android/.MainActivity"

adb_cmd() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    "$ADB" -s "$ANDROID_SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

has_device() {
  adb_cmd devices | grep -qE '\tdevice$'
}

wait_for_boot() {
  adb_cmd wait-for-device
  for _ in $(seq 1 60); do
    if [[ "$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Emulator did not finish booting in time." >&2
  exit 1
}

maybe_boot_emulator() {
  if has_device; then
    return 0
  fi
  if [[ "$BOOT_AVD" -eq 0 ]]; then
    echo "No device connected. Re-run with --boot-avd or start emulator manually." >&2
    exit 1
  fi
  if [[ ! -x "$EMULATOR" ]]; then
    echo "Emulator not found at $EMULATOR (set ANDROID_HOME)." >&2
    exit 1
  fi
  echo "Starting AVD $AVD_NAME in background …" >&2
  "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-load >/dev/null 2>&1 &
  wait_for_boot
}

install_and_launch() {
  cd "$ROOT"
  echo "Installing debug APK …" >&2
  ./gradlew :androidApp:installDebug -q
  if [[ "$SCENARIO" == "fresh" ]]; then
    echo "Clearing app data (fresh install) …" >&2
    adb_cmd shell pm clear "$PKG" >/dev/null
  fi
  adb_cmd shell am start -n "$ACTIVITY" >/dev/null
}

print_nav() {
  cat <<EOF

══════════════════════════════════════════════════════════════
  Toebeans manual QA — scenario: ${SCENARIO}
  (App should be open on the emulator/device now.)
══════════════════════════════════════════════════════════════

EOF
  case "$SCENARIO" in
    fresh)
      cat <<'EOF'
1. If welcome / demo dialog appears, choose path for your test (e.g. empty vs demo).
2. **Pets** tab → open a pet → confirm detail loads.
3. Add or open a **medication** → create/edit a **schedule** → save.
4. **Reminders** tab → confirm schedule appears.
5. (When materializer lands) After save, verify pending dose rows / notification within soak window.

Pass: core nav works on fresh install; no crash on schedule save.
EOF
      ;;
    warm)
      cat <<'EOF'
App launched with **existing** data (no pm clear).

1. Confirm home/today shows expected state from prior session.
2. Exercise the change under test (e.g. boot rehydration, doc-only flows N/A here).
3. Force-stop and relaunch if testing persistence across process death.

Pass: warm state matches expectation for the PR under test.
EOF
      ;;
  esac
  if [[ "$OPEN_STYLE_LAB" -eq 1 ]]; then
    cat <<EOF

Style lab (docs-only):
  open "$ROOT/docs/style-lab/index.html"
  Toggle variant packs; confirm CSS updates. Do not edit DECISIONS.md “Chosen” until sign-off.
EOF
  fi
  echo
}

main() {
  echo "Repo: $ROOT" >&2
  echo "Scenario: $SCENARIO" >&2
  maybe_boot_emulator
  wait_for_boot
  install_and_launch
  if [[ "$OPEN_STYLE_LAB" -eq 1 ]]; then
    if command -v open >/dev/null 2>&1; then
      open "$ROOT/docs/style-lab/index.html" 2>/dev/null || true
    fi
  fi
  print_nav
}

main
