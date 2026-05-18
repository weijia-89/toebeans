# ADR-0018: Argon2id backup cipher design. The cipher that activates when an ADR-0016 v2 trigger fires.

Date: 2026-05-18
Status: Accepted
Deciders: Wei Jia (with Cascade)
Supersedes: the design-deferral stance in ADR-0016 (the v1 posture itself remains in force until a trigger fires and D1 lands the implementation per this ADR).

## Context

ADR-0016 (2026-05-17) decided that v1 ships plain JSON for the manual backup export and deferred the encrypted posture until one of four triggers fires:

- Owner personally identifiable information (PII) enters the data model.
- Backup sharing with a third party becomes a supported flow (the M2 caregiver-share QR per ROADMAP).
- Cloud sync ships at M6, which already requires end-to-end encryption (E2EE) per the project privacy guardrail.
- A user explicitly requests encrypted backup.

ADR-0016 left two things unsettled. The first is the design of the encrypted format. The second is the migration path from the existing `TBN1` envelope, which the dormant `BackupCipher` infrastructure produces today (PBKDF2-HMAC-SHA256 (Password-Based Key Derivation Function 2 with HMAC-SHA256), 600k iterations, AES-256-GCM, 16-byte salt, 12-byte initialization vector, 128-bit Galois/Counter Mode authentication tag).

The followup register at `docs/issues/v0.1-followups.md` item 7 names Argon2id (a memory-hard key derivation function (KDF) that won the 2015 Password Hashing Competition and was standardized in RFC 9106) as the v2 KDF and proposes envelope magic `TBN2` with a backward-compatible decrypt path for `TBN1`. That register entry is one bullet long. It does not pick parameters; it does not pick a library; it does not pick whether the cipher rotates as well; it does not name an authenticated-encryption-with-associated-data (AEAD) binding strategy for the envelope header.

This ADR fills those gaps. The implementation, called D1 in this document, is a separate session.

### Why the deferral is closing now

ADR-0016's deferral was a design-deferral with a clear off-ramp; the encryption posture was always going to come back when a trigger fired. The trigger conditions in §Decision of ADR-0016 are concrete and at least one of them (M1.2 internal beta surfacing user-shared backups, M6 cloud sync gating on E2EE) is on the near roadmap. Carrying the design forward as a one-line followup register entry is fragile because the design decisions interact (parameter choice depends on library choice depends on KMP source-set strategy), and resolving them in a future "we need this now" moment risks shortcuts. The design lands in advance so the implementation session can read the answers off the page.

### Threat model carried forward

The v1 threat model (per SECURITY.md and the underlying spec at `docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md`) names a bulk-scraper of cloud storage as the adversary against backup files. The Wei-global privacy guardrail upgrades the bar: any system that stores user data must be designed so it cannot be used by mass surveillance or intelligence analysts to track and identify people, even given full server-side compromise or legal compulsion. The encrypted backup is an at-rest defense; the parameter selection in this ADR is calibrated to that upgraded bar. The bulk-scraper bar of v1 sits below what this ADR targets.

Concretely, the adversary model for the encrypted backup is:

- A capable attacker with the encrypted envelope and unlimited offline compute through 2030.
- The attacker does not have the user's passphrase.
- The attacker may have arbitrary auxiliary information about pet-medication patterns in general, but no targeted prior on the specific user's passphrase choices.

The parameter selection below sets the offline-cracking cost high enough that a brute-force search over the realistic passphrase space is uneconomic per envelope, even with custom application-specific integrated circuits (ASICs). RFC 9106 § 7 names this exact failure model and is the source for the parameter floor.

## Decision

### Symmetric cipher: AES-256-GCM

