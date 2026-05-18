package app.toebeans.core.backup

/**
 * Android actual for [Argon2idKdf]. Mirrors `jvmMain/Argon2idKdf.jvm.kt`.
 *
 * The duplication exists because KMP requires an actual for each compilation target.
 * D1 implements both actuals identically via BouncyCastle (`org.bouncycastle.crypto
 * .generators.Argon2BytesGenerator`); BC is pure-Java and runs unchanged on Android
 * (no JNI, no per-ABI native library shipping). See ADR-0018 § Decision § Library
 * choice. Consolidation tracked by the deferred intermediate-source-set ADR named
 * in `BackupCipher.android.kt`.
 */
public actual class Argon2idKdf public actual constructor() {
    public actual fun derive(
        passphrase: CharArray,
        salt: ByteArray,
    ): ByteArray =
        throw NotImplementedError(
            "Argon2idKdf.derive is not implemented yet. " +
                "D1 (ADR-0018) lands the real Argon2id derivation via BouncyCastle.",
        )
}
