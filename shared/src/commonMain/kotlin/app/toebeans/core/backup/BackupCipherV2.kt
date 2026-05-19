package app.toebeans.core.backup

/**
 * v2 backup cipher per ADR-0018: AES-256-GCM with a passphrase-derived key via
 * [Argon2idKdf], 12-byte random nonce, 128-bit authentication tag, and AEAD
 * additional authenticated data (AAD) binding `magic || salt || iv` (32 bytes).
 *
 * **Envelope layout (`TBN2`):**
 * ```
 * [magic(4)="TBN2"][salt(16)][iv(12)][ciphertext+tag(N+16)]
 * ```
 *
 * The layout is byte-identical to the v1 [BackupCipher] envelope except for the
 * 4-byte magic value. The semantic change is the AEAD (authenticated encryption
 * with associated data) construction: TBN2 binds `magic || salt || iv` as AAD,
 * which closes the downgrade-attack class (an attacker who flips the magic from
 * `TBN2` to `TBN1` invalidates the GCM tag). TBN1 did not use AAD.
 *
 * **Authentication:** GCM's 128-bit tag is the message authentication code (MAC,
 * a cryptographic checksum that proves the ciphertext has not been tampered with).
 * No additional MAC is needed.
 *
 * **Backward compatibility:** TBN1 envelopes (none exist in production per
 * ADR-0016, because the v1 cipher was never wired to a production code path)
 * are rejected with a clear error by [decrypt]. The TBN1 decrypt path in
 * [BackupCipher] stays in the codebase as the dormant test surface.
 *
 * **Implementation:** declared as an interface in commonMain. The expect factory
 * [BackupCipherV2Factory] yields a concrete instance via JVM and Android actuals.
 * Both actuals currently throw [NotImplementedError]; the real cipher lands in
 * D1 after human review of this test-as-spec commit.
 */
public interface BackupCipherV2 {
    public fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
    ): ByteArray

    public fun decrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray

    public companion object {
        public const val MAGIC: String = "TBN2"
        public const val MAGIC_BYTES_LENGTH: Int = 4
        public const val SALT_BYTES_LENGTH: Int = 16
        public const val IV_BYTES_LENGTH: Int = 12
        public const val GCM_TAG_BITS: Int = 128

        /**
         * Length of the AEAD additional authenticated data (AAD), which is the
         * concatenation `magic || salt || iv`. The whole envelope header is bound
         * to the ciphertext via the GCM authentication tag.
         */
        public const val AAD_BYTES_LENGTH: Int =
            MAGIC_BYTES_LENGTH + SALT_BYTES_LENGTH + IV_BYTES_LENGTH

        /** Derived key length (matches [ARGON2ID_OUTPUT_KEY_BYTES]). */
        public const val DERIVED_KEY_BITS: Int = 256
    }
}

/**
 * Platform-specific factory for [BackupCipherV2]. Implemented in `jvmMain`
 * (used by both pure JVM and Android targets) and `androidMain` per the
 * existing duplication pattern in [BackupCipherFactory].
 *
 * @param argon2idKdf the [Argon2idKdf] instance used to derive the symmetric
 *   key. Injectable for testing; production callers use the default-constructed
 *   instance which carries the parameters fixed in ADR-0018.
 */
public expect class BackupCipherV2Factory(
    argon2idKdf: Argon2idKdf = Argon2idKdf(),
) {
    public fun create(): BackupCipherV2
}