The cipher does not rotate. The existing `TBN1` envelope already uses AES-256-GCM with a 128-bit authentication tag, a 16-byte salt, and a 12-byte nonce (number-used-once, the cipher's per-message random value). The choice is defensible on its merits:

- Hardware acceleration. ARMv8-A AES extensions are present on every Android device running our minSdk (API 26, Android 8.0, released 2017; ARMv8-A AES has been mandatory on AArch64 since 2014). AES-256-GCM encrypts at multi-GB/s on a modern Pixel.
- Maturity. AES-256-GCM is the dominant AEAD construction in TLS, IPsec, and the Signal protocol. It has roughly two decades of operational deployment.
- JCA (Java Cryptography Architecture) support. The existing implementation in `shared/src/jvmMain/kotlin/app/toebeans/core/backup/BackupCipher.jvm.kt` already uses `Cipher.getInstance("AES/GCM/NoPadding")`. No new platform dependency for the cipher itself.

XChaCha20-Poly1305 is a strong alternative and gets explicit consideration in the Rejected alternatives section below. Briefly: it would require a new envelope shape (24-byte extended nonce instead of 12-byte), would require BouncyCastle or a custom backport on Android (JCA does not expose XChaCha20-Poly1305 in current Android API levels), and would not materially improve the security posture for the per-envelope-random-key construction used here.

### Key derivation: Argon2id with explicit parameters

The KDF rotates. PBKDF2-HMAC-SHA256 is replaced by Argon2id. The chosen parameters are:

- Memory cost m = 65536 KiB (64 MiB).
- Iterations t = 3.
- Parallelism p = 1 lane.
- Salt length = 16 bytes (unchanged from `TBN1`).
- Output key length = 32 bytes (256 bits, unchanged).

Rationale:

- RFC 9106 § 4 lists two recommended parameter sets. The first option is m = 2 GiB, t = 1, p = 4. The second option is m = 64 MiB, t = 3, p = 4. Mobile constraint makes the first option unreachable (a 2 GiB allocation will be killed by the Android low-memory killer on the majority of in-market devices). The second option is the floor we adopt, with parallelism reduced to p = 1.
- The parallelism reduction (p = 1 instead of p = 4) trades single-derivation wall-clock time for deterministic behavior across asymmetric core layouts (big.LITTLE, Tensor G2's mix of Cortex-X1 + A78 + A55). On a single passphrase derivation done at user-initiated export and import time, p = 1 is more predictable. The asymptotic ASIC-attack cost (memory-time area-time product) is preserved by the t = 3 choice: total memory-time product is 64 MiB × 3 passes = 192 MiB·passes, the same total cost as the RFC's second option (64 MiB × 3 = 192 MiB·passes; the lanes parallelize the same total work).
- OWASP's 2024 Password Storage Cheat Sheet names m = 19 MiB, t = 2, p = 1 as the minimum and m = 47 MiB, t = 1, p = 1 as a second tier. We pick a stricter parameter set because the project's privacy guardrail names a nation-state adversary model. The cheat sheet's defaults are calibrated to a password-database adversary, which is a lower bar than what this ADR targets.

Salt management: per envelope, randomly sampled at encryption time from the platform's cryptographically secure random number generator (`java.security.SecureRandom`). The salt lives in the envelope prefix and is recovered at decrypt time. Sixteen bytes exceeds RFC 9106 § 3.1's recommended minimum of 8 bytes and matches the existing `TBN1` slot, simplifying the envelope diff.

### Nonce management: per envelope, randomly sampled, 12 bytes

The AES-GCM nonce (also called the initialization vector or IV in JCA terminology) is 96 bits (12 bytes), sampled per encryption from `SecureRandom`. Two facts make this safe at the scale of manual backup exports:

- Per-envelope random salt means per-envelope unique derived key. AES-GCM nonce reuse is catastrophic only under the same key; with a unique key per envelope, the nonce-reuse failure mode is closed off independently.
- The birthday bound for random 96-bit nonces under a fixed key is roughly 2 to the 48th envelopes. Even if a user re-exports a backup once per hour for a century, the count is far below this bound. Combined with the per-envelope unique key, the safety margin is large.

### Authentication: GCM's built-in 128-bit tag, with envelope header bound as AEAD associated data

GCM's authentication tag (the message authentication code, or MAC, which proves the ciphertext has not been tampered with) is 128 bits. This is the AEAD tag and no additional MAC is required.

The TBN2 envelope binds the header bytes as AEAD additional authenticated data (AAD, the unencrypted-but-authenticated portion of an AEAD construction). The AAD is the concatenation of:

- The 4-byte magic value `TBN2`.
- The 16-byte salt.
- The 12-byte nonce.

Binding the header as AAD closes one specific attack class: an adversary who attempts to downgrade the envelope by flipping the magic bytes from `TBN2` to `TBN1` cannot do so without invalidating the GCM tag, since the magic value is now part of the authenticated input. The `TBN1` envelope did not use AAD. The `TBN2` envelope does. This is a small but free defense-in-depth improvement.

### Envelope format

```
[magic(4)="TBN2"][salt(16)][iv(12)][ciphertext+tag(N+16)]
```

The layout is identical to `TBN1` except for the magic value. Parameter rotation (a future bump from m = 64 MiB to m = 128 MiB, for example) goes through a new magic value `TBN3`. Embedding parameters in the envelope itself is rejected in the alternatives section below. The pinning-to-magic strategy is consistent with the migration pattern ADR-0016 named for `TBN1` to `TBN2` (an importer dispatches on the first 4 bytes), and it sidesteps the parameter-confusion question entirely: each magic value is bound to a single fixed parameter set in the code's lookup table.

### Library choice: org.bouncycastle:bcprov-jdk18on

The JCA on JDK 21 and Android API 36 does not expose Argon2id. A third-party dependency is required. The chosen library is BouncyCastle's `bcprov-jdk18on` artifact, specifically the `org.bouncycastle.crypto.generators.Argon2BytesGenerator` class.

Rationale:

- Pure Java, no JNI. JNI bindings to libargon2 (such as `de.mkammerer:argon2-jvm`) would be faster, but they require shipping native shared objects per architecture (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and add an Android build-time ABI variation surface. The pure-Java implementation is slower in absolute terms but eliminates the JNI integration risk.
- Mature. `Argon2BytesGenerator` landed in BouncyCastle 1.60 (released 2018) and has been a stable class through the BC 1.x line. Seven-plus years of production use.
- Android-compatible. The `bcprov-jdk18on` artifact's package namespace is `org.bouncycastle.*`, which does not collide with the Android platform's bundled and stripped-down `com.android.org.bouncycastle.*` internal copy. Adding the dependency does not break existing JCA providers.
- KMP-compatible via the existing expect/actual shape. The `BackupCipher.kt` interface in `commonMain` already uses `expect class BackupCipherFactory`. The Argon2id implementation lives in `jvmMain` (used by the JVM target) and `androidMain` (used by the Android target), both of which can depend on `bcprov-jdk18on`. Any future iOS source set will use CryptoKit or a Swift Argon2id package and is out of scope for this ADR.

The dependency addition lands in D1. This ADR records the parameter values and library identity; it performs no dependency edits. D1 is a vibe-dangerous change under AGENTS.md § Vibe-safety tiers because it adds a Gradle dependency, and the hallucination-check discipline in AGENTS.md § Hallucination vigilance applies. The library has been on Maven Central since 2000 and the specific artifact since 2021 (the `jdk18on` suffix marks the JDK 1.8+ branch). First-seen far exceeds the 30-day floor.

## Consequences

### Positive

- The dormant cipher gains a real plan. ADR-0016 left the cipher tested but never wired; this ADR records the design so the implementation session can land in a single sitting rather than re-deriving choices under deadline pressure.
- Encrypted backup becomes a viable answer when a trigger fires. The M1.2 internal beta can ship a caregiver-share flow without the design becoming a blocker. M6 cloud sync gains a backup-envelope format that reuses the KDF infrastructure.
- The AAD-binding upgrade closes a small downgrade-attack class that the `TBN1` envelope did not address.
- Magic-bump migration matches the project's existing pattern (ADR-0016 named the pattern; ADR-0018 confirms it).

### Negative

- Argon2id is computationally expensive. The 64 MiB memory cost dominates the operation. A representative Pixel 7 (released 2022, Tensor G2) running `Argon2BytesGenerator` with m = 64 MiB, t = 3, p = 1 derives a key in the range of 1.5 to 3 seconds (literature benchmark range; D1 measures the exact figure on real device hardware and adjusts the user-facing latency expectation accordingly). On a 2020-era Pixel 4a (Snapdragon 730G, 6 GB RAM), the same parameters are likely 3 to 5 seconds. On a low-end 2018 device with 2 GB RAM, the 64 MiB allocation may fail under memory pressure; D1 must surface a graceful error and a retry path rather than crashing.
- The pure-Java BouncyCastle implementation is slower than the libargon2 C implementation by a constant factor (rough estimate: 2x to 3x slower). The choice trades absolute speed for JNI-free portability. Mitigation: the operation runs off the main thread with a progress indicator. The latency cost lands on a user who has explicitly chosen to export an encrypted backup. The medication-firing path is untouched by this work.
- The Gradle dependency surface grows by one artifact (`bcprov-jdk18on` is roughly 8 MB packed, 25 MB unpacked). Mitigation: R8 shrinks unused BC classes aggressively, since only `Argon2BytesGenerator` and its direct dependencies are referenced.
- A backup file exported under TBN1 in the dormant-cipher era (none exist in production, per ADR-0016, because the cipher was never wired) loses its status as a supported encrypt format. The TBN1 decrypt path stays in the codebase so the dormant tests do not bit-rot; the importer is taught to dispatch on magic and surface a clear error if a TBN1 envelope is presented, with no silent auto-upgrade path.
- Future parameter rotation requires a magic bump and a code change in the parameter lookup table. There is no runtime config flip; the parameter set is fixed in code per envelope magic. Mitigation: the parameter table in code is small and the bump pattern is documented here.

### Mitigations

- The pre-encryption check at D1 verifies the device has at least 128 MiB free heap before attempting the 64 MiB Argon2id allocation. If the check fails, the UI surfaces a non-destructive error and offers the plain JSON path as fallback.
- The user-facing latency expectation is named in the export dialog ("Encrypting your backup. This may take a few seconds."). The wording avoids a precision claim that does not survive across devices.
- D1 ships a known-answer test against the RFC 9106 § 5 test vectors, asserting the BouncyCastle output matches the reference implementation byte-for-byte. The test guards against a BouncyCastle regression silently weakening the KDF.

## Rejected alternatives

### Key derivation alternatives

- **PBKDF2-HMAC-SHA256 (the current TBN1 KDF).** Rejected for new envelopes. PBKDF2 has no memory-hardness property; the per-iteration cost is uniformly low in memory, so an ASIC implementation gains roughly 10000x speedup over a CPU implementation. RFC 9106 § 7 names exactly this failure mode as the reason memory-hard KDFs were standardized. The 600k-iteration PBKDF2 baseline that TBN1 uses is OWASP-compliant for password storage in a server-side database against a casual adversary; it is below the project's nation-state-grade threat-model bar.
- **scrypt.** Rejected. scrypt is memory-hard and would technically meet the bar, but it predates Argon2id, was not standardized by an IETF working group (the closest reference is RFC 7914, which describes scrypt as informational rather than a recommended construction), and the post-2015 Password Hashing Competition consensus moved to Argon2id. Picking scrypt for a 2026 design would require justifying a step backward from the 2021 standard.
- **bcrypt.** Rejected. bcrypt has a fixed memory footprint of roughly 4 KiB, which is below the memory-hardness threshold that defends against GPU and ASIC attacks. It is appropriate for low-value password storage; not appropriate here.
- **HKDF (HMAC-based Key Derivation Function).** Rejected as a passphrase KDF. HKDF is a key-derivation function designed to expand a high-entropy key into multiple subkeys; it is not designed to slow down brute-force against a low-entropy passphrase. The two roles are different.

### Symmetric cipher alternatives

- **XChaCha20-Poly1305.** Rejected for this iteration. XChaCha20-Poly1305's 24-byte nonce eliminates random-nonce collision risk under a fixed key, which is a real win in some deployment models. In our deployment, the per-envelope unique key already closes the same failure mode, so the marginal benefit is small. The cost of switching is a new envelope shape, a new JCA gap to fill (Android's bundled JCA does not expose XChaCha20-Poly1305 at the API levels we support), and divergence from the existing TBN1 AES-256-GCM infrastructure. Revisit if and when an iOS source set lands and CryptoKit's first-class XChaCha20-Poly1305 support becomes the more natural fit.
- **AES-256-CBC plus HMAC-SHA256 (Encrypt-then-MAC).** Rejected. Encrypt-then-MAC is a valid AEAD construction but requires manual padding management and manual MAC verification, both of which are sites where implementation bugs have historically landed. The AEAD primitive (GCM in our case) bakes the MAC into the cipher's API surface and is harder to get wrong.

### Keying-without-passphrase alternatives

- **Device-keystore-only encryption (no passphrase).** Rejected for the same reason ADR-0016 rejected it: a backup that decrypts only on the originating device fails the device-loss-recovery use case, which is the primary backup use case.
- **Hardcoded application key.** Rejected. The key would live in the published APK and any user with the APK could derive it via standard reverse-engineering tools. ADR-0016 named this as theater encryption; the rejection stands.
- **No backup encryption at all (continue ADR-0016's plain JSON in perpetuity).** Rejected for a v2 trigger world. ADR-0016 already enumerated the v2 trigger conditions; this ADR does not relitigate them, only provides the design that fires when one materializes.

### Library alternatives

- **de.mkammerer:argon2-jvm (JNI binding to libargon2).** Rejected. The JNI bridge adds native-library shipping complexity per Android ABI and a non-zero crash-mode surface (JNI mismatches manifest as `UnsatisfiedLinkError` at runtime). The performance win is real (libargon2 in C is roughly 2x to 3x faster than BouncyCastle's pure-Java implementation) but lands on an operation that already runs off the main thread with a progress UI. The portability cost dominates the performance benefit.
- **Custom Argon2id implementation.** Rejected as forbidden under the project's anti-enterprise default. AGENTS.md § Posture rejects "build your own crypto" by inference (the boring-tech rule names mature, well-deployed libraries as the canonical choice). RFC 9106 has multiple subtle implementation pitfalls (the H' function, the indexing function, the lane synchronization); a from-scratch implementation would need formal review that is out of scope for this project.

## Followups

These artifacts are proposed by this ADR but NOT created here. Each one is sized for its own session.

- **D1 (the implementation session).** The ADR is the design record; D1 is the implementation. D1 produces:
    - A new file `shared/src/commonMain/kotlin/app/toebeans/core/backup/Argon2idKdf.kt` declaring `expect class Argon2idKdf` with derive(passphrase, salt) returning a 32-byte key.
    - `jvmMain` and `androidMain` actuals using `org.bouncycastle.crypto.generators.Argon2BytesGenerator` with the parameters fixed in this ADR.
    - A new `BackupCipherV2` (or a renamed `BackupCipher` if the V1 interface is migrated) using AES-256-GCM with AAD = magic || salt || iv, magic value `TBN2`.
    - The Gradle dependency addition `org.bouncycastle:bcprov-jdk18on:<latest-stable>` in `gradle/libs.versions.toml`. This is a vibe-dangerous change per AGENTS.md and requires a paired calibration entry.
    - The retired PBKDF2 path stays for read-only TBN1 decryption (the dormant cipher's test surface). The encrypt path of the V1 cipher is removed or marked deprecated.
    - SECURITY.md § Threat model line 27 is updated to point at this ADR (currently it claims AES-256-GCM with Argon2id without naming the deferral; the line becomes accurate once D1 lands).
- **Tests required by D1.**
    - `Argon2idKdfTest`: known-answer tests against RFC 9106 § 5 test vectors (the canonical interoperability check; if BouncyCastle ever regresses, this test catches it).
    - `BackupCipherV2Test`: round-trip with a sample passphrase; decrypt failure on tampered ciphertext; decrypt failure on tampered AAD (magic flip from TBN2 to TBN1 must fail authentication); decrypt failure on wrong passphrase. The wrong-passphrase failure must be indistinguishable from the tampered-ciphertext failure at the API boundary, per the existing `BackupDecryptException` rationale.
    - Performance budget test: derive-key time on the jvmTest reference hardware is below 8 seconds (an upper bound that catches an order-of-magnitude regression; the real-device measurement happens at M1.2 internal beta).
    - Memory-pressure test: low-memory environment surfaces a graceful error rather than an unchecked `OutOfMemoryError`.
- **Fitness function: no PBKDF2 in commonMain backup module after TBN2 lands.** Sketch only; the function reads every Kotlin file under `shared/src/commonMain/kotlin/app/toebeans/core/backup/` and fails if `PBKDF2` appears outside the TBN1 read-only decrypt path. The function lives alongside `scripts/test_no_pii_in_crash_log.sh` and follows the same shape. The function lands in D1 or in a follow-on session; this ADR does not author the script.
- **CONTRIBUTING.md pre-push gauntlet addendum.** Per ADR-0017 Lesson 1, the local gauntlet needs an explicit step that verifies the BouncyCastle dependency does not break the `compileTestKotlinJvm` stage. This is a sub-bullet under the existing pre-push ordering doc.
- **SECURITY.md edit (proposed for D1).** Line 27 currently states the encrypted-backup posture as if it ships in v1. After D1, the line points to ADR-0018 and ADR-0016 jointly: ADR-0016 for the v1 plain-JSON posture and trigger conditions, ADR-0018 for the encrypted-backup design that activates on trigger. The edit cannot land in this ADR's worktree because the worktree is scoped to `docs/adr/` per the vibe-careful protocol.

## Revisit conditions

This ADR moves to **Superseded** if any of the following:

- NIST formally deprecates Argon2id, or RFC 9106 is moved to historic status by a successor RFC. As of mid-2026, neither is on the IETF agenda; Argon2id won the 2015 Password Hashing Competition and the RFC was published in 2021. The probability of deprecation within the M6 horizon is low but not zero.
- The BouncyCastle `bcprov-jdk18on` artifact becomes unmaintained (no release in 18 months, security advisories unaddressed). The library has been actively maintained since 2000; the probability is low but the revisit cost is bounded (swap to an alternative pure-Java Argon2id implementation or accept the JNI cost).
- A KMP-native Argon2id implementation reaches production maturity. The expect/actual split would simplify to a commonMain-only implementation. The current commonMain expect/actual is not a problem; the simplification is a future cleanup with no forcing function attached.
- M1.2 internal beta user research surfaces a different threat model. For instance, if research finds users routinely share backups with veterinarians via insecure channels, the AAD strategy might extend to bind a recipient identifier; or if research finds users want passphrase-less encrypted backups, the Android Keystore option (rejected here for device-portability reasons) returns to the table under a different sharing model.
- The parameter floor moves. If RFC 9106 or OWASP's Password Storage Cheat Sheet raises the recommended minimum above the parameters in this ADR, a `TBN3` envelope with the new floor follows the same migration pattern this ADR establishes.

## References

- RFC 9106, Argon2 Memory-Hard Function for Password Hashing and Proof-of-Work Applications: https://datatracker.ietf.org/doc/html/rfc9106
- OWASP Password Storage Cheat Sheet (Argon2id section): https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- BouncyCastle Argon2BytesGenerator API documentation: https://www.bouncycastle.org/docs/docs1.8on/org/bouncycastle/crypto/generators/Argon2BytesGenerator.html
- ADR-0009 (`docs/adr/0009-local-crash-log-no-telemetry.md`). The local-only, no-telemetry framing the encrypted backup inherits.
- ADR-0016 (`docs/adr/0016-plain-json-backup-encryption-deferred.md`). The v1 plain-JSON posture and the v2 trigger conditions this ADR resolves the design for.
- ADR-0017 (`docs/adr/0017-lessons-from-adr-0016-ci-iteration.md`). The CI-iteration lessons relevant to D1's local-gauntlet handoff.
- ADR-0003 (`docs/adr/0003-local-first-no-cloud.md`). The local-first posture and the original (deferred) Manual export row.
- `docs/issues/v0.1-followups.md` item 7. The followup register entry naming Argon2id, the envelope-magic bump, and the backward-compat decrypt path; this ADR resolves the open design questions that entry left under-specified.
- `shared/src/commonMain/kotlin/app/toebeans/core/backup/BackupCipher.kt`. The dormant TBN1 cipher whose KDF rotates under this ADR; the AES-256-GCM cipher and envelope shape carry forward.
- SECURITY.md § Threat model. The v1 threat model this ADR upgrades to the nation-state adversary model for the encrypted-backup design.
