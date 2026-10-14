#!/usr/bin/env bash
# Self-test for scripts/test_post_notifications_silent_failure_guard.sh.
set -euo pipefail

REPO_ROOT="${1:-.}"
SCRIPT="$REPO_ROOT/scripts/test_post_notifications_silent_failure_guard.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "FAIL fitness-function script not found at $SCRIPT"
  exit 1
fi

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

good="$tmpdir/good.kt"
bad="$tmpdir/bad.kt"
cat >"$good" <<'EOF'
fun show() {
    if (!notificationManager.areNotificationsEnabled()) return
    notificationManager.notify(1, notification)
}
EOF
cat >"$bad" <<'EOF'
fun show() {
    notificationManager.notify(1, notification)
}
EOF

if ! bash "$SCRIPT" "$good"; then
  echo "FAIL good fixture should pass"
  exit 1
fi
if bash "$SCRIPT" "$bad"; then
  echo "FAIL bad fixture should fail"
  exit 1
fi
echo "PASS post-notifications silent-failure guard self-test"
