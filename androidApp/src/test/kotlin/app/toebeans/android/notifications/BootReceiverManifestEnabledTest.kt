package app.toebeans.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Red on purpose. The manifest still sets android:enabled="true" on BootReceiver.
 * ADR-0012 wants that flag false until the first schedule materializes.
 */
class BootReceiverManifestEnabledTest {
    @Test
    fun `BootReceiver manifest sets android enabled to false`() {
        val enabled = bootReceiverEnabledAttribute(mainManifestText())
        assertEquals(
            "app.toebeans.android.notifications.BootReceiver android:enabled",
            "false",
            enabled,
        )
    }

    private fun bootReceiverEnabledAttribute(manifest: String): String {
        val nameToken = "android:name=\".notifications.BootReceiver\""
        val nameAt = manifest.indexOf(nameToken)
        check(nameAt >= 0) { "manifest is missing .notifications.BootReceiver" }
        val openAt = manifest.lastIndexOf("<receiver", nameAt)
        check(openAt >= 0) { ".notifications.BootReceiver is not inside a receiver tag" }
        val closeAt = manifest.indexOf('>', nameAt)
        check(closeAt > openAt) { "BootReceiver receiver tag does not close" }
        val tag = manifest.substring(openAt, closeAt)
        val marker = "android:enabled=\""
        val enabledAt = tag.indexOf(marker)
        check(enabledAt >= 0) { "BootReceiver receiver tag has no android:enabled" }
        val valueAt = enabledAt + marker.length
        val valueEnd = tag.indexOf('"', valueAt)
        check(valueEnd > valueAt) { "BootReceiver android:enabled value does not close" }
        return tag.substring(valueAt, valueEnd)
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
