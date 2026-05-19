// FITNESS-ALLOW-PBKDF2-FILE: TBN1 legacy cipher per ADR-0018 § Implementation footprint. V2 (Argon2id) actual lives in BackupCipherV2.android.kt.
package app.toebeans.core.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android implementation of [BackupCipher].
 *
 * This is the SAME impl as `jvmMain/BackupCipher.jvm.kt` because Android exposes JCA's
 * `AES/GCM/NoPadding` and `PBKDF2WithHmacSHA256` since API 26 (our minSdk).
 *
 * The duplication exists because KMP requires an actual for each compilation target and
 * we have not introduced an intermediate source set hierarchy at v0.1. ADR to consolidate:
 * docs/adr/0007-jvm-android-shared-source-set.md (deferred to slice 1).
 */
internal class AndroidBackupCipher(
    private val pbkdf2Iterations: Int,
) : BackupCipher {
    private val secureRandom = SecureRandom()
    private val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")

    override fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        val salt = ByteArray(BackupCipher.SALT_BYTES_LENGTH).also(secureRandom::nextBytes)
        val iv = ByteArray(BackupCipher.IV_BYTES_LENGTH).also(secureRandom::nextBytes)
        val key = deriveKey(passphrase, salt)

        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(BackupCipher.GCM_TAG_BITS, iv))
            }
        val ciphertext = cipher.doFinal(plaintext)

        val magic = BackupCipher.MAGIC.toByteArray(Charsets.US_ASCII)
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
    }

    override fun decrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        val minimumEnvelopeSize =
            BackupCipher.MAGIC_BYTES_LENGTH +
                BackupCipher.SALT_BYTES_LENGTH +
                BackupCipher.IV_BYTES_LENGTH +
                (BackupCipher.GCM_TAG_BITS / 8)
        if (envelope.size < minimumEnvelopeSize) {
            throw BackupDecryptException("Envelope too short (${envelope.size} bytes).")
        }

        val magic = envelope.copyOfRange(0, BackupCipher.MAGIC_BYTES_LENGTH)
        if (!magic.contentEquals(BackupCipher.MAGIC.toByteArray(Charsets.US_ASCII))) {
            throw BackupDecryptException("Not a toebeans backup envelope (magic mismatch).")
        }

        val saltStart = BackupCipher.MAGIC_BYTES_LENGTH
        val ivStart = saltStart + BackupCipher.SALT_BYTES_LENGTH
        val ciphertextStart = ivStart + BackupCipher.IV_BYTES_LENGTH

        val salt = envelope.copyOfRange(saltStart, ivStart)
        val iv = envelope.copyOfRange(ivStart, ciphertextStart)
        val ciphertext = envelope.copyOfRange(ciphertextStart, envelope.size)

        val key = deriveKey(passphrase, salt)
        return try {
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(BackupCipher.GCM_TAG_BITS, iv))
                }
            cipher.doFinal(ciphertext)
        } catch (t: Throwable) {
            throw BackupDecryptException("Backup could not be decrypted.", t)
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, pbkdf2Iterations, BackupCipher.DERIVED_KEY_BITS)
        try {
            val keyBytes = keyFactory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

public actual class BackupCipherFactory public actual constructor(
    private val pbkdf2Iterations: Int,
) {
    public actual fun create(): BackupCipher = AndroidBackupCipher(pbkdf2Iterations)
}
