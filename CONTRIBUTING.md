# Contributing to toebeans

toebeans is a pet medication tracker for Android. It runs locally and never talks to the network. The design refuses to act like a vet. The canonical contributor reference is [AGENTS.md](AGENTS.md); read it before opening a PR. This file is the wayfinding surface for new contributors, and AGENTS.md is the rulebook.

## Pre-push gauntlet ordering

The local pre-push gauntlet **must** run lint and compile-tests as independent steps when a JDK is available locally. The reason is mechanical: in CI, the Lint stage short-circuits the Gradle compile step. A push that fails at the Lint stage produces no information about whether compile-tests would pass. If a contributor sees lint fail in CI, fixes the lint findings, and pushes again, any latent compile errors stay invisible until the lint stage finally passes. [ADR-0017](docs/adr/0017-lessons-from-adr-0016-ci-iteration.md) records the four-iteration cleanup that this lesson came from.

The order to run locally:

1. `./gradlew ktlintCheck`
2. `./gradlew detekt`
3. `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest`
4. `./gradlew koverVerify`
5. `./gradlew :androidApp:assembleDebug`

Run all five even if step 1 or 2 reports failures. The local gauntlet's value is the full picture; fixing lint findings in isolation can mask compile errors that only surface once lint passes. The CI workflow runs the same checks in the same order, but its parallelism budget short-circuits later steps on early-stage failure. The local environment has no such budget, so each step can run independently.

Order matters even on a clean repo. A contributor who fixes a lint issue and pushes without running the compile-tests step is making the same inference that ADR-0017 § Lesson 1 calls out: that lint passing implies the rest of the gauntlet would pass. That inference is unsafe whenever the diff touches a surface the local environment has not actually compiled.

Until JDK 17 is available locally, CI is the only compile-tests gate. A push that fails at the Lint stage in CI makes no claim about whether the compile-tests would have passed.

The local gauntlet is the per-contributor mitigation, not the whole fix. The CI workflow itself still runs Lint before Gradle compile and short-circuits the latter on the former's failure, so the same inference problem reappears for any contributor without local JDK 17. Reworking `.github/workflows/ci.yml` to run lint and compile-tests as independent gates is tracked in [ADR-0017 § Followups](docs/adr/0017-lessons-from-adr-0016-ci-iteration.md#followups) as a separate, deferred work item.

### When a Gradle dependency changes

Whenever a commit adds, upgrades, or removes a Gradle dependency (any line in `gradle/libs.versions.toml` or any `implementation`/`api`/`testImplementation`/`runtimeOnly` declaration in a module's `build.gradle.kts`), run a dependency-refreshing compile step before the regular gauntlet:

```
./gradlew --refresh-dependencies :shared:compileTestKotlinJvm :androidApp:compileDebugUnitTestKotlin
```

The reason is mechanical. Gradle caches resolved dependencies aggressively. A contributor who edits `libs.versions.toml`, then runs the gauntlet without the `--refresh-dependencies` flag, may see green compile-tests that were resolved against the stale cache. The CI runner has no cache and resolves cleanly, so a dependency typo or a version that fails to resolve will fail in CI even though the local gauntlet was green.

The first dependency addition this rule applies to is the BouncyCastle artifact `org.bouncycastle:bcprov-jdk18on`, planned for the Argon2id backup cipher implementation per [ADR-0018](docs/adr/0018-argon2id-backup-cipher-design.md) and tracked as D1. The rule is general; it covers every future dependency change too.

The CI workflow itself uses a fresh dependency resolution on every run (no cache key tied to `libs.versions.toml` exists today), so this addendum is a per-contributor local hygiene step. The wire-up of a CI-side dependency-resolution cache and the matching `--refresh-dependencies` gate is a separate, deferred concern outside this addendum's scope.

## What to read before contributing

- [AGENTS.md](AGENTS.md). The load-bearing contract for any agent or human working on the codebase, including vibe-safety tiers, the permission allowlist, the refusal list, and the CI fitness functions.
- [docs/ROADMAP.md](docs/ROADMAP.md). The current milestone, what is shippable now, what is deferred.
- [docs/adr/](docs/adr/). Architecture decisions in MADR format.
- [README.md](README.md). What the app does today and what it deliberately does not.
- [SECURITY.md](SECURITY.md). Threat model and disclosure policy.

## Commit hygiene

- **Conventional Commits prefix.** Every commit message starts with `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, or `ci:`. The vibe-safety tier of the change appears in trailing brackets when relevant, for example `[vibe-dangerous]` or `[vibe-careful]`.
- **Calibration-entry pairing for vibe-dangerous paths.** Any commit that touches a vibe-dangerous surface listed in [AGENTS.md § Vibe-safety tiers](AGENTS.md#vibe-safety-tiers-per-code-helper-5) must also stage a new entry in `.codeit/calibration.jsonl` recording the change's confidence score. The pre-commit hook at `scripts/git-hooks/pre-commit` enforces this mechanically. Install it once per clone with `bash scripts/install-git-hooks.sh`.
- **Prose discipline in commit messages.** The same voice rules as the rest of the codebase. Plain English. The banned-vocabulary list in [AGENTS.md § Refusal list](AGENTS.md#refusal-list-will-not-produce-without-justification) applies to commit messages too. No em-dashes, no theatrical phrasing.

## Tests as specs

The full test-as-spec contract is in [AGENTS.md § Test-as-spec rules](AGENTS.md#test-as-spec-rules). For any change to a vibe-dangerous surface, the failing test lands first in its own commit and gets human review before the implementation lands.
