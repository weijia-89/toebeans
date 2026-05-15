# Security policy — toebeans

## Reporting

Please report security vulnerabilities **privately**. Do not open a public issue.

- Email: `security@toebeans.local` *(placeholder; replace before publishing the repo)*
- We will acknowledge within 5 business days and triage within 10.

## In scope

- Anything in this repository.
- Behavior of the published Android app.

## Out of scope

- Social engineering of toebeans contributors.
- Physical attacks on a user's device.
- Issues that require a rooted/jailbroken device.
- Anything in third-party dependencies that has an existing CVE (please report upstream first, then to us if our usage amplifies the impact).

## Threat model (slice 1 — v0.1)

See the threat model table in [`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md`](docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md#12-threat-model-stride-code-helper-613) §12. Highlights:

- **No PII collected.** No user accounts. No analytics. No network calls.
- **Backup encryption.** Manual export is encrypted with AES-256-GCM, key derived from a user passphrase via Argon2id. The passphrase is **never** persisted.
- **Android Auto Backup.** Encryption is provided by the Android platform's per-user device-bound key. If a user enables this feature, their data lives in their own Google account backup quota; toebeans never sees it.
- **Permissions.** AndroidManifest is restricted to the explicit allowlist in [`AGENTS.md`](AGENTS.md). Adding any permission requires an ADR and human review.

## Supply chain posture

- SLSA Build Track L1 at v0.1; L2 target by slice 6.
- All Gradle dependencies pinned to specific versions in `gradle/libs.versions.toml`.
- `./gradlew dependencyCheckAnalyze` (OWASP) gate in CI.
- Hallucinated-package vigilance per [`AGENTS.md` § Hallucination vigilance](AGENTS.md#hallucination-vigilance). Every new import requires explicit human verification against Maven Central.

## Disclosure

We follow [coordinated disclosure](https://www.cisa.gov/coordinated-vulnerability-disclosure-process):

- Acknowledge → Triage → Fix → Pre-release notification (if you reported) → Public disclosure (≥90 days after report unless you agree sooner).
- We will credit reporters by handle of their choosing unless they prefer anonymity.

## Things we will NEVER ask for

- Your veterinarian's credentials.
- Your insurance policy number.
- Your pet's microchip number for cross-database lookup.

If any communication from "toebeans" requests these, treat it as a phishing attempt.
