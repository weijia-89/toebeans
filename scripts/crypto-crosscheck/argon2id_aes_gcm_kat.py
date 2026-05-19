"""Cross-check the Argon2id KAT and the AAD-order AES-256-GCM vector for ADR-0018.

This script derives the two cross-check vectors pinned in the D1 backup-cipher
tests against TWO INDEPENDENT crypto libraries:

  * `argon2-cffi` (binds to the official phc-winner-argon2 C reference impl
    per RFC 9106); this is the Argon2id reference of record.
  * `cryptography` (binds to OpenSSL's `EVP_aes_256_gcm`); this is the
    AES-GCM reference.

Neither library is BouncyCastle. So if this script reproduces the bytes
pinned in the Kotlin tests, the BouncyCastle path is cross-validated against
two unrelated reference implementations.

USE CASES
---------

* Re-verify the pinned KAT bytes after a BouncyCastle version bump.
* Diagnose a fitness-function or KAT-test failure: is the regression in
  BouncyCastle, or were the original bytes wrong?
* Reproduce the vectors from scratch with different parameters when ADR-0018
  is superseded by a TBN3 envelope.

REQUIREMENTS
------------

  $ python3 -m pip install argon2-cffi cryptography

On Apple Silicon, ensure the arm64 wheel is used. If the script fails with
`ImportError` on `argon2.low_level`, your interpreter may be x86_64 under
Rosetta; re-run as `arch -arm64 python3 argon2id_aes_gcm_kat.py`.

USAGE
-----

  $ python3 scripts/crypto-crosscheck/argon2id_aes_gcm_kat.py

Exit code 0 = both vectors match the pinned expected outputs. Exit code 1 =
divergence; the printed diff names which vector and which bytes.

VECTOR SOURCES
--------------

KAT vector (Argon2idKdfTest.kt `known-answer test matches the cross-checked reference`):
  inputs at @/Users/wjia/Projects/toebeans/shared/src/commonTest/kotlin/app/toebeans/core/backup/Argon2idKdfTest.kt:107-108
  expected output at the same file, lines 118-152.

AAD-order vector (BackupCipherV2Test.kt `synthetic AAD-order vector pins magic-salt-iv construction`):
  inputs at @/Users/wjia/Projects/toebeans/shared/src/jvmTest/kotlin/app/toebeans/core/backup/BackupCipherV2Test.kt:233-236
  expected output at the same file, lines 253-296.
"""

import sys

# Argon2id parameters per ADR-0018 § Decision § Parameter set, pinned in
# @/Users/wjia/Projects/toebeans/shared/src/commonMain/kotlin/app/toebeans/core/backup/Argon2idKdf.kt:12-26
ARGON2ID_MEMORY_COST_KIB = 65_536   # 64 MiB
ARGON2ID_ITERATIONS = 3              # three passes
ARGON2ID_PARALLELISM = 1             # one lane
ARGON2ID_OUTPUT_KEY_BYTES = 32       # 256-bit derived key
ARGON2ID_VERSION = 0x13              # RFC 9106

# BackupCipherV2 envelope constants per ADR-0018 § Decision § Envelope layout
MAGIC = b"TBN2"
GCM_TAG_BITS = 128


# --- Vector 1: Argon2id KAT ---------------------------------------------------

KAT_PASSPHRASE = "argon2id-kat-passphrase".encode("ascii")
# Mirrors `"argon2id-kat-salt-16b".toByteArray(Charsets.US_ASCII).copyOf(16)`:
# the Kotlin source string is 21 ASCII characters; `.copyOf(16)` truncates to
# the first 16, yielding `argon2id-kat-sal`.
KAT_SALT = "argon2id-kat-salt-16b".encode("ascii")[:16]

KAT_EXPECTED = bytes(
    [
        0xB8, 0xD6, 0x6E, 0xD9, 0x90, 0x07, 0x1D, 0x40,
        0xF5, 0x7E, 0xF8, 0x8C, 0x12, 0x1F, 0x2F, 0xB0,
        0x8E, 0xB3, 0x1E, 0xBD, 0x58, 0xC2, 0xA3, 0xED,
        0x02, 0x71, 0xE2, 0x28, 0xD1, 0x69, 0x13, 0xB6,
    ]
)


# --- Vector 2: AAD-order AES-256-GCM ------------------------------------------

