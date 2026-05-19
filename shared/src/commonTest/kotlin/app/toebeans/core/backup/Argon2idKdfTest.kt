package app.toebeans.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Test-as-spec for [Argon2idKdf].
 *
 * Failing-test commit per AGENTS.md § Test-as-spec rules. Each test currently
 * fails at runtime with [NotImplementedError] from the platform actual. After
 * human review of the test signatures, D1 (per ADR-0018 § Followups) implements
 * the actuals via BouncyCastle's `Argon2BytesGenerator` and these tests pass.
 *
 * The tests live in `commonTest` so the same suite runs against jvm and android
 * targets. BouncyCastle is a pure-Java library; both platforms exercise the same
 * code path.
 *
 * Test categories:
 *  1. Parameter constants match the values fixed in ADR-0018 § Decision § KDF
 *     parameters. These are top-level compile-time constants; the asserts catch
 *     a future editor who silently changes a parameter without bumping the
 *     envelope magic. See [parameter constants reflect ADR-0018].
 *  2. Basic output-shape and contract behavior. See [derives a 32-byte key],
 *     [derivation is deterministic], [different salts yield different keys],
 *     [different passphrases yield different keys], [salt below the minimum is
 *     rejected].
 *  3. Known-answer test (KAT) vector. The expected byte sequence comes from
 *     BouncyCastle's own test suite cross-checked against a second independent
 *     Argon2id reference (e.g. the phc-winner-argon2 reference implementation).
 *     Currently a `TODO(D1)` placeholder; D1 fills in the expected bytes after
 *     running the live derivation and verifying against the independent
 *     reference. See [known-answer test matches the cross-checked reference].
 */
class Argon2idKdfTest {
    private val kdf = Argon2idKdf()

    @Test
    fun `parameter constants reflect ADR-0018`() {
        // Memory cost = 64 MiB. Lower than RFC 9106's second-recommended option only
        // if the iteration count compensates; we hold at 3 iterations and 1 lane.
        assertEquals(65_536, ARGON2ID_MEMORY_COST_KIB, "memory cost must be 64 MiB")
        assertEquals(3, ARGON2ID_ITERATIONS, "iteration count must be 3")
        assertEquals(1, ARGON2ID_PARALLELISM, "parallelism must be 1 (single lane)")
        assertEquals(32, ARGON2ID_OUTPUT_KEY_BYTES, "output key length must be 32 bytes (256 bits)")
        assertEquals(8, ARGON2ID_MIN_SALT_BYTES, "minimum salt length per RFC 9106 § 3.1")
    }

    @Test
    fun `derives a 32-byte key`() {
        val passphrase = "correct horse battery staple".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val derived = kdf.derive(passphrase, salt)
        assertEquals(ARGON2ID_OUTPUT_KEY_BYTES, derived.size)
    }

    @Test
    fun `derivation is deterministic for the same passphrase and salt`() {
        val passphrase = "deterministic".toCharArray()
        val salt = ByteArray(16) { 0x42 }
        val first = kdf.derive(passphrase.copyOf(), salt.copyOf())
        val second = kdf.derive(passphrase.copyOf(), salt.copyOf())
        assertContentEquals(first, second, "same inputs must yield identical output")
    }

    @Test
    fun `different salts yield different keys`() {
        val passphrase = "shared".toCharArray()
        val saltA = ByteArray(16) { 0x00 }
        val saltB = ByteArray(16) { 0xFF.toByte() }
        val keyA = kdf.derive(passphrase.copyOf(), saltA)
        val keyB = kdf.derive(passphrase.copyOf(), saltB)
        assertNotEquals(keyA.toList(), keyB.toList(), "salt rotation must change the derived key")
    }

    @Test
    fun `different passphrases yield different keys`() {
        val salt = ByteArray(16) { 0x42 }
        val keyA = kdf.derive("password-a".toCharArray(), salt.copyOf())
        val keyB = kdf.derive("password-b".toCharArray(), salt.copyOf())
        assertNotEquals(keyA.toList(), keyB.toList(), "passphrase rotation must change the derived key")
    }

    @Test
    fun `salt below the minimum length is rejected`() {
        // RFC 9106 § 3.1 requires salt >= 8 bytes. A 4-byte salt must throw rather than
        // silently derive a key that an attacker could attack offline at low cost.
        assertFailsWith<IllegalArgumentException> {
            kdf.derive("any".toCharArray(), ByteArray(4))
        }
    }

