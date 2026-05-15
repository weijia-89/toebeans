# toebeans

> Your pet's admin layer. Medication reminders that don't lie to you.

**Status:** v0.1.0-dev — pre-MVP scaffold. Reminders-first slice in active development.

**License:** [AGPL-3.0-or-later](LICENSE)

---

## What this is

toebeans is a local-first, Android-first pet admin app. v1 ships medication and dose reminders with adherence logging for multi-pet households. No cloud. No AI. No symptom checker. No diagnostic content.

The design rationale, market evidence, and competitive analysis are documented in:

- [`research/00-feasibility-dossier.md`](research/00-feasibility-dossier.md) — feasibility + M&A precedent + competitive density
- [`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md`](docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md) — MVP design doc with falsifiable success criteria
- [`docs/adr/`](docs/adr/) — architecture decisions

## What this is NOT

- Not a symptom checker.
- Not a diagnostic tool.
- Not an AI vet.
- Not a substitute for veterinary care.
- Not a clinic-facing PMS.

## Success criteria (slice 1)

1. **Reliability:** zero missed reminders in a 30-day soak test on a non-OEM-customized Android device with battery optimization disabled.
2. **Latency:** reminders fire within ±60 seconds of scheduled time.
3. **Survivability:** all pet, schedule, and dose-event data survives app reinstall via at least one user-selected backup mechanism.

## Stack

Kotlin + Compose Multiplatform + SQLDelight + WorkManager + AlarmManager + Koin + kotlinx-datetime. See [`docs/adr/0001-kmp-and-compose-multiplatform.md`](docs/adr/0001-kmp-and-compose-multiplatform.md).

## Getting started (dev)

**Prerequisites:**

- **JDK 17** (NOT 21+). Kotlin 2.0.21 + AGP 8.7 do not support newer JVMs reliably. `brew install openjdk@17`.
- **Android SDK 34+** (via Android Studio or `cmdline-tools` + `sdkmanager`). Required only for Android compile + tests.
- Gradle is **not** required to be installed system-wide — the project ships a wrapper.

**Set JAVA_HOME for this project:**

The brew openjdk@17 install is keg-only (it does not register itself system-wide). For zsh:

```bash
# Add to ~/.zshrc, or run per-shell:
export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

**Verify the scaffold (no Android SDK required):**

```bash
./gradlew :shared:jvmTest --console=plain
# Expected: 4 failures in SchedulePhaseRulesTest with kotlin.NotImplementedError.
# That IS the v0.1 spec — DefaultScheduleCalculator is a stub. The failing tests
# define what taper-correctness MUST mean before the next agent (or human) writes the impl.
```

**Verify the fitness functions (no Android SDK or JDK required):**

```bash
for s in scripts/*.sh; do bash "$s" .; done
# Expected: all five gates report ✓.
```

**Build the Android app (requires Android SDK):**

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug   # to a connected device/emulator
```

**Build + run the full CI surface:**

```bash
./gradlew check    # ktlint + detekt + tests + fitness functions (once wired into CI)
```

## Repository layout

```
toebeans/
  shared/                         # KMP shared module (core models, scheduler, backup codec)
  androidApp/                     # Android-specific app (Compose UI + platform actuators)
  docs/
    adr/                          # MADR-short architecture decisions
    superpowers/specs/            # design specs (approved before scaffolding)
    ARCHITECTURE.md
  research/
    00-feasibility-dossier.md
  scripts/                        # fitness-function and dev scripts
  .codeit/                        # codeit engagement state (idempotent re-runs)
  .github/workflows/              # CI: lint + test + fitness functions
```

## Contributing

This repo follows [`AGENTS.md`](AGENTS.md) (the agent host contract). Read it before opening a PR or running an LLM-driven change.

The **reminder-firing path is vibe-dangerous** (medication-critical). Changes to `shared/.../scheduler/`, `shared/.../backup/`, `androidApp/.../notifications/`, SQLDelight schema migrations, or AndroidManifest permissions require human-written tests and human-read diffs per [`code-helper` §1](https://github.com/wei/code-helper.skill).

## Security

See [`SECURITY.md`](SECURITY.md). Report vulnerabilities privately.
