# Remove demo data from Settings

**Source:** S3 demo-clear affordance, sprint/demo-clear-2026-11-10. Device session: not run yet. Device boxes stay unchecked in the pull request body.

## Cold start

Run this block from the repository root. Do not shorten it.

```bash
for _jdk in \
  /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
  [[ -x "$_jdk/bin/java" ]] && export JAVA_HOME="$_jdk" && break
done
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JDK 17 not found. Run: brew install openjdk@17" >&2
  exit 1
fi
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
"$ANDROID_HOME/emulator/emulator" -avd toebeans-pixel7
"$ANDROID_HOME/platform-tools/adb" wait-for-device
./gradlew :androidApp:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n app.toebeans.android/.MainActivity
```

## Steps

1. Run the cold-start block above from the repository root.
2. Tap "Load demo data" on the first-launch dialog so Rufus and Luna appear.
3. Open the Settings tab.
4. In the Data card, tap "Remove demo data".
5. Read the confirmation dialog, then tap "Remove".
6. Open the Pets tab.
7. Optional second pass: tap "Cancel" instead in step 5 and confirm Rufus and Luna stay.

## Expected result

After "Remove", Rufus, Luna, their six medications, and every demo schedule and dose row are gone; the Pets tab shows only pets you created yourself (or the empty state). No demo reminder fires afterwards. After "Cancel", the demo data is untouched. The first-launch dialog does not reappear on the next app start.