    @Test
    fun `known-answer test matches the cross-checked reference`() {
        // RFC 9106 § 5.3 publishes one test vector that includes a "secret" and
        // "associated data" extension that toebeans does not use. The vector below
        // is for (passphrase, salt) only, with empty secret and empty associated
        // data, derived against the parameters fixed in ADR-0018 (m=64MiB, t=3,
        // p=1, 32-byte output).
        //
        // KAT inputs (constants):
        val passphrase = "argon2id-kat-passphrase".toCharArray()
        val salt = "argon2id-kat-salt-16b".toByteArray(Charsets.US_ASCII).copyOf(16)

        // KAT expected output. Cross-checked against argon2-cffi (which binds to
        // the official phc-winner-argon2 C reference implementation per RFC 9106).
        // The Python cross-check script lives at /tmp/d1_argon2id_kat_crosscheck.py
        // in the D1 step 3 session log; the inputs (passphrase, salt, params)
        // above produce this exact 32-byte output under the reference impl. If
        // BouncyCastle ever regresses and silently weakens the KDF, this test
        // catches it byte-for-byte.
        @Suppress("ktlint:standard:property-naming")
        val EXPECTED_KAT_OUTPUT: ByteArray =
            byteArrayOf(
                0xb8.toByte(),
                0xd6.toByte(),
                0x6e.toByte(),
                0xd9.toByte(),
                0x90.toByte(),
                0x07.toByte(),
                0x1d.toByte(),
                0x40.toByte(),
                0xf5.toByte(),
                0x7e.toByte(),
                0xf8.toByte(),
                0x8c.toByte(),
                0x12.toByte(),
                0x1f.toByte(),
                0x2f.toByte(),
                0xb0.toByte(),
                0x8e.toByte(),
                0xb3.toByte(),
                0x1e.toByte(),
                0xbd.toByte(),
                0x58.toByte(),
                0xc2.toByte(),
                0xa3.toByte(),
                0xed.toByte(),
                0x02.toByte(),
                0x71.toByte(),
                0xe2.toByte(),
                0x28.toByte(),
                0xd1.toByte(),
                0x69.toByte(),
                0x13.toByte(),
                0xb6.toByte(),
            )

        val derived = kdf.derive(passphrase, salt)
        assertEquals(32, derived.size, "KAT output must be 32 bytes")
        assertFalse(
            EXPECTED_KAT_OUTPUT.isEmpty(),
            "KAT expected output is the D1 TODO; the assertion below must be wired in by D1",
        )
        assertContentEquals(EXPECTED_KAT_OUTPUT, derived, "derivation must match the cross-checked reference vector")
    }

    @Test
    fun `derive does not mutate the passphrase or salt input arrays`() {
        // The JVM actual wipes its own intermediate UTF-8 byte buffer in finally,
        // but MUST NOT mutate the caller's CharArray passphrase or ByteArray salt.
        // The caller owns those arrays and is responsible for their lifecycle;
        // a hidden mutation here would surprise callers who pass `passphrase.copyOf()`
        // expecting the original buffer to survive (the pattern in every other
        // test in this file).
        val passphrase = "no-mutate-test".toCharArray()
        val passphraseSnapshot = passphrase.copyOf()
        val salt = ByteArray(16) { (it * 7 + 3).toByte() }
        val saltSnapshot = salt.copyOf()

        kdf.derive(passphrase, salt)

        assertContentEquals(
            passphraseSnapshot,
            passphrase,
            "derive() must not mutate the caller's passphrase array",
        )
        assertContentEquals(
            saltSnapshot,
            salt,
            "derive() must not mutate the caller's salt array",
        )
    }

    @Test
    fun `derive completes within 8 seconds on reference hardware`() {
        // ADR-0018 § Followups: "Performance budget test: derive-key time on the
        // jvmTest reference hardware is below 8 seconds (an upper bound that
        // catches an order-of-magnitude regression; the real-device measurement
        // happens at M1.2 internal beta)."
        //
        // The 8-second ceiling is intentionally loose. A Pixel 7 derives in
        // ~1.5-3s per the ADR; a CI runner or developer Mac with more memory and
        // wider cores derives faster. The test catches a regression that pushes
        // wall-clock into the 8+ s territory (e.g. a parameter typo bumping
        // memory cost or iterations beyond what ADR-0018 pins). It does NOT
        // pin a tight latency contract because that would flake under shared CI.
        val passphrase = "perf-budget-test".toCharArray()
        val salt = ByteArray(16) { 0x42 }

        val elapsed =
            measureTime {
                kdf.derive(passphrase, salt)
            }

        assertTrue(
            elapsed.inWholeMilliseconds < 8_000,
            "derive() took ${elapsed.inWholeMilliseconds}ms; budget is 8000ms per ADR-0018 \u00a7 Followups",
        )
    }
}
