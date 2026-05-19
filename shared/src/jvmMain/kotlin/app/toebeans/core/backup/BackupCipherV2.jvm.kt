package app.toebeans.core.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM (and Android) actual for [BackupCipherV2Factory]. Implements the v2 AEAD
 * per ADR-0018 § Decision: AES-256-GCM via JCA, Argon2id-derived key, AAD =
 * `magic || salt || iv` (32 bytes).
 *
 * **Envelope layout (`TBN2`):**
 * ```
 * [magic(4)="TBN2"][salt(16)][iv(12)][ciphertext+tag(N+16)]
 * ```
 *
 * **Failure-mode indistinguishability:** all four decrypt failure modes that
 * depend on the passphrase (wrong passphrase, tampered ciphertext, tampered
 * AAD via salt or iv flip, post-magic truncation) surface as a single
 * [BackupDecryptException] with message `"Backup could not be decrypted."` —
 * byte-for-byte identical to V1. The single exception class with a uniform
 * message blocks the oracle an attacker would otherwise gain by submitting
 * candidate passphrases and observing message text.
 *
 * Magic-mismatch is the one decrypt failure mode with a distinct message,
 * because it does NOT depend on the passphrase: an attacker who can submit a
 * non-TBN2 envelope learns nothing about the user's passphrase from the
 * "magic mismatch" message. Surfacing it distinctly lets the UI tell the user
 * "this is the wrong file type" instead of "decryption failed," which is the
 * useful diagnostic. The TBN1 envelope test pins this contract.
 *
 * **Android compatibility:** AES/GCM/NoPadding is available on every supported
 * Android device (API 26+, ARMv8 AES hardware acceleration per ADR-0018
 * § Decision § Symmetric cipher). The Android actual mirrors this file
 * byte-for-byte.
 */
internal class JvmBackupCipherV2(
    private val argon2idKdf: Argon2idKdf,
) : BackupCipherV2 {
    private val secureRandom = SecureRandom()

    override fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        val salt = ByteArray(BackupCipherV2.SALT_BYTES_LENGTH).also(secureRandom::nextBytes)
        val iv = ByteArray(BackupCipherV2.IV_BYTES_LENGTH).also(secureRandom::nextBytes)
        val magic = BackupCipherV2.MAGIC.toByteArray(Charsets.US_ASCII)

        val keyBytes = argon2idKdf.derive(passphrase, salt)
        try {
            val key = SecretKeySpec(keyBytes, "AES")
            val aad = buildAad(magic, salt, iv)

            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(BackupCipherV2.GCM_TAG_BITS, iv))
                    updateAAD(aad)
                }
            val ciphertext = cipher.doFinal(plaintext)

            // Envelope: magic(4) || salt(16) || iv(12) || ciphertext+tag(N+16).
            val envelope = ByteArray(magic.size + salt.size + iv.size + ciphertext.size)
            var offset = 0
            magic.copyInto(envelope, offset)
            offset += magic.size
            salt.copyInto(envelope, offset)
            offset += salt.size
            iv.copyInto(envelope, offset)
            offset += iv.size
            ciphertext.copyInto(envelope, offset)
            return envelope
        } finally {
            keyBytes.fill(0)
        }
    }

    override fun decrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        val magicLen = BackupCipherV2.MAGIC_BYTES_LENGTH
        val saltLen = BackupCipherV2.SALT_BYTES_LENGTH
        val ivLen = BackupCipherV2.IV_BYTES_LENGTH
        val tagLen = BackupCipherV2.GCM_TAG_BITS / 8
        val minimumEnvelopeSize = magicLen + saltLen + ivLen + tagLen

        if (envelope.size < minimumEnvelopeSize) {
            throw BackupDecryptException("Envelope too short (${envelope.size} bytes).")
        }

        val expectedMagic = BackupCipherV2.MAGIC.toByteArray(Charsets.US_ASCII)
        val actualMagic = envelope.copyOfRange(0, magicLen)
        if (!actualMagic.contentEquals(expectedMagic)) {
            // Distinct from the post-magic failures: magic mismatch does NOT
            // depend on the passphrase, so surfacing it with a structural
            // message ("magic"/"TBN") leaks no oracle. The TBN1 envelope test
            // pins this contract.
            throw BackupDecryptException("Not a toebeans backup envelope (magic mismatch; expected TBN2).")
        }

        val saltStart = magicLen
        val ivStart = saltStart + saltLen
        val ciphertextStart = ivStart + ivLen

        val salt = envelope.copyOfRange(saltStart, ivStart)
        val iv = envelope.copyOfRange(ivStart, ciphertextStart)
        val ciphertext = envelope.copyOfRange(ciphertextStart, envelope.size)

        val keyBytes = argon2idKdf.derive(passphrase, salt)
        try {
            val key = SecretKeySpec(keyBytes, "AES")
            val aad = buildAad(expectedMagic, salt, iv)

            return try {
                val cipher =
                    Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(BackupCipherV2.GCM_TAG_BITS, iv))
                        updateAAD(aad)
                    }
                cipher.doFinal(ciphertext)
            } catch (t: Throwable) {
                // Wrong passphrase, tampered ciphertext, tampered AAD (via salt
                // or iv flip), or post-magic truncation all surface here. We
                // MUST NOT leak which one — the uniform message is the
                // indistinguishability contract per ADR-0018 § Followups.
                throw BackupDecryptException("Backup could not be decrypted.", t)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * Construct the AEAD additional authenticated data per ADR-0018 § Decision
     * § Authentication: `magic || salt || iv` in this exact order. The order
     * is pinned by the synthetic-vector test `AAD construction order matches
     * ADR-0018 (magic, salt, iv)` in [BackupCipherV2Test]; any other
     * permutation produces a GCM tag mismatch against the reference vector.
     */
    private fun buildAad(
        magic: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val aad = ByteArray(BackupCipherV2.AAD_BYTES_LENGTH)
        var offset = 0
        magic.copyInto(aad, offset)
        offset += magic.size
        salt.copyInto(aad, offset)
        offset += salt.size
        iv.copyInto(aad, offset)
        return aad
    }
}

public actual class BackupCipherV2Factory public actual constructor(
    private val argon2idKdf: Argon2idKdf,
) {
    public actual fun create(): BackupCipherV2 = JvmBackupCipherV2(argon2idKdf)
}
