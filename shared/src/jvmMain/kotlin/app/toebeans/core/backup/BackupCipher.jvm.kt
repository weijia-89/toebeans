package app.toebeans.core.backup

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM (and Android) implementation of [BackupCipher] via JCA.
 *
 * - Android: AES-GCM and PBKDF2WithHmacSHA256 are both available on API 26+ (our minSdk).
 * - JVM: same; standard since JDK 8.
 *
 * @see BackupCipher
 */
internal class JvmBackupCipher(
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

        // Envelope: [magic(4)][salt(16)][iv(12)][ciphertext+tag(N+16)]
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
            // Wrong passphrase, tampered ciphertext, or truncated envelope all surface here.
            // We MUST NOT leak which one — see KDoc on BackupDecryptException.
            throw BackupDecryptException("Backup could not be decrypted.", t)
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(passphrase, salt, pbkdf2Iterations, BackupCipher.DERIVED_KEY_BITS)
        try {
            val keyBytes = keyFactory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            // Best-effort wipe; PBEKeySpec internally copies the passphrase but exposes a clearPassword().
            (spec as? PBEKeySpec)?.clearPassword()
        }
    }
}

public actual class BackupCipherFactory public actual constructor(
    private val pbkdf2Iterations: Int,
) {
    public actual fun create(): BackupCipher = JvmBackupCipher(pbkdf2Iterations)
}
