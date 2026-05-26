# ADR-0020 (M1.2): License posture: AGPL-3.0 for the core repository

Date: 2026-05-26
Status: **Accepted** (M1.2 decision gate closed)
Deciders: Wei Jia (operator sign-off)

> **Slug note:** This file is `0020-license-posture-m12.md`. A separate accepted ADR uses the `0020` prefix for vet-visit modeling (`0020-vet-visit-domain-model.md`). Cross-link by path, not by number alone.

## Context

Milestone 1.2 (`docs/ROADMAP.md` § Milestone 1.2) requires a license decision before M2 ships publicly. The feasibility dossier (`research/00-feasibility-dossier.md` § 11, open question #2) frames the fork as **AGPLv3** (copyleft, network-use disclosure, stronger open-core moat) versus **Apache-2.0** (frictionless reuse, weaker enclosure resistance, preserves more acquirer integration paths).

The ROADMAP row named Covetrus and IDEXX as unlikely to embed AGPL into closed product lines, and Apache+CLA as preserving strategic-acquirer optionality at weaker moat preservation. That tension is real; the operator resolved it for M1.2 on 2026-05-26.

**Repository state at decision time:** Root `LICENSE` already states GNU Affero General Public License version 3 (copyright Wei Jia, 2026). This ADR records the gate resolution and downstream expectations. **No LICENSE rewrite** was required; header and full license text were verified on `main` / branch `docs/adr-0020-agpl-m12`.

Operator lock recorded in `research/decisions/2026-05-26-m12-beta-gates.md` § License (Q6).

## Decision

**The toebeans core repository remains licensed under AGPL-3.0.** The M1.2 license gate is **closed**. M2 public ship may proceed on license posture once other M1.2 gates clear; this ADR does not by itself unblock M2.

## Rationale

### Public-good and anti-enclosure posture

AGPL-3.0 aligns with toebeans' stated trust posture (local-first, no training on user data, owner-controlled portability) documented in ADR-0003 and the feasibility dossier's open-core framing (§ 8, trust-and-portability layer vs moat layer). Copyleft makes network-facing forks of the core disclose corresponding source, which supports **public-good** distribution of improvements to the reminder engine, schema, and portability surfaces rather than silent proprietary forks of the same code.

### Acquisition and partnership tension (acknowledged, not resolved away)

The dossier documents active strategic acquirers in pet health (Mars, IDEXX, Covetrus; Pawprint→Covetrus precedent). `[verified]` A typical strategic buyer integrating consumer app code into a **closed** clinic or PMS product line faces AGPL's source-offer obligations for modified versions and, for SaaS/network use, § 13 Affero triggers. `[inferred]` That reduces acquirer appetite relative to Apache-2.0 or a dual-license commercial grant.

The operator chose AGPL **knowing** that trade-off: preserve copyleft and moat alignment for the open core over maximizing unnamed acquirer optionality at M1.2. Revisit is not scheduled here; a future **dual-license** or **commercial exception** program would be a **new** ADR and explicit legal review, not a silent LICENSE edit.

### Why not Apache-2.0 at M1.2

Apache-2.0 would ease embedding in proprietary stacks and contributor onboarding for corporations with AGPL policies. It would weaken copyleft protection for the core as currently shipped. The operator rejected that path for the M1.2 gate to keep LICENSE, public positioning, and dossier open-core narrative consistent without a mid-beta license churn.

## Contributor expectations

1. **Inbound contributions** (if the project opens beyond the current personal-maintainer model): contributors must accept that merged work is AGPL-3.0. A Contributor License Agreement (CLA) is **not** in force today; if contribution volume warrants it, add CLA only via a follow-up ADR: do not imply CLA exists in README or CONTRIBUTING without that ADR.
2. **Copyright headers:** New source files should carry SPDX `AGPL-3.0-or-later` (or project-standard header) consistent with root `LICENSE`. Match surrounding files in each module.
3. **Dependencies:** Prefer dependencies compatible with AGPL distribution. Flag copyleft-incompatible or proprietary SDK additions in PR review before merge. Network/analytics SDKs remain out of scope per ADR-0003 and project constraints.
4. **Forks and PRs from employers:** Contributors whose employers block AGPL contribution need written employer clearance or must not contribute code; documentation-only PRs may still trigger policy review on their side.

See `CONTRIBUTING.md` and `AGENTS.md` for engineering gates; this ADR is the license-specific addendum.

## Acquirer and partner implications `[inferred]`

These are planning assumptions, not legal advice. Confirm with counsel before outreach, data-room, or store compliance claims.

| Stakeholder | AGPL implication (high level) |
|---|---|
| **Strategic acquirer (Covetrus, IDEXX, Mars-class)** | Unlikely to acquire **only** the AGPL core for closed incorporation without a separate commercial license or clean-room reimplementation. Consumer-app precedent (Great Pet Care) suggests value may sit in distribution and records integration, not raw app source: acquirer may prefer asset purchase of brand/users with code licensed separately or rebuilt. |
| **Clinic or rescue partner** | SaaS or hosted fork of toebeans serving users over a network triggers AGPL source-offer obligations unless they use unmodified binaries and comply with license terms. Partnership agreements should name who operates network-facing instances. |
| **Insurer co-marketing (dossier wedge candidates)** | White-label or embedded experiences need license review before API or UI reuse; AGPL is not a drop-in for proprietary member portals. |
| **Open-source collaborators** | AGPL is explicit; forks must comply. Good fit for trust-aligned contributors; poor fit for default corporate OSS programs that allow only permissive outbound licenses. |

**Human follow-up (operator):** Legal review before public compliance claims in Play Store listing, partnership decks, or "open source" marketing copy.

## Non-goals

- **Dual-license or commercial AGPL exception**: not decided; out of scope for M1.2.
- **Relicensing historical commits**: not required; LICENSE already AGPL-3.0.
- **Submodule or asset licensing**: fonts, images, and third-party deps retain their own licenses; this ADR covers the **application source repo** root posture only.
- **Patent grant strategy**: no change; rely on AGPL-3.0 patent retaliation terms unless counsel advises otherwise.
- **Changing vet-visit ADR `0020-vet-visit-domain-model.md`**: unrelated domain decision; no merge or renumber.

## Consequences

### Positive

- M1.2 "license decision made + LICENSE reflects it" DoD item satisfied without file churn.
- Single license story for GitHub, forks, and dossier open-core narrative.
- Clear contributor and partner expectations documented before M2 public work.

### Negative / accepted costs

- Reduced probability of naive strategic acquisition of the repo as a permissively licensed code drop. `[inferred]`
- Partners needing proprietary embedding must negotiate separately or use unmodified distribution paths compliant with AGPL.

## Verification

- [x] `LICENSE` first lines: "GNU Affero General Public License, version 3" (2026-05-26, branch `docs/adr-0020-agpl-m12`).
- [x] `docs/ROADMAP.md` M1.2 license row updated to Accepted / AGPL-3.0.
- [x] `research/decisions/2026-05-26-m12-beta-gates.md` § License cross-links this ADR.

## References

- `research/00-feasibility-dossier.md`: § 3.3 (moat), § 4 (acquirers), § 8 (open-core), **§ 11 open question #2** (licensing)
- `research/decisions/2026-05-26-m12-beta-gates.md`: operator lock
- `docs/ROADMAP.md`: Milestone 1.2
- Root `LICENSE`: canonical legal text
