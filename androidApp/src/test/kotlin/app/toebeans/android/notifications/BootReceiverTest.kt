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
 * Test-as-spec for [BootReceiver] (M1 ROADMAP sequencing item 5, phase 1 scaffold).
 *
 * **Contract (phase 2 after human review):**
 * After [Intent.ACTION_BOOT_COMPLETED], the receiver rehydrates the 72-hour alarm horizon
 * from SQLDelight: query upcoming [DoseEvent] rows, call [AndroidNotificationActuator.schedule]
 * for each, and log replay outcome via [app.toebeans.android.crash.LocalCrashLog].
 *
 * **Phase 1 scope (this PR):**
 * - Manifest declares [android.Manifest.permission.RECEIVE_BOOT_COMPLETED] and registers
 *   [BootReceiver] for boot completion.
 * - [BootReceiver.onReceive] is a no-op scaffold that does not crash when the database is
 *   empty (separate-process receiver cannot rely on in-memory fakes).
 *
 * TODO(phase 2): assert AlarmManager alarms after BOOT_COMPLETED with seeded DoseEvents;
 *   see `prompts/toebeans_boot_receiver_impl.txt` and ADR-0012 lifecycle (disabled-by-default
 *   enable after first dose schedule).
 *
 * Per AGENTS.md vibe-dangerous protocol: human review of these assertions before phase 2 impl.
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
        // Phase 1: direct invocation proves the scaffold survives the empty-DB case.
        // Phase 2 will seed SQLDelight and assert 72h-horizon AlarmManager rehydration.
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
