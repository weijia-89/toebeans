package app.toebeans.core.backup

/**
 * JVM (and Android) actual for [Argon2idKdf].
 *
 * D1 (per ADR-0018 § Followups) replaces the body of [derive] with a call to
 * `org.bouncycastle.crypto.generators.Argon2BytesGenerator` using the parameters
 * pinned at file scope in `Argon2idKdf.kt` ([ARGON2ID_MEMORY_COST_KIB],
 * [ARGON2ID_ITERATIONS], [ARGON2ID_PARALLELISM], [ARGON2ID_OUTPUT_KEY_BYTES],
 * [ARGON2ID_MIN_SALT_BYTES]). This file ships in the failing-test commit so the
 * tests compile and fail at runtime with [NotImplementedError]; the real
 * derivation lands after human review of the test signatures per AGENTS.md
 * § Test-as-spec rules.
 *
 * Until D1 lands, attempts to derive a key throw [NotImplementedError]. Calling
 * code that runs into this in production is a bug: there is no production path
 * to [Argon2idKdf] until ADR-0016's v2 trigger fires and the v2 cipher is
 * wired in.
 */
public actual class Argon2idKdf public actual constructor() {
    public actual fun derive(
        passphrase: CharArray,
        salt: ByteArray,
    ): ByteArray =
        throw NotImplementedError(
            "Argon2idKdf.derive is not implemented yet. " +
                "D1 (ADR-0018) lands the real Argon2id derivation via BouncyCastle. " +
                "This stub exists so the test-as-spec commit compiles.",
        )
}
