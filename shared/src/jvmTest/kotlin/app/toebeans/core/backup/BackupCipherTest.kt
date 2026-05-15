package app.toebeans.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Test-as-spec for [BackupCipher].
 *
 * Tests live in `jvmTest` (not `commonTest`) because PBKDF2 + AES-GCM are JCA-only.
 * Andriod hosts the same impl; integration testing on Android is covered by the
 * Robolectric tests on the notification path, not here.
 *
 * Iteration count is the v0.1 test-only value (1_000) — production code uses
 * [BackupCipher.DEFAULT_PBKDF2_ITERATIONS] (600_000). 1_000 keeps the test suite < 100ms
 * while exercising the same code path.
 */
class BackupCipherTest {
    private val factory = BackupCipherFactory(pbkdf2Iterations = 1_000)
    private val cipher = factory.create()

    @Test
    fun `round trip recovers the plaintext exactly`() {
        val plaintext = "the cat ate the prednisone at 0800".toByteArray()
        val passphrase = "correct horse battery staple".toCharArray()

        val envelope = cipher.encrypt(plaintext, passphrase)
        val recovered = cipher.decrypt(envelope, passphrase.copyOf()) // defensive copy: real callers wipe

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun `the envelope starts with the magic prefix`() {
        val envelope = cipher.encrypt("x".toByteArray(), "pp".toCharArray())
        val expectedMagic = BackupCipher.MAGIC.toByteArray(Charsets.US_ASCII)
        assertContentEquals(
            expectedMagic,
            envelope.copyOfRange(0, BackupCipher.MAGIC_BYTES_LENGTH),
            "envelope must begin with the magic ${BackupCipher.MAGIC}",
        )
    }

    @Test
    fun `repeated encryption of the same plaintext produces different envelopes`() {
        // The salt + IV are random; identical inputs MUST yield non-identical ciphertext.
        // This is the property that prevents a passive observer from spotting "the user
        // exported the same backup twice without changes."
        val plaintext = "stable payload".toByteArray()
        val pp = "pp".toCharArray()

        val a = cipher.encrypt(plaintext, pp.copyOf())
        val b = cipher.encrypt(plaintext, pp.copyOf())

        assertNotEquals(a.toList(), b.toList(), "fresh salt + IV must yield different envelopes")
        // But both decrypt cleanly:
        assertContentEquals(plaintext, cipher.decrypt(a, pp.copyOf()))
        assertContentEquals(plaintext, cipher.decrypt(b, pp.copyOf()))
    }

    @Test
    fun `wrong passphrase fails decryption with BackupDecryptException`() {
        val plaintext = "secret".toByteArray()
        val envelope = cipher.encrypt(plaintext, "right".toCharArray())

        assertFailsWith<BackupDecryptException> {
            cipher.decrypt(envelope, "wrong".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext fails decryption with BackupDecryptException`() {
        // Flip exactly one bit in the ciphertext region (past magic + salt + iv).
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt("the original".toByteArray(), pp.copyOf())
        val tampered = envelope.copyOf()
        val ciphertextStart =
            BackupCipher.MAGIC_BYTES_LENGTH +
                BackupCipher.SALT_BYTES_LENGTH +
                BackupCipher.IV_BYTES_LENGTH
        tampered[ciphertextStart] = (tampered[ciphertextStart].toInt() xor 0x01).toByte()

        assertFailsWith<BackupDecryptException> { cipher.decrypt(tampered, pp.copyOf()) }
    }

    @Test
    fun `truncated envelope is rejected`() {
        val envelope = cipher.encrypt("x".toByteArray(), "pp".toCharArray())
        val truncated = envelope.copyOfRange(0, envelope.size - 1)

        assertFailsWith<BackupDecryptException> { cipher.decrypt(truncated, "pp".toCharArray()) }
    }

    @Test
    fun `envelope without correct magic is rejected before decryption is attempted`() {
        // The first 4 bytes are not "TBN1": we should fail on magic check, not on auth check.
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt("x".toByteArray(), pp.copyOf())
        envelope[0] = 'X'.code.toByte() // corrupt the magic
        envelope[1] = 'Y'.code.toByte()

        val ex = assertFailsWith<BackupDecryptException> { cipher.decrypt(envelope, pp.copyOf()) }
        assertTrue(ex.message!!.contains("magic"), "magic-mismatch failure should mention the magic")
    }

    @Test
    fun `extremely short envelope is rejected with a size-related message`() {
        val ex =
            assertFailsWith<BackupDecryptException> {
                cipher.decrypt(byteArrayOf(1, 2, 3), "pp".toCharArray())
            }
        assertTrue(ex.message!!.contains("short", ignoreCase = true))
    }

    @Test
    fun `empty plaintext round-trips`() {
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt(ByteArray(0), pp.copyOf())
        assertEquals(0, cipher.decrypt(envelope, pp.copyOf()).size)
    }

    @Test
    fun `unicode passphrase with emoji and combining characters round-trips`() {
        // PBKDF2 + UTF-16 char-array path; verify we don't mangle high-BMP code units.
        // The CharArray here contains:
        //   - Latin "café"  (with combining acute, NFD form is 5 chars)
        //   - A 🐱 (U+1F431, requires a surrogate pair in UTF-16)
        //   - A non-Latin script: "日本語"
        val passphrase = "café\uD83D\uDC31日本語".toCharArray()
        val plaintext = "Mochi the cat takes prednisone".toByteArray(Charsets.UTF_8)

        val envelope = cipher.encrypt(plaintext, passphrase.copyOf())
        val recovered = cipher.decrypt(envelope, passphrase.copyOf())

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun `large plaintext (64KB) round-trips`() {
        // Defensive: catches any accidental int-sized buffer assumption.
        val pp = "pp".toCharArray()
        val plaintext = ByteArray(64 * 1024) { (it and 0xFF).toByte() }

        val envelope = cipher.encrypt(plaintext, pp.copyOf())
        assertContentEquals(plaintext, cipher.decrypt(envelope, pp.copyOf()))
    }
}
