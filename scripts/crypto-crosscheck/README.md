# Crypto cross-check scripts

Independent-second-source reproducibility for the crypto vectors pinned in the toebeans backup-cipher tests.

## Purpose

The Kotlin tests for `Argon2idKdf` and `BackupCipherV2` pin specific output bytes as known-answer tests (KATs). Pinning gives byte-for-byte regression detection if BouncyCastle ever silently weakens its implementation. But pinning alone doesn't prove the bytes are *correct*, only that they're *stable*. The pinned bytes are correct because the original D1 session derived them against TWO unrelated reference implementations and confirmed agreement:

- **`argon2-cffi`**: Python wrapper around `phc-winner-argon2`, the C reference implementation referenced by RFC 9106. Used as the Argon2id source-of-truth.
- **`cryptography`** (pyca/cryptography): Python wrapper around OpenSSL's `EVP_aes_256_gcm`. Used as the AES-GCM source-of-truth.

Neither library is BouncyCastle. Agreement between BouncyCastle and these two libraries means the BouncyCastle path is cross-validated against two independent references.

This directory preserves the cross-check script so the verification is **reproducible**, not just **historically attested**. Run it after any BouncyCastle version bump, or when diagnosing a KAT-test failure.

## Scripts

### `argon2id_aes_gcm_kat.py`

Derives both pinned vectors:

1. **Argon2id KAT vector** (pinned in `@/Users/wjia/Projects/toebeans/shared/src/commonTest/kotlin/app/toebeans/core/backup/Argon2idKdfTest.kt:107-152`). 32-byte derived key from a fixed passphrase and salt under the ADR-0018 parameters.
2. **AAD-order AES-256-GCM vector** (pinned in `@/Users/wjia/Projects/toebeans/shared/src/jvmTest/kotlin/app/toebeans/core/backup/BackupCipherV2Test.kt:233-296`). 42-byte ciphertext+tag from a fixed passphrase, salt, IV, and plaintext, with AAD constructed as `magic || salt || iv`. Catches any AAD-construction-order regression in the cipher impl.

Exit code 0 = both vectors match the pinned expected outputs. Exit code 1 = divergence; the printed diff names which vector and which byte index diverged.

## Setup

```sh
python3 -m pip install argon2-cffi cryptography
```

### Apple Silicon note

The argon2-cffi wheel ships for both x86_64 and arm64. If the script fails with `ImportError` on `argon2.low_level`, the Python interpreter may be running under Rosetta. Force arm64:

```sh
arch -arm64 python3 -m pip install argon2-cffi cryptography
arch -arm64 python3 scripts/crypto-crosscheck/argon2id_aes_gcm_kat.py
```

## Running

```sh
python3 scripts/crypto-crosscheck/argon2id_aes_gcm_kat.py
```

Expected output on the post-D1 main branch:

```
ADR-0018 cross-check: Argon2id KAT + AES-256-GCM AAD-order vector
Argon2id params: m=65536 KiB, t=3, p=1, hashlen=32, version=0x13, type=Argon2id

Vector 1: Argon2id KAT (Argon2idKdfTest.kt)
  PASS  KAT derived key matches pinned expected output (32 bytes)

Vector 2: AAD-order AES-256-GCM (BackupCipherV2Test.kt)
  PASS  ciphertext+tag matches pinned expected output (42 bytes)

All vectors match. BouncyCastle path is cross-validated against argon2-cffi + cryptography.
```

Runtime is approximately 10-30 seconds, dominated by the two Argon2id derivations (m=64 MiB each).

## When BouncyCastle and the cross-check disagree

If the Kotlin KAT tests pass but this script disagrees with the pinned bytes, that means BouncyCastle and the C reference (and OpenSSL) have diverged. Possible causes, in order of likelihood:

1. The `argon2-cffi` or `cryptography` wheel is broken in your local environment. Reinstall.
2. The `arm64` vs `x86_64` mismatch above. Try `arch -arm64`.
3. BouncyCastle has regressed. Pin the affected BouncyCastle version in `gradle/libs.versions.toml`; surface as a GH issue against `org.bouncycastle/bcprov-jdk18on`.
4. The pinned bytes in the Kotlin tests are wrong (very unlikely; they were cross-checked at D1 step 3 against this exact script's outputs).

If the Kotlin tests fail but this script passes, BouncyCastle has regressed; the pinned bytes are still the correct values per the C reference.

## Provenance

- D1 session log records the original cross-check at `@/Users/wjia/Projects/toebeans/localonly/session-logs/2026-05-19-final-review-d1-batch.md`.
- ADR-0018 § Decision § Cross-check protocol (the section that specifies the two-reference requirement) is at `@/Users/wjia/Projects/toebeans/docs/adr/0018-argon2id-backup-cipher-design.md`.
- The cross-check was originally a `/tmp` script in the D1 dispatch session; it was preserved here in 2026-05-19 as a W1 follow-on per the D1 final-review report.

## Maintenance

When ADR-0018 is superseded by a new envelope (TBN3, etc.), this script's parameter constants update first. Re-run on a clean Python venv to confirm the new vectors before pinning them in the Kotlin tests.
