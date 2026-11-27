# Wide-window navigation reach

**Source:** operator decision 2026-11-23 to require tablet support, and ADR-0023 Decompose navigation. Device session: PENDING (the operator performs the in-app steps).

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
"$ANDROID_HOME/emulator/emulator" -avd toebeans-tablet
"$ANDROID_HOME/platform-tools/adb" wait-for-device
./gradlew :androidApp:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am force-stop app.toebeans.android
"$ANDROID_HOME/platform-tools/adb" shell am start -n app.toebeans.android/.MainActivity
"$ANDROID_HOME/platform-tools/adb" shell screencap -p /sdcard/toebeans-foreground.png
```

If `toebeans-tablet` does not exist, create it before the cold-start block with `avdmanager create avd -n toebeans-tablet -k 'system-images;android-35;google_apis;arm64-v8a' --device 'pixel_tablet'`, accepting the default skin. Do not use a phone image for this case.

## Steps

1. Run the cold-start block above from the repository root and confirm the screencap shows toebeans in the foreground. If it does not, repeat the force-stop and start commands before continuing.
2. With the app in landscape on the tablet, check all five destinations are visible: Today, Med List, Pets, Settings, History.
3. Tap each destination once and check the matching screen appears and its destination is highlighted.
4. From Today, load demo data if the store is empty, then tap an owner dose Edit action. Check the route reaches medication edit or schedule create, matching the dose record.
5. Press the system back key and check the app returns to Today and the shared navigation model agrees with the screen.
6. Open Pets, scroll the list down, switch to Med List, then return to Pets. Check the list returns to the prior scroll position.
7. Repeat steps 3 to 6 with the tablet in portrait, using one hand from the lower half of the screen for every primary action.
8. Check that no primary action or time-critical information is centred out of thumb reach, and that the bottom or trailing control is reachable without shifting the device.

## Expected result

All five destinations are reachable on both orientations. The dose Edit action reaches the correct route, and a system back press returns to the prior screen without leaving the navigation model out of sync. Returning to Pets restores the prior scroll position. Every primary action used while giving a dose sits within thumb reach from the lower half of the display in both orientations.
