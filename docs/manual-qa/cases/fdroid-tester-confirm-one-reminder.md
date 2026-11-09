# Confirm one reminder from the F-Droid path

**Source:** docs/fdroid-tester-tutorial.md. Device session: SKIPPED on 2026-11-08. This run does not publish a release.

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
2. Install the published build with Obtainium, or add the small F-Droid repo in Droid-ify.
3. Open toebeans.
4. Add one pet, one medication, and one schedule.
5. Wait until the reminder time.

## Expected result

One reminder shows for that schedule.
