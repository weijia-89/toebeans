package app.toebeans.core.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * JVM (and Android) actual for [Argon2idKdf]. Delegates to BouncyCastle's
 * [Argon2BytesGenerator] per ADR-0018 § Decision § Library choice. The Android
 * actual in `androidMain` is identical because BC's `bcprov-jdk18on` is
 * pure-Java and runs unchanged on Android (API 26+).
 *
 * **Parameters** are pinned at file scope in `Argon2idKdf.kt` ([ARGON2ID_MEMORY_COST_KIB],
 * [ARGON2ID_ITERATIONS], [ARGON2ID_PARALLELISM], [ARGON2ID_OUTPUT_KEY_BYTES]).
 * Any change to a parameter requires a new envelope magic (`TBN3`) per
 * ADR-0018 § Decision § Envelope format, not a quiet edit here.
 *
 * **Passphrase encoding:** the [CharArray] passphrase is encoded as UTF-8 bytes
 * via [CharBuffer.wrap] + [StandardCharsets.UTF_8.encode] to keep the
 * intermediate `byte[]` under our control (so we can zero-fill it in `finally`).
 * BC's own `generateBytes(char[], byte[])` overload would do the UTF-8 conversion
 * for us, but its internal `Strings.toUTF8ByteArray` uses a `ByteArrayOutputStream`
 * we cannot reach to wipe. The project's nation-state-grade threat model per
 * ADR-0018 § Context § Threat model justifies the extra controlled-buffer hop.
 *
 * **Why UTF-8:** RFC 9106 does not mandate a passphrase charset; UTF-8 is the
 * de-facto modern standard, matches the Argon2id cross-check (Python's
 * `argon2-cffi`) used to fill the KAT vector, and is required for the existing
 * `unicode passphrase with emoji and combining characters round-trips` test to
 * round-trip non-ASCII passphrases. The choice is documented here at the
 * pinning site rather than only in the session log.
 *
 * **Out-of-memory handling:** Argon2id's 64 MiB allocation can fail on
 * low-memory Android devices per ADR-0018 § Consequences § Negative. The catch
 * wraps [OutOfMemoryError] as [IllegalStateException] with a memory-related
 * message so the UI layer can surface a non-destructive error rather than
 * crashing per ADR-0018 § Mitigations. The ADR also names a pre-encryption
 * free-heap check at the UI layer; that check lives in the caller (the v2
 * cipher's export flow), not here.
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
                // OOM may fire at ANY point in the BC pipeline under a saturated
                // heap: Builder.build(), Argon2BytesGenerator() ctor, init(params),
                // the output ByteArray allocation, or (most commonly) inside
                // generateBytes() which allocates the 64 MiB Argon2 memory matrix.
                // The catch sits at the outer level so all five OOM sites funnel
                // into the same graceful IllegalStateException. The
                // Argon2idKdfJvmTest::low-memory test pins this contract.
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

    /**
     * Encode the [CharArray] passphrase as UTF-8 bytes. Returns a fresh
     * [ByteArray] under the caller's control; the caller is expected to wipe
     * it after use. The intermediate buffer used by the encoder is wiped here
     * before returning so the only live copy is the returned array.
     */
    private fun passphraseToUtf8Bytes(passphrase: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(passphrase)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        // The encoder allocates a heap ByteBuffer for typical inputs; wipe the
        // backing array so the only live copy of the encoded passphrase is the
        // returned `bytes` array.
        if (byteBuffer.hasArray()) {
            byteBuffer.array().fill(0)
        }
        return bytes
    }
}
