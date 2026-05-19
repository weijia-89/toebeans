package app.toebeans.core.backup

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only tests for [Argon2idKdf] that need APIs unavailable in commonTest
 * (here: [Runtime.gc] to suggest collection of the heap eaters after the
 * memory-pressure test). Tests that fit cleanly in commonTest live in
 * [Argon2idKdfTest].
 *
 * The single test in this file pins the ADR-0018 § Mitigations contract: a
 * low-memory environment surfaces a graceful [IllegalStateException] rather
 * than an unchecked [OutOfMemoryError]. The contract is asserted by attempting
 * to saturate the JVM heap with byte arrays before calling [Argon2idKdf.derive].
 * Three outcomes are accepted (see in-test comments); only an uncaught
 * [OutOfMemoryError] fails the test.
 */
class Argon2idKdfJvmTest {
    private val kdf = Argon2idKdf()
    private val eaters = mutableListOf<ByteArray>()

    @AfterTest
    fun releaseHeapEaters() {
        eaters.clear()
        @Suppress("ExplicitGarbageCollectionCall")
        System.gc()
        // Give the GC a brief moment so subsequent test methods in this JVM have
        // heap available. JUnit does NOT spin up a fresh JVM per test class.
        Thread.sleep(100)
    }

    @Test
    fun `derive surfaces low-memory failures as IllegalStateException, not raw OutOfMemoryError`() {
        // ADR-0018 § Mitigations contract: derive() catches OutOfMemoryError from
        // Argon2's 64 MiB allocation and rethrows as IllegalStateException so the
        // UI layer can surface a graceful error rather than crashing.
        //
        // Forcing real OOM here is brittle. gradle.properties sets -Xmx4g for the
        // JVM running this suite; the loop below allocates 64 MiB byte-array
        // chunks until the JVM refuses (catching the per-iteration OOM, since
        // we want to saturate, not crash). Then derive() runs against the
        // saturated heap. Three outcomes are acceptable:
        //
        // 1. derive() succeeds (heap was sufficient despite the eaters): the
        //    test asserts the output is 32 bytes — happy-path smoke check.
        //    Happens when the test JVM is run with a much larger heap than
        //    expected, e.g. -Xmx32g on a developer machine.
        // 2. derive() throws IllegalStateException with a memory-related
        //    message: the contract path fired correctly.
        // 3. derive() throws OutOfMemoryError directly: TEST FAILS — the impl
        //    regressed and stopped catching OOM. JUnit's default semantics let
        //    OutOfMemoryError propagate out and fail the test method.
        val eaterSize = 64 * 1024 * 1024 // 64 MiB chunks
        val maxEaters = 80 // ~5 GiB worth; saturates 4 GiB heap with margin
        repeat(maxEaters) {
            try {
                eaters.add(ByteArray(eaterSize))
            } catch (_: OutOfMemoryError) {
                // Heap is saturated as much as it's going to get. Stop allocating.
                return@repeat
            }
        }

        val passphrase = "low-mem-test".toCharArray()
        val salt = ByteArray(16) { 0x42 }
        try {
            val derived = kdf.derive(passphrase, salt)
            // Outcome 1: heap was sufficient. Verify the happy path.
            assertEquals(
                ARGON2ID_OUTPUT_KEY_BYTES,
                derived.size,
                "derive() succeeded under heap pressure; output must be the configured 32 bytes",
            )
        } catch (e: IllegalStateException) {
            // Outcome 2: contract path fired.
            assertTrue(
                e.message?.contains("memory", ignoreCase = true) == true,
                "low-memory IllegalStateException must mention memory; got: ${e.message}",
            )
        }
        // Outcome 3 (uncaught OutOfMemoryError) is not caught here; JUnit
        // surfaces it as a test failure with the OOM stack trace, which is the
        // intended failure signal for a regression in the impl's OOM handling.
    }
}
