#!/usr/bin/env bash
# ADR-0011: notify() in AndroidNotificationActuator.kt must follow
# areNotificationsEnabled(). A notify earlier in the file is a silent drop.
#
# ponytail: source order, not a control-flow graph. A later function that
# notifies without its own check still passes if the check appears above it.
#
# Usage: bash scripts/test_post_notifications_silent_failure_guard.sh [file]
#   Default file is the actuator. The self-test passes a fixture path.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REL="androidApp/src/main/kotlin/app/toebeans/android/notifications/AndroidNotificationActuator.kt"
ARG="${1:-$ROOT}"
if [[ -d "$ARG" ]]; then
  TARGET="$ARG/$REL"
else
  TARGET="$ARG"
fi

if [[ ! -f "$TARGET" ]]; then
  echo "FAIL missing $TARGET"
  exit 1
fi

python3 - "$TARGET" <<'PY'
import sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
check = text.find("areNotificationsEnabled")
notifies = []
start = 0
needle = ".notify("
while True:
    i = text.find(needle, start)
    if i < 0:
        break
    notifies.append(i)
    start = i + len(needle)
if not notifies:
    print(f"FAIL {path}: no .notify( call")
    sys.exit(1)
if check < 0 or any(i < check for i in notifies):
    print(f"FAIL {path}: .notify( appears before areNotificationsEnabled()")
    sys.exit(1)
print(f"OK {path}: areNotificationsEnabled() precedes .notify(")
PY
