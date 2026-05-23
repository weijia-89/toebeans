package app.toebeans.android.notifications

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Test-as-spec for [BootReceiver] (M1 ROADMAP sequencing item 5).
 *
 * **Contract:**
 * After [Intent.ACTION_BOOT_COMPLETED], the receiver rehydrates the 72-hour alarm horizon
 * from SQLDelight: query upcoming [DoseEvent] rows, call [AndroidNotificationActuator.schedule]
 * for each, and log replay outcome (ADR-0012 `BOOT_REPLAY_OK` marker; LocalCrashLog append
 * deferred until that API exists).
 *
 * **Phase 2 stub (shipped):**
 * - [BootReceiver.onReceive] delegates to [app.toebeans.android.ToebeansApp.rehydrateBootAlarms].
 * - SQLDelight lookup returns empty until persistence is wired in the receiver process; zero
 *   alarms scheduled, no crash on empty DB.
 *
 * **Follow-on slices (not this PR):**
 * - Assert AlarmManager alarms after BOOT_COMPLETED with seeded DoseEvents.
 * - ADR-0012 disabled-by-default lifecycle (enable after first dose schedule).
 * - LocalCrashLog non-crash append for BOOT_REPLAY_OK.
 *
 * Per AGENTS.md vibe-dangerous protocol: human review of assertions before extending coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {
    @Test
    fun `manifest declares RECEIVE_BOOT_COMPLETED permission`() {
        val manifest = mainManifestText()
        assertTrue(
            "BootReceiver consumer must declare RECEIVE_BOOT_COMPLETED",
            manifest.contains("android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\""),
        )
    }

    @Test
    fun `manifest registers BootReceiver for BOOT_COMPLETED`() {
        val manifest = mainManifestText()
        assertTrue(
            "BootReceiver must be declared in AndroidManifest",
            manifest.contains("android:name=\".notifications.BootReceiver\""),
        )
        assertTrue(
            "BootReceiver intent-filter must include ACTION_BOOT_COMPLETED",
            manifest.contains("android:name=\"android.intent.action.BOOT_COMPLETED\""),
        )
    }

    @Test
    fun `onReceive with BOOT_COMPLETED does not crash with empty database`() {
        // Stub path: SQLDelight not wired in receiver process yet; rehydration schedules zero alarms.
        val receiver = BootReceiver()
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun `onReceive ignores unrelated actions`() {
        val receiver = BootReceiver()
        val context = RuntimeEnvironment.getApplication()
        receiver.onReceive(context, Intent("app.toebeans.action.UNRELATED"))
    }

    private fun mainManifestText(): String {
        val candidates =
            listOf(
                File("src/main/AndroidManifest.xml"),
                File("androidApp/src/main/AndroidManifest.xml"),
            )
        val manifest =
            candidates.firstOrNull { it.isFile }
                ?: error(
                    "Could not locate AndroidManifest.xml from ${System.getProperty("user.dir")}",
                )
        return manifest.readText()
    }
}
