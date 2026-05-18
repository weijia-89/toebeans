package app.toebeans.core.backup

/**
 * JVM (and Android) actual for [BackupCipherV2Factory].
 *
 * D1 (per ADR-0018) replaces this with a real implementation that constructs
 * a concrete [BackupCipherV2] using AES-256-GCM via JCA, an Argon2id-derived
 * key from [Argon2idKdf], and AAD = `magic || salt || iv`. The cipher details
 * are documented in ADR-0018 § Decision § Cipher choice.
 *
 * Until D1 lands, [create] returns a stub whose encrypt/decrypt methods both
 * throw [NotImplementedError]. The test-as-spec tests in [BackupCipherV2Test]
 * exercise this stub and currently fail at runtime. Human review of the test
 * signatures gates the real implementation per AGENTS.md § Test-as-spec rules.
 */
internal class JvmBackupCipherV2(
    @Suppress("UnusedPrivateProperty") private val argon2idKdf: Argon2idKdf,
) : BackupCipherV2 {
    override fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
    ): ByteArray =
        throw NotImplementedError(
            "BackupCipherV2.encrypt is not implemented yet. " +
                "D1 (ADR-0018) lands AES-256-GCM with Argon2id-derived key and AAD binding.",
        )

    override fun decrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray =
        throw NotImplementedError(
            "BackupCipherV2.decrypt is not implemented yet. " +
                "D1 (ADR-0018) lands AES-256-GCM with Argon2id-derived key and AAD binding.",
        )
}

public actual class BackupCipherV2Factory public actual constructor(
    private val argon2idKdf: Argon2idKdf,
) {
    public actual fun create(): BackupCipherV2 = JvmBackupCipherV2(argon2idKdf)
}
