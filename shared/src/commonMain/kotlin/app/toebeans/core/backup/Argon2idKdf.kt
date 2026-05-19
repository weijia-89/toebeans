package app.toebeans.core.backup

// ADR-0018 § Decision § KDF parameters. Pinned to envelope magic TBN2; any change
// to a value below requires a new envelope magic (TBN3) per the parameter rotation
// policy. Top-level `const val` rather than an `expect` companion object: these
// values are platform-independent (Argon2id parameters are defined by RFC 9106,
// not by the host platform), and Kotlin MP forbids initialized properties inside
// `expect` declarations (and `const val` requires an initializer at the
// declaration site, making `expect const` doubly illegal).

/** Memory cost in kibibytes (64 MiB). RFC 9106 § 4 second-recommended option. */
public const val ARGON2ID_MEMORY_COST_KIB: Int = 65_536

/** Iteration count (three passes). */
public const val ARGON2ID_ITERATIONS: Int = 3

/**
 * Parallelism (one lane). Lower than RFC's second-recommended p = 4 for
 * deterministic single-derivation latency on asymmetric mobile cores;
 * memory-time product preserved at 64 MiB × 3 = 192 MiB·passes via
 * the iteration count.
 */
public const val ARGON2ID_PARALLELISM: Int = 1

/** Output key length in bytes (256 bits). */
public const val ARGON2ID_OUTPUT_KEY_BYTES: Int = 32

/** Minimum salt length per RFC 9106 § 3.1. */
public const val ARGON2ID_MIN_SALT_BYTES: Int = 8

/**
 * Memory-hard key derivation function (KDF) for the v2 backup cipher envelope.
 *
 * Per ADR-0018, the v2 cipher rotates from PBKDF2-HMAC-SHA256 to Argon2id (RFC 9106).
 * Argon2id is a memory-hard KDF: deriving a single key requires a large fixed memory
 * allocation that defeats the per-iteration speedup an attacker gets from a GPU or
 * application-specific integrated circuit (ASIC).
 *
 * **Parameter set (fixed in this version of the interface):**
 *  - Memory cost = [ARGON2ID_MEMORY_COST_KIB] kibibytes (64 MiB).
 *  - Iterations = [ARGON2ID_ITERATIONS] (three passes).
 *  - Parallelism = [ARGON2ID_PARALLELISM] (one lane).
 *  - Output key length = [ARGON2ID_OUTPUT_KEY_BYTES] bytes (256 bits).
 *
 * **Parameter rotation policy:** parameters are pinned to the envelope magic value
 * (`TBN2` for this version). Any future change to the parameter set lands as a new
 * envelope magic (`TBN3`) plus a new [Argon2idKdf] file or version. See ADR-0018
 * § Decision § Envelope format.
 *
 * **Threat model:** the parameters are calibrated to a capable offline attacker
 * with unlimited compute through 2030, per the project privacy guardrail. See
 * ADR-0018 § Context § Threat model carried forward.
 *
 * **Implementation:** declared `expect` in commonMain. The platform actuals live
 * in `jvmMain` and `androidMain` and delegate to `org.bouncycastle.crypto.generators
 * .Argon2BytesGenerator` per ADR-0018 § Decision § Library choice. Both actuals
 * currently throw [NotImplementedError]; the real derivation lands in D1 after
 * human review of this test-as-spec commit per AGENTS.md § Test-as-spec rules.
 */
public expect class Argon2idKdf() {
    /**
     * Derive a 32-byte symmetric key from a user-entered passphrase and an
     * envelope-specific salt.
     *
     * @param passphrase the user's passphrase as a [CharArray]. The caller is
     *   responsible for wiping this array after use; this function does not
     *   retain a reference.
     * @param salt the envelope salt. Must be at least [ARGON2ID_MIN_SALT_BYTES]
     *   bytes per RFC 9106 § 3.1; 16 bytes is the project standard, matching
     *   the TBN1 slot.
     * @return a 32-byte derived key.
     */
    public fun derive(
        passphrase: CharArray,
        salt: ByteArray,
    ): ByteArray
}
