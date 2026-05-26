# Manual QA (hands-on)

Automate emulator, install, fresh state, and launch. **You only navigate** on device.

## Quick start

From repo root:

```bash
bash scripts/manual_qa_boot.sh fresh
bash scripts/manual_qa_boot.sh fresh --boot-avd toebeans-pixel7
bash scripts/manual_qa_boot.sh warm                    # keep existing app data
bash scripts/manual_qa_boot.sh fresh --open-style-lab  # also opens docs/style-lab in browser
```

Env: `TOEBEANS_AVD`, `ANDROID_SERIAL`, `ANDROID_HOME`.

Verify before merge:

```bash
./gradlew ktlintCheck detekt :shared:jvmTest
```

## What the script does

1. Waits for a device (optional `--boot-avd` starts the emulator).
2. `./gradlew :androidApp:installDebug`
3. `fresh`: `pm clear` then launch; `warm`: launch only.
4. Prints **navigation-only** steps.

PR test plans should reference this script for prerequisites.
