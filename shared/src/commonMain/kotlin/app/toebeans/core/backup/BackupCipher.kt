// FITNESS-ALLOW-PBKDF2-FILE: TBN1 legacy cipher per ADR-0018 § Implementation footprint. V2 (Argon2id) lives in BackupCipherV2.kt.
package app.toebeans.core.backup

/**
 * Symmetric encryption for backup payloads.
 *
 * The same plaintext + passphrase + salt + IV produces the same ciphertext, but salt and IV
 * are randomly generated for each [encrypt] call, so re-encrypting the same data produces a
 * different envelope. Decryption recovers the salt and IV from the envelope prefix.
 *
 * **Envelope layout (v1):**
 * ```
 * [magic(4)="TBN1"][salt(16)][iv(12)][ciphertext+tag(N+16)]
 * ```
 *
 * **Algorithm (v1):**
 *  - PBKDF2-HMAC-SHA256 with [DEFAULT_PBKDF2_ITERATIONS] iterations to derive a 256-bit key
 *    from the user's passphrase. (Argon2id is preferred but unavailable on KMP without a
 *    platform-specific dependency. Bumping to Argon2 is a follow-up — see ADR for backup-codec.)
 *  - AES-256-GCM with a 128-bit tag for encryption + authentication.
 *
 * **Security posture:**
 *  - The passphrase is never persisted by toebeans application code.
 *  - The salt is sampled from the platform's cryptographically secure RNG per encryption.
 *  - The IV (96 bits) is sampled per encryption. A repeat IV under the same key would catastrophically
 *    weaken AES-GCM; the per-encryption random IV avoids this.
 *  - Authentication: GCM provides a 128-bit MAC tag. A tampered envelope fails decryption.
 *
 * @see BackupCipherFactory for obtaining a configured instance per platform.
 */
public interface BackupCipher {
    public fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
    ): ByteArray

    public fun decrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray

    public companion object {
        public const val MAGIC: String = "TBN1"
        public const val MAGIC_BYTES_LENGTH: Int = 4
        public const val SALT_BYTES_LENGTH: Int = 16
        public const val IV_BYTES_LENGTH: Int = 12
        public const val GCM_TAG_BITS: Int = 128

        /**
         * 600,000 follows OWASP 2024 guidance for PBKDF2-HMAC-SHA256.
         * Tests SHOULD override with a small value (e.g. 1_000) via the factory's
         * test-only constructor to keep test runtime sane.
         */
        public const val DEFAULT_PBKDF2_ITERATIONS: Int = 600_000
        public const val DERIVED_KEY_BITS: Int = 256
    }
}

/**
 * Indicates a decrypt failure due to wrong passphrase, tampered envelope, or unsupported format.
 *
 * We deliberately do NOT distinguish "wrong passphrase" from "tampered ciphertext" at the API
 * boundary: that distinction would leak an oracle to an attacker who can submit candidate
 * passphrases. The UI surfaces a single user-facing message.
 */
public class BackupDecryptException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Platform-specific factory for [BackupCipher]. Implemented in `jvmMain` (used by Android) and
 * any future iOS source set.
 *
 * @param pbkdf2Iterations override only for tests; production callers must use the default.
 */
public expect class BackupCipherFactory(
    pbkdf2Iterations: Int = BackupCipher.DEFAULT_PBKDF2_ITERATIONS,
) {
    public fun create(): BackupCipher
}
