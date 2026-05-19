package app.toebeans.core.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * Android actual for [Argon2idKdf]. Mirrors `jvmMain/Argon2idKdf.jvm.kt`
 * byte-for-byte.
 *
 * The duplication exists because KMP requires an actual for each compilation target.
 * Both actuals delegate to BouncyCastle's [Argon2BytesGenerator] per ADR-0018
 * § Decision § Library choice; BC's `bcprov-jdk18on` is pure-Java and runs unchanged
 * on Android API 26+ (no JNI, no per-ABI native library shipping). Consolidation
 * tracked by the deferred intermediate-source-set ADR named in `BackupCipher.android.kt`.
 *
 * See `Argon2idKdf.jvm.kt` for the full KDoc covering parameter pinning, UTF-8
 * passphrase encoding rationale, controlled-buffer wiping, and out-of-memory
 * handling. The body below MUST stay identical to the JVM actual until the
 * consolidation ADR lands.
 */
public actual class Argon2idKdf public actual constructor() {
    public actual fun derive(
        passphrase: CharArray,
        salt: ByteArray,
    ): ByteArray {
        require(salt.size >= ARGON2ID_MIN_SALT_BYTES) {
            "salt must be at least $ARGON2ID_MIN_SALT_BYTES bytes per RFC 9106 \u00a7 3.1; got ${salt.size}"
        }

        val passphraseBytes = passphraseToUtf8Bytes(passphrase)
        try {
            return try {
                val params =
                    Argon2Parameters
                        .Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withMemoryAsKB(ARGON2ID_MEMORY_COST_KIB)
                        .withIterations(ARGON2ID_ITERATIONS)
                        .withParallelism(ARGON2ID_PARALLELISM)
                        .withSalt(salt)
                        .build()
                val generator = Argon2BytesGenerator()
                generator.init(params)
                val output = ByteArray(ARGON2ID_OUTPUT_KEY_BYTES)
                generator.generateBytes(passphraseBytes, output)
                output
            } catch (oom: OutOfMemoryError) {
                throw IllegalStateException(
                    "Argon2id memory allocation failed; need ${ARGON2ID_MEMORY_COST_KIB / 1024} MiB " +
                        "free heap for the m=$ARGON2ID_MEMORY_COST_KIB, t=$ARGON2ID_ITERATIONS, " +
                        "p=$ARGON2ID_PARALLELISM parameter set per ADR-0018",
                    oom,
                )
            }
        } finally {
            passphraseBytes.fill(0)
        }
    }

    private fun passphraseToUtf8Bytes(passphrase: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(passphrase)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) {
            byteBuffer.array().fill(0)
        }
        return bytes
    }
}
