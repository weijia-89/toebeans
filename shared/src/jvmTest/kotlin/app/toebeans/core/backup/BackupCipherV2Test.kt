package app.toebeans.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Test-as-spec for [BackupCipherV2] (the TBN2 cipher per ADR-0018).
 *
 * Failing-test commit per AGENTS.md § Test-as-spec rules. Each test currently
 * fails at runtime with [NotImplementedError]. After human review of the test
 * signatures, D1 implements the real cipher and these tests pass.
 *
 * Tests live in `jvmTest` (not `commonTest`) for the same reason as
 * [BackupCipherTest]: AES-GCM goes through JCA. The Android target re-uses the
 * same impl via the duplicated `androidMain` actual, covered by Robolectric
 * integration tests elsewhere.
 *
 * Categories:
 *  1. Round-trip and envelope-shape contracts (parallel to v1).
 *  2. AAD-binding contracts (new in v2 vs v1):
 *     - Tampering the magic header MUST fail decryption.
 *     - Tampering the salt header MUST fail decryption.
 *     - Tampering the IV header MUST fail decryption.
 *  3. Downgrade-attack rejection: a `TBN1`-magic envelope MUST be rejected by
 *     v2's decrypt path (it's a different cipher).
 *  4. Standard tamper, truncation, wrong-passphrase, and edge-case round-trips
 *     (parallel to v1).
 */
class BackupCipherV2Test {
    private val factory = BackupCipherV2Factory()
    private val cipher = factory.create()

    @Test
    fun `companion constants reflect ADR-0018`() {
        assertEquals("TBN2", BackupCipherV2.MAGIC)
        assertEquals(4, BackupCipherV2.MAGIC_BYTES_LENGTH)
        assertEquals(16, BackupCipherV2.SALT_BYTES_LENGTH)
        assertEquals(12, BackupCipherV2.IV_BYTES_LENGTH)
        assertEquals(128, BackupCipherV2.GCM_TAG_BITS)
        assertEquals(256, BackupCipherV2.DERIVED_KEY_BITS)
        assertEquals(32, BackupCipherV2.AAD_BYTES_LENGTH, "AAD = magic(4) || salt(16) || iv(12) = 32 bytes")
    }