AAD_PASSPHRASE = "aad-order-vector-passphrase".encode("ascii")
AAD_SALT = "aad-order-vec-sa".encode("ascii")        # 16 bytes
AAD_IV = "aad-order-iv".encode("ascii")              # 12 bytes
AAD_PLAINTEXT = "aad-order-vector-plaintext".encode("ascii")  # 26 bytes

AAD_EXPECTED_CIPHERTEXT_AND_TAG = bytes(
    [
        0x8E, 0x84, 0xF9, 0xB7, 0xC0, 0xC4, 0xC1, 0xE9,
        0x2C, 0xE9, 0x11, 0x87, 0x6A, 0xED, 0xD7, 0x89,
        0xB4, 0xDA, 0x61, 0x14, 0x79, 0x8D, 0x16, 0x1C,
        0xE1, 0xB4, 0x93, 0xE2, 0xE7, 0x24, 0x3F, 0xCC,
        0xC6, 0x97, 0x98, 0x42, 0x3F, 0x83, 0x6F, 0x17,
        0xFF, 0x30,
    ]
)


def derive_argon2id(passphrase: bytes, salt: bytes) -> bytes:
    """Derive a 32-byte Argon2id key against the ADR-0018 parameters."""
    from argon2.low_level import hash_secret_raw, Type

    return hash_secret_raw(
        secret=passphrase,
        salt=salt,
        time_cost=ARGON2ID_ITERATIONS,
        memory_cost=ARGON2ID_MEMORY_COST_KIB,
        parallelism=ARGON2ID_PARALLELISM,
        hash_len=ARGON2ID_OUTPUT_KEY_BYTES,
        type=Type.ID,
        version=ARGON2ID_VERSION,
    )


def aes_256_gcm_encrypt(key: bytes, iv: bytes, plaintext: bytes, aad: bytes) -> bytes:
    """Encrypt under AES-256-GCM and return ciphertext concatenated with the 128-bit tag."""
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    aead = AESGCM(key)
    # `AESGCM.encrypt` returns ciphertext || tag. Tag length is 128 bits / 16 bytes by default.
    return aead.encrypt(iv, plaintext, aad)


def hexdump(label: str, data: bytes) -> str:
    return f"{label} ({len(data)} bytes): " + data.hex()


def check(label: str, actual: bytes, expected: bytes) -> bool:
    if actual == expected:
        print(f"  PASS  {label} matches pinned expected output ({len(actual)} bytes)")
        return True
    print(f"  FAIL  {label} diverges from pinned expected output")
    print("        " + hexdump("actual  ", actual))
    print("        " + hexdump("expected", expected))
    for i, (a, e) in enumerate(zip(actual, expected)):
        if a != e:
            print(f"        first diverging byte at index {i}: actual=0x{a:02x} expected=0x{e:02x}")
            break
    if len(actual) != len(expected):
        print(f"        length mismatch: actual={len(actual)} expected={len(expected)}")
    return False


def main() -> int:
    print("ADR-0018 cross-check: Argon2id KAT + AES-256-GCM AAD-order vector")
    print(f"Argon2id params: m={ARGON2ID_MEMORY_COST_KIB} KiB, t={ARGON2ID_ITERATIONS}, "
          f"p={ARGON2ID_PARALLELISM}, hashlen={ARGON2ID_OUTPUT_KEY_BYTES}, "
          f"version=0x{ARGON2ID_VERSION:02x}, type=Argon2id")
    print()

    print("Vector 1: Argon2id KAT (Argon2idKdfTest.kt)")
    kat_actual = derive_argon2id(KAT_PASSPHRASE, KAT_SALT)
    kat_ok = check("KAT derived key", kat_actual, KAT_EXPECTED)
    print()

    print("Vector 2: AAD-order AES-256-GCM (BackupCipherV2Test.kt)")
    aad_key = derive_argon2id(AAD_PASSPHRASE, AAD_SALT)
    aad_bytes = MAGIC + AAD_SALT + AAD_IV  # 32 bytes total
    assert len(aad_bytes) == 32, f"AAD must be 32 bytes; got {len(aad_bytes)}"
    aad_actual = aes_256_gcm_encrypt(aad_key, AAD_IV, AAD_PLAINTEXT, aad_bytes)
    aad_ok = check("ciphertext+tag", aad_actual, AAD_EXPECTED_CIPHERTEXT_AND_TAG)
    print()

    if kat_ok and aad_ok:
        print("All vectors match. BouncyCastle path is cross-validated against argon2-cffi + cryptography.")
        return 0
    print("Cross-check FAILED. Investigate the diverging vector above before trusting the BouncyCastle output.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
