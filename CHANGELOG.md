# Changelog

All notable changes to toebeans will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial repository scaffold.
- Feasibility dossier (`research/00-feasibility-dossier.md`) with market, competitive, and M&A evidence.
- MVP design doc (`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md`) for the reminders-first slice.
- AGENTS.md / CLAUDE.md vibe-coding host contract.
- SECURITY.md threat model.
- AGPL-3.0-or-later license.
- Kotlin Multiplatform + Compose Multiplatform Gradle skeleton.
- SQLDelight schema for Pet / Medication / Schedule / SchedulePhase / DoseEvent.
- `ScheduleCalculator` interface (KMP shared, pure-functional).
- First failing test: `SchedulePhaseRulesTest` (test-as-spec for taper correctness).
- 5 ADRs: KMP+Compose, AlarmManager+WorkManager hybrid, local-first/no-cloud, tapering schedule model, vibe-dangerous classification.
- CI workflow with 5 fitness functions (no-network, no-analytics, scheduler purity, permission whitelist, scheduler coverage).
- `.codeit/state.jsonl` recording the discovery + planning phases of the codeit engagement.

### Reasoning

- Slice 1 scope is reminders-first per the design doc. No documents, no OCR, no cloud, no AI.
- Vibe-dangerous classification of the reminder-firing path means all scheduler/backup/notification changes require human-written tests and human-read diffs.

[Unreleased]: https://github.com/REPLACE_BEFORE_PUBLIC/toebeans/compare/v0.0.0...HEAD