    @Test
    fun `round trip recovers the plaintext exactly`() {
        val plaintext = "the cat ate the prednisone at 0800".toByteArray()
        val passphrase = "correct horse battery staple".toCharArray()

        val envelope = cipher.encrypt(plaintext, passphrase.copyOf())
        val recovered = cipher.decrypt(envelope, passphrase.copyOf())

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun `the envelope starts with the TBN2 magic prefix`() {
        val envelope = cipher.encrypt("x".toByteArray(), "pp".toCharArray())
        val expectedMagic = BackupCipherV2.MAGIC.toByteArray(Charsets.US_ASCII)
        assertContentEquals(
            expectedMagic,
            envelope.copyOfRange(0, BackupCipherV2.MAGIC_BYTES_LENGTH),
            "envelope must begin with the magic ${BackupCipherV2.MAGIC}",
        )
    }

    @Test
    fun `repeated encryption of the same plaintext produces different envelopes`() {
        val plaintext = "stable payload".toByteArray()
        val pp = "pp".toCharArray()

        val a = cipher.encrypt(plaintext, pp.copyOf())
        val b = cipher.encrypt(plaintext, pp.copyOf())

        assertNotEquals(a.toList(), b.toList(), "fresh salt + IV must yield different envelopes")
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
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt("the original".toByteArray(), pp.copyOf())
        val tampered = envelope.copyOf()
        val ciphertextStart =
            BackupCipherV2.MAGIC_BYTES_LENGTH +
                BackupCipherV2.SALT_BYTES_LENGTH +
                BackupCipherV2.IV_BYTES_LENGTH
        tampered[ciphertextStart] = (tampered[ciphertextStart].toInt() xor 0x01).toByte()

        assertFailsWith<BackupDecryptException> { cipher.decrypt(tampered, pp.copyOf()) }
    }

    @Test
    fun `tampered magic header fails decryption via AAD binding`() {
        // The AAD = magic || salt || iv is bound to the GCM tag. Flipping any byte
        // in the magic header MUST invalidate the tag and fail decryption. This is
        // the v2-vs-v1 difference: v1 had no AAD and would have decrypted the body
        // after only a magic-mismatch error message change.
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt("x".toByteArray(), pp.copyOf())
        envelope[0] = (envelope[0].toInt() xor 0x01).toByte()

        assertFailsWith<BackupDecryptException> { cipher.decrypt(envelope, pp.copyOf()) }
    }

    @Test
    fun `tampered salt header fails decryption via AAD binding`() {
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt("x".toByteArray(), pp.copyOf())
        val saltStart = BackupCipherV2.MAGIC_BYTES_LENGTH
        envelope[saltStart] = (envelope[saltStart].toInt() xor 0x01).toByte()

        assertFailsWith<BackupDecryptException> { cipher.decrypt(envelope, pp.copyOf()) }
    }

    @Test
    fun `tampered IV header fails decryption via AAD binding`() {
        val pp = "pp".toCharArray()
        val envelope = cipher.encrypt("x".toByteArray(), pp.copyOf())
        val ivStart = BackupCipherV2.MAGIC_BYTES_LENGTH + BackupCipherV2.SALT_BYTES_LENGTH
        envelope[ivStart] = (envelope[ivStart].toInt() xor 0x01).toByte()

        assertFailsWith<BackupDecryptException> { cipher.decrypt(envelope, pp.copyOf()) }
    }

    @Test
    fun `TBN1 envelope is rejected with a magic-related error`() {
        // Downgrade rejection. The v2 cipher does not transparently decrypt v1
        // envelopes; the TBN1 read-only path is BackupCipher's job. A user who
        // hands a TBN1 envelope to V2.decrypt MUST get a clear magic-mismatch
        // error rather than a garbled key-derivation attempt.
        val tbn1Stub = ByteArray(BackupCipherV2.AAD_BYTES_LENGTH + 16) { 0 }
        "TBN1".toByteArray(Charsets.US_ASCII).copyInto(tbn1Stub, 0)

        val ex =
            assertFailsWith<BackupDecryptException> {
                cipher.decrypt(tbn1Stub, "pp".toCharArray())
            }
        assertTrue(
            ex.message!!.contains("magic", ignoreCase = true) || ex.message!!.contains("TBN", ignoreCase = true),
            "TBN1 rejection should mention the magic value or the magic field",
        )
    }

    @Test
    fun `truncated envelope is rejected`() {
        val envelope = cipher.encrypt("x".toByteArray(), "pp".toCharArray())
        val truncated = envelope.copyOfRange(0, envelope.size - 1)

        assertFailsWith<BackupDecryptException> { cipher.decrypt(truncated, "pp".toCharArray()) }
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
        val passphrase = "café\uD83D\uDC31日本語".toCharArray()
        val plaintext = "Mochi the cat takes prednisone".toByteArray(Charsets.UTF_8)

        val envelope = cipher.encrypt(plaintext, passphrase.copyOf())
        val recovered = cipher.decrypt(envelope, passphrase.copyOf())

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun `large plaintext (64KB) round-trips`() {
        val pp = "pp".toCharArray()
        val plaintext = ByteArray(64 * 1024) { (it and 0xFF).toByte() }

        val envelope = cipher.encrypt(plaintext, pp.copyOf())
        assertContentEquals(plaintext, cipher.decrypt(envelope, pp.copyOf()))
    }

    @Test
    fun `AAD construction order matches ADR-0018 (magic, salt, iv)`() {
        // Pre-computed cross-reference vector pinning the AAD construction order.
        //
        // ADR-0018 § Decision § Authentication fixes AAD = magic(4) || salt(16) ||
        // iv(12). The byte-flip tests above (magic / salt / iv tamper) verify that
        // each field participates in the AAD, but they do NOT pin the construction
        // ORDER: an implementation that built AAD as (salt || iv || magic) or any
        // other permutation would still fail every single-byte flip, because the
        // impl would use the same wrong order on encrypt and decrypt. This test
        // pins the order by reference vector.
        //
        // The envelope below was constructed offline with:
        //   passphrase = "aad-order-vector-passphrase"
        //   salt       = the 16-byte ASCII string "aad-order-vec-sa"
        //   iv         = the 12-byte ASCII string "aad-order-iv"
        //   plaintext  = the ASCII string "aad-order-vector-plaintext"
        //   AAD        = "TBN2" || salt || iv (32 bytes, in that exact order)
        //   key        = Argon2id(passphrase, salt, m=64MiB, t=3, p=1, 32 bytes)
        //   cipher     = AES-256-GCM with a 128-bit tag
        //
        // If the implementation uses any AAD order other than magic || salt || iv,
        // the GCM tag check fails and decrypt() throws BackupDecryptException.
        //
        // The ciphertext+tag bytes are a TODO(D1) placeholder; D1 fills them in
        // after computing the vector against an independent Argon2id + AES-256-GCM
        // reference (e.g. python-cryptography + a known Argon2id reference such as
        // libsodium's crypto_pwhash, run side-by-side against BouncyCastle to
        // cross-check both the KDF output and the AEAD tag).
        val passphrase = "aad-order-vector-passphrase".toCharArray()
        val salt = "aad-order-vec-sa".toByteArray(Charsets.US_ASCII)
        val iv = "aad-order-iv".toByteArray(Charsets.US_ASCII)
        val expectedPlaintext = "aad-order-vector-plaintext".toByteArray(Charsets.US_ASCII)

        // Defensive shape checks: the synthetic vector below depends on these
        // lengths matching the envelope layout in ADR-0018.
        assertEquals(BackupCipherV2.SALT_BYTES_LENGTH, salt.size, "vector salt must be exactly 16 bytes")
        assertEquals(BackupCipherV2.IV_BYTES_LENGTH, iv.size, "vector iv must be exactly 12 bytes")

        @Suppress("ktlint:standard:property-naming")
        val EXPECTED_CIPHERTEXT_AND_TAG: ByteArray =
            byteArrayOf(
                // TODO(D1): replace with the AES-256-GCM ciphertext+tag bytes that an
                //   independent reference produces for (key, iv, plaintext, AAD) above,
                //   where AAD is constructed in the order magic || salt || iv. The
                //   length is plaintext.size + 16 (GCM tag) = 26 + 16 = 42 bytes.
            )

        val envelope =
            BackupCipherV2.MAGIC.toByteArray(Charsets.US_ASCII) +
                salt +
                iv +
                EXPECTED_CIPHERTEXT_AND_TAG

        // Pre-impl: cipher.decrypt() throws NotImplementedError below.
        // Post-impl, TODO unfilled: decrypt() throws BackupDecryptException because
        //   the envelope has no ciphertext+tag region (size == AAD only). Either
        //   failure points the next reader at the TODO via the assertFalse below.
        // Post-TODO-filled, correct AAD order: decrypt() returns the plaintext.
        // Post-TODO-filled, wrong AAD order: decrypt() throws BackupDecryptException
        //   on GCM tag mismatch.
        val recovered = cipher.decrypt(envelope, passphrase)
        assertFalse(
            EXPECTED_CIPHERTEXT_AND_TAG.isEmpty(),
            "AAD-order vector ciphertext is the D1 TODO; D1 must compute and fill in the bytes",
        )
        assertContentEquals(
            expectedPlaintext,
            recovered,
            "AAD construction order must be magic || salt || iv per ADR-0018 § Decision § Authentication",
        )
    }
}
