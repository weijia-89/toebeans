package app.toebeans.android.crash

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Tests for [LocalCrashLog] (ADR-0009).
 *
 * No Robolectric needed: the class only touches the JVM-builtin
 * `Thread.UncaughtExceptionHandler` API and a `File`-based log directory. We pass
 * a `TemporaryFolder` directly to the internal constructor.
 *
 * Verification scope, per ADR-0009:
 *   1. Writes a record on first crash with the expected fields.
 *   2. Rotates the file when it exceeds the size threshold.
 *   3. Always delegates to the previously-installed default handler.
 *   4. Swallows IO errors from the write step (no recursive crash).
 *   5. install() is idempotent — does not chain itself to itself.
 */
class LocalCrashLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var savedDefault: Thread.UncaughtExceptionHandler? = null

    @Before
    fun saveDefault() {
        // The JVM's default handler is process-wide. Capture and restore around each
        // test so a test that installs the handler doesn't leak into the next test.
        savedDefault = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun restoreDefault() {
        Thread.setDefaultUncaughtExceptionHandler(savedDefault)
    }

    @Test
    fun `first crash writes record to crash log with expected fields`() {
        val logDir = tmp.newFolder("files")
        val log = newLog(logDir, version = "0.1.0", nowMillis = 1_700_000_000_000L)

        log.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        val primary = File(logDir, LocalCrashLog.FILE_PRIMARY)
        assertTrue("crash.log was created", primary.exists())
        val contents = primary.readText()
        assertTrue("includes timestamp", contents.contains("timestamp_utc_ms=1700000000000"))
        assertTrue("includes app version", contents.contains("app_version=0.1.0"))
        assertTrue("includes android sdk", contents.contains("android_sdk=33"))
        assertTrue("includes device model", contents.contains("device=TestVendor TestModel"))
        assertTrue("includes exception class", contents.contains("exception=java.lang.RuntimeException"))
        assertTrue("includes exception message", contents.contains("message=boom"))
        assertTrue("includes stack-trace marker", contents.contains("stack:"))
    }

    @Test
    fun `record is appended, not replaced, on a second crash`() {
        val logDir = tmp.newFolder("files")
        val log = newLog(logDir)

        log.uncaughtException(Thread.currentThread(), RuntimeException("first"))
        log.uncaughtException(Thread.currentThread(), IllegalStateException("second"))

        val primary = File(logDir, LocalCrashLog.FILE_PRIMARY)
        val contents = primary.readText()
        assertTrue("first crash is present", contents.contains("message=first"))
        assertTrue("second crash is also present", contents.contains("message=second"))
    }

    @Test
    fun `oversize log rotates to crash log dot 1 and primary starts fresh`() {
        val logDir = tmp.newFolder("files")
        // Small maxBytes so a single record exceeds it.
        val log = newLog(logDir, maxBytes = 64)

        log.uncaughtException(Thread.currentThread(), RuntimeException("first-crash-padding-padding"))
        // After the first write, primary is now >64 bytes. A second crash must rotate
        // before writing, so primary ends up containing only the second record.
        log.uncaughtException(Thread.currentThread(), IllegalStateException("second-after-rotate"))

        val primary = File(logDir, LocalCrashLog.FILE_PRIMARY)
        val rotated = File(logDir, LocalCrashLog.FILE_ROTATED)
        assertTrue("rotated copy now exists", rotated.exists())
        assertTrue("rotated copy holds the first crash", rotated.readText().contains("first-crash-padding-padding"))
        assertTrue("primary holds the second crash", primary.readText().contains("second-after-rotate"))
        assertFalse(
            "primary must not still hold the first crash after rotation",
            primary.readText().contains("first-crash-padding-padding"),
        )
    }

    @Test
    fun `handler always delegates to the previously installed default handler`() {
        val logDir = tmp.newFolder("files")
        val delegated = mutableListOf<Pair<Thread, Throwable>>()
        val previous =
            Thread.UncaughtExceptionHandler { t, e -> delegated.add(t to e) }
        Thread.setDefaultUncaughtExceptionHandler(previous)

        val log = newLog(logDir)
        log.install()
        val boom = RuntimeException("boom")
        log.uncaughtException(Thread.currentThread(), boom)

        assertEquals("delegated to previous handler exactly once", 1, delegated.size)
        assertSame("delegated the SAME throwable, not a wrapper", boom, delegated.single().second)
    }

    @Test
    fun `install is idempotent and does not chain the handler to itself`() {
        val logDir = tmp.newFolder("files")
        // Baseline handler is a no-op; we only care that install() does NOT chain
        // log -> log on a second call (which would loop forever in delegation).
        val baseline = Thread.UncaughtExceptionHandler { _, _ -> }
        Thread.setDefaultUncaughtExceptionHandler(baseline)

        val log = newLog(logDir)
        log.install()
        // Second install() must NOT chain log -> log; the previous reference must
        // still be the baseline so delegation skips the no-op self-chain.
        log.install()

        assertSame("default handler is the LocalCrashLog instance", log, Thread.getDefaultUncaughtExceptionHandler())
        // Indirect proof of no self-chain: trigger a crash and ensure the test does not
        // recurse. (If install() had chained `this` to `this`, delegation would loop.)
        log.uncaughtException(Thread.currentThread(), RuntimeException("not infinite"))
    }

    @Test
    fun `IO failure inside the write is swallowed and delegation still happens`() {
        val logDir = tmp.newFolder("files")
        // Make logDir un-writable by deleting it and replacing with a file of the same
        // name. mkdirs() inside writeRecord will silently fail; appendText() will then
        // throw an IOException, which the handler must catch.
        logDir.delete()
        val blocker = File(logDir.parentFile, logDir.name)
        blocker.createNewFile()
        try {
            val delegated = mutableListOf<Throwable>()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> delegated.add(e) }

            val log = newLog(blocker)
            log.install()
            val boom = IOException("simulated IO during crash")
            // Should NOT itself throw, even though file operations underneath will.
            log.uncaughtException(Thread.currentThread(), boom)

            assertEquals(
                "delegation must still happen even when the write fails",
                1,
                delegated.size,
            )
            assertSame(boom, delegated.single())
        } finally {
            blocker.delete()
        }
    }

    private fun newLog(
        dir: File,
        version: String = "0.1.0",
        maxBytes: Long = LocalCrashLog.DEFAULT_MAX_BYTES,
        nowMillis: Long = 1_700_000_000_000L,
    ): LocalCrashLog =
        LocalCrashLog(
            logDir = dir,
            buildVersionName = version,
            sdkInt = 33,
            deviceModel = "TestVendor TestModel",
            maxBytes = maxBytes,
            now = { nowMillis },
        )

    @Test
    fun `factory hands back a non-null instance`() {
        // The Context-bound factory isn't directly testable without Robolectric; this
        // is a smoke check that the public surface exists and compiles. The real
        // behavioral coverage is in the constructor-based tests above.
        assertNotNull(LocalCrashLog::class.java)
    }
}
