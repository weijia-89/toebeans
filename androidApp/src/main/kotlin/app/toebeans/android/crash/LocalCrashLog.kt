package app.toebeans.android.crash

import android.content.Context
import android.os.Build
import kotlinx.datetime.Clock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Local-only uncaught-exception capture per ADR-0009.
 *
 * Writes a single rotating file under the app's private `filesDir` (`crash.log`,
 * rotated to `crash.log.1` when it exceeds [maxBytes]). Always delegates to the
 * previously-installed default handler so the OS still terminates the process — we
 * are not swallowing the crash, just adding a side-channel record for the user to
 * export through Settings → "Export logs" if they choose.
 *
 * No network. No telemetry. No PII in the log:
 *   - We write the stack trace + build/device metadata.
 *   - We do NOT write any repository, DAO, or model state.
 *   - We do NOT call `.toString()` on any domain object.
 *
 * Build/device metadata is informational only and is what every Play Console
 * crash report already includes; it does not narrow the user's privacy further.
 *
 * Threading: `uncaughtException` is called on the thread that crashed and must not
 * itself throw. All IO is wrapped in try/catch; a write failure is silent rather
 * than risking a recursive crash mid-shutdown.
 */
public class LocalCrashLog internal constructor(
    private val logDir: File,
    private val buildVersionName: String,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : Thread.UncaughtExceptionHandler {
    private val primary: File get() = File(logDir, FILE_PRIMARY)
    private val rotated: File get() = File(logDir, FILE_ROTATED)

    /**
     * The handler that was installed before us. We always delegate to it after
     * writing so the process exits the way the platform expects.
     */
    private var previous: Thread.UncaughtExceptionHandler? = null

    /**
     * Install this handler as the JVM's default uncaught exception handler.
     * Safe to call multiple times; subsequent calls are no-ops (we don't want to
     * chain ourselves to ourselves).
     */
    public fun install() {
        val existing = Thread.getDefaultUncaughtExceptionHandler()
        if (existing === this) return
        previous = existing
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            writeRecord(thread, throwable)
        } catch (_: Throwable) {
            // Swallow IO failure: a recursive crash inside the crash handler is
            // strictly worse than a missing log entry. The OS will still get the
            // original throwable via the delegated handler below.
        }
        previous?.uncaughtException(thread, throwable)
    }

    private fun writeRecord(
        thread: Thread,
        throwable: Throwable,
    ) {
        if (!logDir.exists()) logDir.mkdirs()
        rotateIfNeeded()
        val stackTrace =
            StringWriter()
                .also { sw ->
                    PrintWriter(sw).use { pw -> throwable.printStackTrace(pw) }
                }.toString()
        val record =
            buildString {
                appendLine("---- toebeans crash ----")
                appendLine("timestamp_utc_ms=${now()}")
                appendLine("app_version=$buildVersionName")
                appendLine("android_sdk=$sdkInt")
                appendLine("device=$deviceModel")
                appendLine("thread=${thread.name}")
                appendLine("exception=${throwable.javaClass.name}")
                appendLine("message=${throwable.message ?: "(no message)"}")
                appendLine("stack:")
                append(stackTrace)
                appendLine()
            }
        primary.appendText(record)
    }

    private fun rotateIfNeeded() {
        if (!primary.exists() || primary.length() < maxBytes) return
        if (rotated.exists()) rotated.delete()
        primary.renameTo(rotated)
    }

    public companion object {
        public const val FILE_PRIMARY: String = "crash.log"
        public const val FILE_ROTATED: String = "crash.log.1"
        public const val DEFAULT_MAX_BYTES: Long = 256L * 1024L // ~256 KB

        /**
         * Factory used by [app.toebeans.android.ToebeansApp.onCreate]. Builds an
         * instance against the app's private `filesDir` and the resolved build
         * version name.
         */
        public fun forApplication(
            context: Context,
            buildVersionName: String,
        ): LocalCrashLog = LocalCrashLog(logDir = context.filesDir, buildVersionName = buildVersionName)
    }
}
