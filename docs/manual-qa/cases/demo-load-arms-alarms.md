# Demo load arms the 72-hour alarms

**Source:** PR #132 (2876963). Tapping Load demo data persists the demo rows
through the repository contracts and registers real alarms for the next 72
hours of pending doses.

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

1. Run the cold-start block above from the repository root. The emulator boots
   with a fresh install and the app launches to first launch.
2. In the first-launch dialog, tap Load demo data.
3. Open Today and find the next demo dose time that falls inside the next 72
   hours. The demo schedules use fixed local times, for example Luna's
   Methimazole at 8:00 am and 8:00 pm, and Rufus's Fish oil at 6:00 pm.
4. Wait until that time, with the app in the background or closed.
5. Watch for the dose notification.

## Expected result

- One dose notification appears at the demo schedule time. The title names the
  demo pet and medication (for example, Luna, Methimazole) and ends with the
  word due. The text reads Tap to log dose.
- Today lists the demo rows: two pets (Rufus the dog, Luna the cat), three
  medications per pet, and due dose rows at the fixed demo times.
