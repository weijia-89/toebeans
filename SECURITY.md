# Security policy: toebeans

## Reporting

Please report security vulnerabilities **privately**. Do not open a public issue.

## In scope

- Anything in this repository.
- Behavior of the published Android app.

## Out of scope

- Social engineering of toebeans contributors.
- Physical attacks on a user's device.
- Issues that require a rooted/jailbroken device.
- Anything in third-party dependencies that has an existing CVE (please report upstream first, then to us if our usage amplifies the impact).

## Threat model (slice 1, v0.1)

See the threat model table in [`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md`](docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md#12-threat-model-stride-code-helper-613) §12. Highlights:

- **No PII collected.** No user accounts. No analytics. No network calls.
- **Backup encryption.** Manual backup export ships plain JSON in v1 per [ADR-0016](docs/adr/0016-plain-json-backup-encryption-deferred.md), which records the threat-model rationale for deferring encryption and names the four trigger conditions that reactivate the encrypted posture. The cipher design that activates on trigger is recorded in [ADR-0018](docs/adr/0018-argon2id-backup-cipher-design.md). The design uses AES-256-GCM with a 128-bit authentication tag plus a passphrase-derived key via Argon2id, a memory-hard key derivation function (KDF) standardized in RFC 9106. The passphrase is **never** persisted.
- **Android Auto Backup.** Encryption is provided by the Android platform's per-user device-bound key. If a user enables this feature, their data lives in their own Google account backup quota; toebeans never sees it.
- **Permissions.** AndroidManifest is restricted to the explicit allowlist in [`AGENTS.md`](AGENTS.md). Adding any permission requires an ADR and human review.

## Supply chain posture

- SLSA Build Track L1 at v0.1; L2 target by slice 6.
- All Gradle dependencies pinned to specific versions in `gradle/libs.versions.toml`.
- Pull request CI runs `actions/dependency-review-action` and fails the job when a newly added dependency has a high-severity advisory (`.github/workflows/ci.yml`, job `dependency-review`).
- Hallucinated-package vigilance per [`AGENTS.md` § Hallucination vigilance](AGENTS.md#hallucination-vigilance). Every new import requires explicit human verification against Maven Central.

### Buildscript-transitive vs. app-runtime dependencies

The supply-chain surface has two classes, and toebeans treats them very
differently.

**App-runtime classpath.** Anything that ends up in
`:shared:jvmRuntimeClasspath` or `:androidApp:releaseRuntimeClasspath` ships
to user devices, so a CVE here maps directly to user risk. The pinned
versions in `gradle/libs.versions.toml` define this set, and a human review
per `AGENTS.md` precedes any change.

**Buildscript / plugin classpath.** Anything pulled in by the Android Gradle
Plugin, the Kotlin Gradle Plugin, or other build-only plugins runs on the
developer machine and the GitHub Actions runner during a build. None of
these classes are packaged into the APK. The transitives that AGP pulls in
(including the grpc-netty and bouncycastle stacks) live entirely in this
classpath. A CVE on any of them is a developer-environment supply-chain
concern, and end users on the published APK are not exposed.

A buildscript jar is not in the published APK. When the resolved version is already outside the advisory range, leave it. When the resolved version is inside the range, pin it in the existing buildscript `resolutionStrategy.force` list in `build.gradle.kts`. A force that breaks the build is reverted. An AGP or Kotlin bump stays on its own schedule.

Triage decisions are logged in
`docs/security/dependabot-triage-<YYYY-MM-DD>.md` with the alert IDs, the
classpath each package lives in, and the dismissal rationale per package.
The first such log is
[`docs/security/dependabot-triage-2026-05-18.md`](docs/security/dependabot-triage-2026-05-18.md).

### Dependabot pull requests

`.github/dependabot.yml` checks Gradle and GitHub Actions once a week. A version-update pull request waits until the release is 30 days old. That wait matches the first-seen rule in [`AGENTS.md`](AGENTS.md).

Security update pull requests are already enabled on the GitHub repository. The 30-day wait does not apply to them.

Dependabot does not merge its own pull requests. A Gradle version change still needs human review per [`AGENTS.md`](AGENTS.md).

This config file does not close alerts. An `ignore` rule would only stop version-update pull requests. The 2026-05-18 triage recorded that limit, and it still holds. The open-alert work list is [`docs/superpowers/plans/2026-11-08-dependabot-alerts.md`](docs/superpowers/plans/2026-11-08-dependabot-alerts.md).

## Disclosure

We follow [coordinated disclosure](https://www.cisa.gov/coordinated-vulnerability-disclosure-process):

- Acknowledge → Triage → Fix → Pre-release notification (if you reported) → Public disclosure (≥90 days after report unless you agree sooner).
- We will credit reporters by handle of their choosing unless they prefer anonymity.

## Things we will NEVER ask for

- Your veterinarian's credentials.
- Your insurance policy number.
- Your pet's microchip number for cross-database lookup.

If any communication from "toebeans" requests these, treat it as a phishing attempt.
