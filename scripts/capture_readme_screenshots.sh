#!/usr/bin/env bash
# Capture README screenshots on toebeans-pixel7 emulator with demo data seeded.
# Requires: ANDROID_HOME, JDK 17, booted emulator, installDebug already run.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
PKG="app.toebeans.android"
ACTIVITY="app.toebeans.android/.MainActivity"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/screenshots"
TMP="/data/local/tmp/toebeans-screenshots"

# grep -q closes the pipe early. With pipefail that looks like "no device"
# even when adb already printed one. \t is not a tab in POSIX grep.
if ! "$ADB" devices | grep -E '[[:space:]]device$' >/dev/null; then
  echo "No emulator/device connected. Boot toebeans-pixel7 first." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
"$ADB" shell mkdir -p "$TMP"

wait_for_boot() {
  "$ADB" wait-for-device
  for _ in $(seq 1 60); do
    if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Emulator did not finish booting in time." >&2
  exit 1
}

# Poll until a node containing $1 exists. On a software-rendered emulator the
# first painted frame can lag the activity start by tens of seconds; without
# this wait, capture grabs the splash screen.
wait_text() {
  local text="$1"
  local timeout="${2:-60000}"
  local end=$((SECONDS + timeout / 1000))
  while (( SECONDS < end )); do
    "$ADB" shell uiautomator dump "$TMP/window.xml" >/dev/null 2>&1 || true
    if "$ADB" exec-out cat "$TMP/window.xml" 2>/dev/null | tr -d '\r' \
      | grep -qF "$text"; then
      return 0
    fi
    sleep 1.5
  done
  echo "UI text never appeared: $text" >&2
  return 1
}

tap_text() {
  local text="$1"
  local timeout="${2:-8000}"
  local end=$((SECONDS + timeout / 1000))
  while (( SECONDS < end )); do
    "$ADB" shell uiautomator dump "$TMP/window.xml" >/dev/null 2>&1 || true
    local bounds
    bounds=$("$ADB" exec-out cat "$TMP/window.xml" 2>/dev/null | tr -d '\r' | python3 -c "
import sys, re, xml.etree.ElementTree as ET
text = sys.argv[1]
try:
    root = ET.fromstring(sys.stdin.read())
except ET.ParseError:
    sys.exit(1)
# Exact matches first: a substring pass would tap 'Your pets' when asked
# for the 'Pets' tab, or a Today filter chip when asked for a pet row.
for exact in (True, False):
    for node in root.iter('node'):
        for attr in ('text', 'content-desc'):
            value = node.get(attr) or ''
            matched = (value == text) if exact else (text in value)
            if matched:
                b = node.get('bounds')
                if b:
                    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
                    if m:
                        x1,y1,x2,y2 = map(int, m.groups())
                        print((x1+x2)//2, (y1+y2)//2)
                        sys.exit(0)
sys.exit(1)
" "$text" 2>/dev/null) || bounds=""
    if [[ -n "$bounds" ]]; then
      read -r x y <<<"$bounds"
      "$ADB" shell input tap "$x" "$y"
      sleep 2
      "$ADB" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
      return 0
    fi
    sleep 0.5
  done
  echo "Could not find UI text: $text" >&2
  return 1
}

capture() {
  local name="$1"
  sleep 1
  "$ADB" exec-out screencap -p >"$OUT_DIR/$name"
  echo "Wrote $OUT_DIR/$name"
}

wait_for_boot

# Fresh install state for welcome dialog
"$ADB" shell pm clear "$PKG" >/dev/null
# API 33+ shows a system permission sheet before the welcome choice finishes.
# Grant it here so the capture stays on the app screens.
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
"$ADB" shell appops set "$PKG" SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
"$ADB" shell am start -n "$ACTIVITY" >/dev/null
wait_text "Welcome to toebeans" 90000
sleep 2
capture "00-welcome-dialog.png"

tap_text "Load demo data" 12000 || tap_text "Load Rufus and Luna demo data" 12000
wait_text "Today's doses" 90000
sleep 2
capture "01-home-today.png"

tap_text "Pets" 8000
wait_text "Add pet" 30000
sleep 2
capture "02-pets-list.png"

tap_text "Luna" 8000
wait_text "Log dose now" 30000
sleep 2
capture "03-pet-detail-luna.png"

# Stacked screens hide the bottom nav; return to a tab root first.
"$ADB" shell input keyevent KEYCODE_BACK
sleep 1

tap_text "Settings" 8000
sleep 1
capture "04-settings.png"

tap_text "Reminders" 8000
sleep 1
capture "05-reminders.png"

# Schedule detail (tap schedule row on Reminders; avoid Pets-tab Luna row)
if tap_text "twice daily" 5000 || tap_text "3650 days" 5000 || tap_text "Methimazole" 5000; then
  sleep 1
  capture "07-schedule-detail.png"
  "$ADB" shell input keyevent KEYCODE_BACK
  sleep 1
fi

tap_text "Today" 8000
sleep 1

# Log dose on home (button label is "Log dose" on Today rows)
if tap_text "Log dose" 8000; then
  wait_text "Given" 30000
  sleep 2
  capture "06-home-dose-logged.png"
fi

echo "Done. Screenshots in $OUT_DIR"
