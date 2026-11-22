# Emergency card contacts dial and disclaim

**Source:** feat(emergency) static emergency resources card. Device session: PENDING (run at review).

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
2. Open toebeans. The Today screen shows a collapsed Emergency card.
3. Tap the Emergency card to expand it.
4. Check the two hotline rows show "24/7" and a fee note, a find-an-emergency-vet guidance line, and the "toebeans does not provide medical advice" line.
5. Tap the ASPCA Animal Poison Control row, then cancel the dialer.
6. Tap the Pet Poison Helpline row, then cancel the dialer.
7. Open Settings, tap the "Emergency resources" row in the About card, and check the dialog shows the same rows. Tap Close.

## Expected result

Expanding the Emergency card shows the first line "If your pet may have eaten something toxic or is in distress, call your veterinarian or the nearest emergency animal hospital now.", then "ASPCA Animal Poison Control (888) 426-4435, 24/7, consultation fee may apply", "Pet Poison Helpline (855) 764-7661, 24/7, $99 per incident fee applies", the search-for-an-emergency-vet guidance, and the disclaimer. Each hotline tap opens the phone dialer pre-filled with that number and requires no permission. The Settings dialog shows the same content and closes on Close.
