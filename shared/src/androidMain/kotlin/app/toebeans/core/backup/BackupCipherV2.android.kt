package app.toebeans.core.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android actual for [BackupCipherV2Factory]. Mirrors `jvmMain/BackupCipherV2.jvm.kt`
 * byte-for-byte. AES/GCM/NoPadding is available on every supported Android device
 * (API 26+, ARMv8 AES hardware acceleration per ADR-0018 § Decision § Symmetric
 * cipher). Consolidation tracked by the deferred intermediate-source-set ADR named
 * in `BackupCipher.android.kt`.
 *
 * See `BackupCipherV2.jvm.kt` for the full KDoc covering envelope layout,
 * failure-mode indistinguishability rationale, AAD construction order, and the
 * magic-mismatch-distinct-message exception. The body below MUST stay identical
 * to the JVM actual until the consolidation ADR lands.
 */
internal class AndroidBackupCipherV2(
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
                throw BackupDecryptException("Backup could not be decrypted.", t)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

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
    public actual fun create(): BackupCipherV2 = AndroidBackupCipherV2(argon2idKdf)
}
