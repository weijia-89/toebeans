# AAR metadata check, 2026-11-09

The skip of `checkDebugAarMetadata` does not ship. The task runs.

## What failed

Gradle 9.9.0 `:androidApp:checkDebugAarMetadata --stacktrace` exits 1 on `78171d31`. The task reports 40 issues. `androidx.navigation:navigation-compose-android:2.10.2` needs compileSdk 37 and AGP 9.1.0. The project uses compileSdk 35 and AGP 8.7.0.

The same task on a temporary Gradle 8.10 wrapper also exits 1 with the same 40 issues. The wrapper files were restored before any other edit.

## What the fix is

Resolution pins Compose UI, foundation, animation, and runtime to 1.11.4, `core` to 1.16.0, `activity` to 1.9.2, `lifecycle` to 2.8.6, `navigation` to 2.9.6, and `kotlinx-datetime` to 0.6.2. `material3` 1.11.4 is not published, so that artifact is not pinned. App code keeps `kotlinx.datetime.Clock`.

Robolectric tests use SDK 34. API 33's jar is missing `android.bluetooth.BluetoothDevice.BluetoothAddress`. The debug APK excludes `META-INF/versions/9/OSGI-INF/MANIFEST.MF` because bouncycastle and jspecify both ship it.

## What passed after the pins

`:androidApp:checkDebugAarMetadata --rerun-tasks` executed and exited 0. `:androidApp:compileDebugKotlin` exited 0. `:androidApp:testDebugUnitTest` and `:androidApp:assembleDebug` exited 0. `bash scripts/verify_before_push.sh` exited 0.

AGP stays 8.7.0. The Gradle wrapper stays 9.9.0. The `wire-runtime-jvm` exclude stays. This change is under the vibe-dangerous floor until a human reads the pin diff. pitest was not run.
