# ADR-0003: Local-first storage. No cloud at v1. User-configurable backup.

Date: 2026-05-14
Status: Accepted
Deciders: Wei Jia (with Cascade)
Amended: 2026-05-17 — manual backup posture revised to plain JSON for v1. See ADR-0016 and the inline Amendment note below.

## Context

The product plan and the feasibility dossier argue that the trust posture — "your data stays on your device" — is a moat against cloud-default competitors like PetDesk and Great Pet Care. The competitive analysis (`research/00-feasibility-dossier.md` §3.3) identifies trust as one of five defensible moats.

Two questions:

1. Where does data live?
2. How does data survive app reinstall (success criterion 3)?

## Decision

### Storage

All data lives in an app-private SQLite database (`ToebeansDatabase.db`) managed by SQLDelight. The app makes **zero** network calls at v1. A fitness function in CI fails the build if any network library appears in the dependency graph.

### Backup

The user chooses from a Settings panel. Default: manual export.

| Mode | Default | What it is |
|---|---|---|
| **Manual export** | enabled | User taps "Export data" in Settings; app writes a plain JSON file to a user-chosen Files location via Storage Access Framework. Restore is "Import data" + file picker. **See ADR-0016 for the v1 plain-JSON posture and the v2 trigger conditions that activate the dormant encryption codec.** |
| **Android Auto Backup** | disabled | User opts in via Settings with a clear disclosure that data will be backed up to their Google account. App enables `android:allowBackup="true"` programmatically via a settings-driven manifest extension. (At v0.1 the manifest sets `allowBackup="false"`; enabling it is gated.) |
| **Cloud sync** | not in v1 | Reserved for the paid Plus tier. Not built. |

### Amendment (2026-05-17)

The original Manual export row said "encrypted JSON file (AES-256-GCM with Argon2id key derivation) to a user-chosen Files location. Restore is 'Import' + passphrase." That posture is deferred. ADR-0016 supersedes it for v1.

Summary of the change:

- v1 ships **plain JSON** export. No passphrase. No KDF wait. Universal `.json` extension.
- The `BackupCipher` codec (PBKDF2 + AES-256-GCM, 11 tests) **stays in the codebase as dormant infrastructure** for v2. Not wired into Koin; not called by production code; Kover-gated to prevent bit-rot.
- v2 trigger conditions (any one activates the encrypted backup posture): owner PII added to the data model; backup-sharing-with-third-parties shipped (ROADMAP M2 caregiver-share); cloud sync (ROADMAP M6); explicit user request.
- Threat-model rationale: pet-medication-schedule data has low resale value, no targeted adversary, and the cost of passphrase friction is disproportionate to the protection benefit at v1's asset value. See ADR-0016 §Context for the full reasoning.
- Import semantic: **merge-by-id (insert new + skip existing)** per coached-override in `.codeit/calibration.jsonl`. The post-import success toast names the semantic so users discover it post-hoc.

The original posture (encrypted + passphrase) is recoverable verbatim from the dormant `BackupCipher` infrastructure if/when the v2 trigger fires.

## Consequences

### Positive

- Zero server cost in v1.
- No GDPR/CCPA scope beyond "we collect nothing" (which is the simplest scope to maintain).
- Compelling privacy story for marketing.
- Compliance posture in the dossier is preserved (HIPAA does not apply; state privacy laws are about clinic-side data and we hold none of it).

### Negative

- ~~Users who lose their device AND forget their passphrase lose their data.~~ (Superseded by ADR-0016: v1 has no passphrase. The remaining device-loss risk is the user not exporting in the first place; mitigation is encourage Android Auto Backup opt-in plus a future "verify your backup" prompt.)
- ~~Manual export is a friction point. We accept this; the UX cost of a passphrase prompt is the price of the trust posture.~~ (Superseded by ADR-0016: v1 export is a single tap + SAF file picker. The friction is the file-picker step itself, which is unavoidable for user-owned file output.)

### Rejected alternatives

- **Cloud sync from day 1 (Firebase / Supabase).** Rejected: adds auth, a server cost center, GDPR/CCPA scope, and undermines the moat. Deferred to milestone 6 (Plus tier).
- **Android Auto Backup default-on (silent).** Rejected: users do not realize their pet data is being uploaded to Google. We require explicit opt-in.
- **Plaintext export.** ~~Rejected: backup files often end up in cloud drives, on USB sticks, attached to emails. Encryption-at-rest for exports is non-negotiable.~~ (Reversed by ADR-0016 on 2026-05-17. Threat-model reassessment found pet-medication-schedule data does not warrant encryption-at-rest at v1's asset value, and the keyless-encryption options all fail the brief — theater, non-portable, or not actually keyless. Accepted v2 trigger conditions reactivate the encrypted posture; see ADR-0016 §Decision.)

## Verification

- Fitness function `scripts/test_no_network.sh` fails if `okhttp3`, `ktor`, `URLConnection`, `URL(...)`, etc. appear in `commonMain/` or `androidMain/`.
- Backup codec lives in `shared/.../backup/` (vibe-dangerous; requires human-written tests).
- `android:allowBackup="false"` is the v0.1 manifest default; enabling it programmatically is a milestone-1 work item with its own PR and ADR.

## References

- Internal: `research/00-feasibility-dossier.md` §3.3 (trust as a moat) and §4.5 (regulatory posture).
- OWASP Mobile Security Top 10 — M9: Insecure Data Storage.
