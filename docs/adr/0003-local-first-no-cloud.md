# ADR-0003: Local-first storage. No cloud at v1. User-configurable backup.

Date: 2026-05-14
Status: Accepted
Deciders: Wei Jia (with Cascade)

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
| **Manual encrypted export** | enabled | User taps "Export" in Settings; app prompts for a passphrase; writes an encrypted JSON file (AES-256-GCM with Argon2id key derivation) to a user-chosen Files location. Restore is "Import" + passphrase. |
| **Android Auto Backup** | disabled | User opts in via Settings with a clear disclosure that data will be backed up to their Google account. App enables `android:allowBackup="true"` programmatically via a settings-driven manifest extension. (At v0.1 the manifest sets `allowBackup="false"`; enabling it is gated.) |
| **Cloud sync** | not in v1 | Reserved for the paid Plus tier. Not built. |

The passphrase is **never** persisted. The user must remember it to restore. This is intentional and surfaced in the UX as a "write it down" prompt.

## Consequences

### Positive

- Zero server cost in v1.
- No GDPR/CCPA scope beyond "we collect nothing" (which is the simplest scope to maintain).
- Compelling privacy story for marketing.
- Compliance posture in the dossier is preserved (HIPAA does not apply; state privacy laws are about clinic-side data and we hold none of it).

### Negative

- Users who lose their device AND forget their passphrase lose their data. Mitigation: encourage Android Auto Backup opt-in; surface a "verify your backup" prompt monthly.
- Manual export is a friction point. We accept this; the UX cost of a passphrase prompt is the price of the trust posture.

### Rejected alternatives

- **Cloud sync from day 1 (Firebase / Supabase).** Rejected: adds auth, a server cost center, GDPR/CCPA scope, and undermines the moat. Deferred to slice 6 (Plus tier).
- **Android Auto Backup default-on (silent).** Rejected: users do not realize their pet data is being uploaded to Google. We require explicit opt-in.
- **Plaintext export.** Rejected: backup files often end up in cloud drives, on USB sticks, attached to emails. Encryption-at-rest for exports is non-negotiable.

## Verification

- Fitness function `scripts/test_no_network.sh` fails if `okhttp3`, `ktor`, `URLConnection`, `URL(...)`, etc. appear in `commonMain/` or `androidMain/`.
- Backup codec lives in `shared/.../backup/` (vibe-dangerous; requires human-written tests).
- `android:allowBackup="false"` is the v0.1 manifest default; enabling it programmatically is a slice-1 work item with its own PR and ADR.

## References

- Internal: `research/00-feasibility-dossier.md` §3.3 (trust as a moat) and §4.5 (regulatory posture).
- OWASP Mobile Security Top 10 — M9: Insecure Data Storage.
