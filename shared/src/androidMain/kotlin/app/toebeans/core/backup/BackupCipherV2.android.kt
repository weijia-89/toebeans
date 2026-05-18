package app.toebeans.core.backup

/**
 * Android actual for [BackupCipherV2Factory]. Mirrors `jvmMain/BackupCipherV2.jvm.kt`.
 *
 * D1 implements this identically to the JVM actual; AES-256-GCM via JCA is available
 * on every supported Android device (API 26+, ARMv8 AES hardware acceleration). See
 * ADR-0018 § Decision § Cipher choice.
 */
internal class AndroidBackupCipherV2(
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
    public actual fun create(): BackupCipherV2 = AndroidBackupCipherV2(argon2idKdf)
}
