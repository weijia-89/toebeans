# ADR-0016: v1 backup export ships plain JSON. Encryption deferred until PII surfaces.

Date: 2026-05-17
Status: Accepted
Deciders: Wei Jia (with Cascade; coached-override on import dedupe semantic logged in `.codeit/calibration.jsonl`)
Supersedes: the encryption posture in ADR-0003 §Backup row 1 (see Amendment in ADR-0003 dated 2026-05-17).

## Context

ADR-0003 originally specified "AES-256-GCM with Argon2id key derivation from a user-entered passphrase" for manual backup export. The codec landed in `shared/.../backup/` with PBKDF2-HMAC-SHA256 (Argon2id is not available on KMP without a platform-specific dependency; the codec KDoc names this as a v2 follow-up). The codec has 17 passing tests (`BackupCipherTest` 11 + `BackupSerializerTest` 6) and is gated to 85% line coverage by Kover.

At the moment of wiring the codec to a UI surface (the Settings → Export data button, disabled since v0.1), the threat model was reassessed:

1. **What is the asset?** Pet name, pet species/breed, medication names and dosages, schedule cadence, dose-event history. No owner PII (no email, phone, address, billing, payment methods). No clinical-grade identifiers.
2. **Who is the adversary?** No identifiable targeted adversary. Pet-medication-schedule data has low resale value to ad networks (it doesn't reveal human health, just animal-care patterns) and zero value to credential thieves. The only plausible adversary is a bulk-scraper of cloud storage who indiscriminately collects files; the mitigation against bulk scraping is "do not upload to insecure cloud storage," which is a user-behavior question, not a file-format question.
3. **What does encryption-without-passphrase actually buy?** Three real options exist for keyless encryption, all of which fail the brief:
   - **Hardcoded key in app source.** Bytes look scrambled but anyone with the public APK can decrypt in under a minute via standard reverse-engineering. This is theater, not protection.
   - **Device-bound key via Android Keystore.** Real encryption, but the backup is unreadable on any other device, defeating the primary backup use case (device-loss recovery on a new phone).
   - **Passphrase entry.** Real encryption, real portability, but ~1s PBKDF2 wait per export, a two-field confirm-passphrase UI, an irrecoverability warning, and a "remember this exactly" cognitive load on the user. Justified for high-value secrets; disproportionate for pet medication schedules.
4. **What is the cost of encryption today?** ~1s PBKDF2 spinner per export, ~3s for a 1000-event archive (see ADR-0008 perf-budget row). Passphrase UI complexity. The user has to track a passphrase they will use rarely. ADR-0003 mitigation ("monthly verify-your-backup prompt") was itself a friction point.
5. **What is the cost of plain export today?** None. The user opens the file in any text editor or `jq` to verify contents, which is an unexpectedly nice product property.

## Decision

**v1 backup export ships plain JSON.** No encryption. No passphrase. No KDF wait.

- File extension: `.json`
- MIME type: `application/json`
- Filename default: `toebeans-backup-MMDDYYYY-HHMM.json`
- Wire format: the existing `BackupExport` data class (`schemaVersion`, `exportedAt`, `appVersion`, `pets`, `medications`, `schedules`, `schedulePhases`, `doseEvents`) serialized by the existing `BackupSerializer` (already 6 tests green).
- Output channel: Storage Access Framework (`ACTION_CREATE_DOCUMENT`), user picks destination.

**Existing `BackupCipher` infrastructure (PBKDF2 + AES-256-GCM, 11 tests) stays in the codebase as dormant infrastructure.** It is not wired into Koin. It is not called by any production code. It remains tested and Kover-gated so it does not bit-rot. When the v2 trigger fires (see below), the cipher is ready to wire.

**v2 trigger conditions** (any one activates the encrypted backup posture):

- Owner PII is added to the data model (e.g. owner contact info for emergency-contact sharing, billing/payment data, vet account credentials).
- Sharing a backup with a third party (e.g. caregiver-share via QR per ROADMAP M2) becomes a supported flow; passing PII-or-not through such a flow requires encryption at rest because the channel is no longer "user's own device."
- Cloud sync ships (ROADMAP M6); end-to-end encryption is already a hard gate per Wei's anti-surveillance global rule and uses a different mechanism (per-device key derivation), but the backup envelope may piggyback on the same KDF infrastructure.
- A user explicitly requests encrypted backup (signal that the threat model has shifted in the field).

## Consequences

### Positive

- Honest. The file is what the user thinks it is. No security theater.
- Universal format. Any text editor, `jq`, `cat`, or `less` opens it. A vet who receives a backup can read it without installing anything.
- Simpler implementation. ~3-4h end-to-end vs ~5-7h with the passphrase UI. No PBKDF2 spinner, no key handling, no irrecoverability warnings, no length-validation policy decisions.
- Faster tests. The 11 `BackupCipherTest` cases pay 600k PBKDF2 iterations (mitigated by test-only iteration override, but still nonzero); aggregator tests will pay zero.
- The dormant codec is a real option for v2. The 17 tests gate its correctness. We have not thrown work away.

### Negative

- The backup file is human-readable. If a user emails it to themselves and the email account is breached, the contents are visible. Mitigation: the Settings copy explicitly says "your data lives only on this device" and the file format is what it appears to be.
- Future product evolution (PII addition) requires a backup-format migration. Mitigation: the existing `BackupExport.schemaVersion` field plus the `BackupCipher` envelope's `TBN1`/future-`TBN2` magic prefix give us a clean migration path: v2 backups are `.tbn2` files; v1 plain `.json` remains readable forever; an import can dispatch on the magic-bytes prefix.
- ADR-0003 stated a stronger posture. This ADR explicitly walks that back; the amendment in ADR-0003 records the reversal.

### Rejected alternatives

- **Theater encryption with hardcoded app key.** Rejected: gives the user a false sense of security. If we later add PII, users with old "encrypted" backups may believe their data is protected when the key is in the APK they uninstalled. Iron rule: do not call something encrypted if the key is shipped in the binary.
- **Device-bound encryption via Android Keystore.** Rejected: defeats the primary backup use case (device-loss recovery on a new device).
- **Passphrase-protected encryption (the ADR-0003 original).** Deferred: the friction cost is real, the protection benefit is not justified by the current asset value. Re-evaluate at v2 trigger.

## Verification

- `scripts/test_no_network.sh` still applies, since the backup file is local-only; no upload path exists in v1.
- `BackupCipherTest` and `BackupSerializerTest` stay green under Kover (the dormant cipher must not bit-rot).
- The Settings → Export data button MUST do real work (no Toast-stub regressions per ADR-0003 line 45-47 reasoning).
- The new `BackupAggregatorTest` covers the round-trip fixture: fake-repo state → `BackupAggregator.collect()` → `BackupSerializer.encodeToString` → `decodeFromString` → field equality with the source state.
- Import dedupe semantic is merge-by-id (insert new + skip existing) per coached override; the post-import success toast names this semantic so it is discoverable to users.

## References

- ADR-0003 (Local-first storage. No cloud at v1.): amended to point to this ADR for the actual v1 posture.
- ADR-0008 (Performance class target): backup export 1000-event perf row no longer "PBKDF2 dominates"; remains as a budget for the JSON serialization + I/O.
- ADR-0009 (Local crash-log capture; no telemetry): sibling decision in the same trust-posture vein.
- `shared/src/commonMain/kotlin/app/toebeans/core/backup/BackupCipher.kt`: the dormant cipher.
- `shared/src/commonMain/kotlin/app/toebeans/core/backup/BackupExport.kt`: the wire-format data class (unchanged by this ADR).
- `.codeit/calibration.jsonl`: calibration log entry at the implementing commit; coached-override entry for the merge-by-id dedupe choice.
