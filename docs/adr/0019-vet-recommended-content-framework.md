# ADR-0019: Vet-recommended content framework. Owner-entered or vet-curated, rule-based delivery, never LLM-generated.

Date: 2026-05-19
Status: Proposed
Deciders: Wei Jia (with Cascade)

## Context

The product vision Wei stated in the 2026-05-19 orchestrator session names "a full pet health tracker with ability to do outreach, noting scheduled followups, and incorporating vet-recommended advice for checkins and treatments with tips and reminders." This extends beyond the current MVP medication-and-dose-reminders scope.

`AGENTS.md` (and its `CLAUDE.md` parity copy) refuses, under § Vibe-impossible, "any AI symptom checker, diagnostic content, drug interaction warning, or 'is this dose safe?' feature." The MVP design spec (`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md` § 8.1) carries the same refusal. Read literally, the current contract forbids the app from surfacing any vet-related content at all, including content the user types in from their own vet's discharge instructions.

The feasibility dossier (`research/00-feasibility-dossier.md` § 3.3) names "chronic-condition admin overlays" (CKD, diabetes, seizure, derm) as moat candidate #4 and explicitly notes a veterinary advisory board is required. The same dossier § 8.1 hard-refuses any "AI vet" framing in marketing or UI.

The ROADMAP already contains a one-line precedent for the reconciliation at M4 line 159: "Drug-interaction warnings: STRICTLY rule-based, vet-curated, NO LLM (forbidden by AGENTS.md vibe-impossible)." That framing (**rule-based, vet-curated, no LLM**) is the seed of the framework this ADR records, but it lives in a one-bullet milestone entry and is not formalized.

Multiple downstream features need a single posture to derive from: vet visit / appointment reminders, vaccine reminders, chronic-condition overlays, document-timeline-driven content surfacing, drug-interaction warnings, AAHA-2025 referral packets. Re-litigating the AI-content question per feature is the failure mode this ADR exists to prevent.

## Decision

Vet-recommended content is permitted in the app under all four of the following constraints. A feature that cannot meet all four is forbidden.

### 1. Source constraint

Content must originate from one of three pathways:

- **Owner-entered.** The user types the content. Examples: "my vet said give 0.5 mL twice daily with food," "recheck in six weeks," "watch for vomiting."
- **Vet-record imported.** The user imports a vet-issued document via the Slice 3 document-timeline surface (ROADMAP M2 placeholder). The app surfaces text the vet wrote, attributed to the imported document. No app paraphrasing.
- **Vet-advisory-board curated.** A registered veterinary advisory board (a formal body, defined in a separate ADR before any curated content ships per Followup F2 below) authors rule-based content for specific chronic-condition overlays. The board's identity, signing authority, and review cadence are part of that future ADR; not this one.

LLMs are not in the source list. Web-scraped vet content, AI-generated summaries of vet content, and AI-paraphrased vet content are all out of scope.

### 2. Delivery constraint

Content delivery is rule-based, never inferred. The app performs no judgment about the user's pet's condition. Rules are deterministic lookup tables, scheduled-event templates, or owner-configured prompts. The same input always produces the same output.

Concretely: a chronic-condition overlay for diabetes ships as a static rule pack ("for pets with diagnosed diabetes, the recommended monitoring schedule from `<advisory body>` is blood-glucose check at frequency X, vet visit at frequency Y, weight check at frequency Z"). The app does not decide which pets need this overlay; the owner applies it explicitly after their vet's diagnosis.

### 3. Framing constraint

The UI never claims authorship of advice. Permitted phrasings:

- "Your vet noted [...]"
- "Your reminder schedule for [condition] includes [...]"
- "[Vet advisory board name] recommends [...] for pets with [condition]."
- "From your imported [document name]: [...]"

Forbidden phrasings:

- "The app recommends [...]"
- "Based on your pet's symptoms [...]"
- "We suggest [...]"
- "Your pet probably has [...]"
- Anything that implies the app is the source of medical judgment.

Attribution must be explicit and machine-checkable. A UX style guide (Followup F4) names the wording rules.

### 4. Refusal carve-out

The following remain hard-forbidden and the carve-out is explicit in `AGENTS.md` and `CLAUDE.md` (amendment per Followup F1):

- AI symptom checkers (LLM or rules-based pattern matching against owner-described symptoms).
- AI-generated diagnostic content.
- AI-derived drug-interaction warnings.
- AI-derived treatment recommendations.
- Any feature where the LLM, not a vet or the owner, is the source of advice.
- Any feature that paraphrases vet content through an LLM before display.

The carve-out distinguishes "vet-curated, owner-entered, or vet-record-imported content delivered rule-based under ADR-0019" from "AI-derived content of any kind." The former is permitted; the latter is vibe-impossible.

## Specific application to known features

| Feature | Source pathway | Delivery | Framing example | Status |
|---|---|---|---|---|
| Drug-interaction warnings (ROADMAP M4 line 159) | Vet-advisory-board curated rule pack | Rule-based lookup at medication entry | "[Advisory body] flagged a known interaction between [drug A] and [drug B] for [species]." | Already framed correctly; this ADR formalizes |
| Chronic-condition admin overlays (Moat #4 per dossier § 3.3) | Vet-advisory-board curated per-condition rule packs | Scheduled reminder templates per condition | "Your CKD care schedule, from [advisory body]: monthly weight check, quarterly bloodwork, every-6-month vet visit." | Gated on Followup F2 (advisory board ADR) |
| Vet visit / appointment reminders | Owner-entered or vet-record-imported | Scheduled one-shot reminder | "Your vet noted a 6-week recheck on [date]." | New domain model needed; see ROADMAP M2 amendment |
| Vaccine reminders | Owner-entered or vet-record-imported; optionally regional-regulatory rule (e.g., annual rabies in state X) | Scheduled recurring reminder | "Annual rabies booster (from your [date] visit) due [date]." | Subtype of vet-visit-reminder domain |
| Treatment tips within chronic-condition overlays | Vet-advisory-board curated | Static text per condition | "[Advisory body] suggests rotating injection sites for insulin in diabetic cats." | Gated on Followup F2 |
| Document-timeline content surfacing (Slice 3 per dossier § 8.2) | Vet-record imported | Display as-imported, no paraphrasing | "From your [doc name] imported [date]: [verbatim vet text]." | New surface; ROADMAP M2 amendment |

## Consequences

### Positive

- One written framework for all vet-recommended content. New features derive from it.
- The vibe-impossible refusal stays unambiguous (no LLM-derived advice) while the broader product vision becomes implementable.
- Moat #4 (chronic-condition overlays) gains a clear path, gated only on the advisory-board decision.
- Slice 3 (document timeline) gains a clear posture for what to surface from imported records.
- AAHA 2025 § 4's shared-portal referral model maps cleanly to the owner-controlled, vet-curated, owner-shared posture this framework establishes.
- The framework is auditable. Every piece of content in the app traces to one of three named sources; no source means the content cannot ship.

### Negative

- A veterinary advisory board is a real organizational cost (dossier § 11 Q3 names it as a human-decision before chronic-condition overlays can ship). This ADR does not solve the cost; it makes the prerequisite explicit.
- Vet-curated rule-pack storage, signing, versioning, and update channels need their own ADR (Followup F3). Until that lands, only owner-entered and vet-record-imported pathways are usable.
- The `AGENTS.md` / `CLAUDE.md` amendment to add the carve-out is vibe-dangerous (touches the contract files) and requires the parity-check gate. The amendment is small but is not in this ADR's commit.
- Framing discipline ("your vet noted" not "the app recommends") needs a UX style guide. The repo does not currently have one.
- Edge case: regional regulatory rules (annual rabies booster) blur the "vet-advisory-board curated" line. Are these part of the rule pack, or a separate "regulatory rule" pathway? Followup F2 (advisory-board ADR) names which body owns regional rules.

### Mitigations

- The advisory board is gated separately as a dossier § 11 Q3 human-decision. This ADR does not block Slice 1 MVP work.
- The rule-pack storage ADR is gated on the advisory-board ADR. Until both land, the only ship-able pathways are owner-entered and vet-record-imported.
- The `AGENTS.md` amendment is filed under Followup F1 with the parity-check requirement explicit.
- Until the UX style guide lands (Followup F4), feature work that introduces new framing strings includes inline review against the four permitted phrasings in §3 above.

## Rejected alternatives

- **Keep the broad refusal as-is and ship vet-recommended advice anyway.** Rejected. Internally inconsistent; depends on reviewer-by-reviewer judgment to admit each feature. The reason this ADR exists is to prevent that drift.
- **Allow LLM-generated advice if a vet manually reviews each output before shipping.** Rejected. Auditability and reproducibility are unenforceable at scale; the LLM remains the source, the vet is a rubber stamp, and the user cannot tell the difference. Also fails the dossier § 8.1 marketing posture ("no AI vet framing").
- **Allow LLM-paraphrasing of vet-entered content for readability.** Rejected for the same reason. The LLM becomes a content modifier and the trail of "what the vet actually said" is lost. The user reads the LLM's paraphrase, not the vet's words. Any divergence is the app's claim, attributed implicitly to the vet.
- **Defer this ADR until moat #4 is ready to ship.** Rejected. Lower-tier features (drug interactions, vet-visit reminders, vaccine reminders, document-timeline surfacing) need the framework now to avoid per-feature re-litigation.
- **Make the framework permissive at MVP and tighten later.** Rejected. The vibe-impossible posture has been load-bearing since v0.1; loosening it without explicit boundary-setting is the path to feature drift. Permissive-then-tighten is harder than restrictive-then-carve-out.

## Followups

These artifacts are proposed by this ADR but NOT created here. Each is sized for its own session.

- **Followup F1 (vibe-dangerous, parity-required).** Amend `AGENTS.md` and `CLAUDE.md` § Vibe-impossible to add the carve-out distinguishing "AI symptom checker / AI-generated diagnostic content / AI-derived drug-interaction warning / AI-derived treatment recommendation" (forbidden) from "vet-curated, owner-entered, or vet-record-imported content under ADR-0019" (permitted under the four constraints). The CI fitness function `tests/agents_claude_parity.sh` enforces parity; the amendment lands in both files in the same commit. Reviewer-approval gate per `AGENTS.md` § Review gates line 64. Estimated effort: one session, ~30 min.

- **Followup F2 (vibe-careful).** Veterinary advisory board ADR. Names the formal body (or bodies, e.g., per-specialty), charter, content review cadence, signing/integrity mechanism for rule packs, update channel. Gated separately by dossier § 11 Q3 human-decision (Wei picks whether to set up the board and at what cost). Estimated effort: one session for the ADR; advisory-board recruitment is real-world work outside this codebase.

- **Followup F3 (vibe-careful).** Vet-curated content storage + update ADR. How rule packs are stored, versioned, updated (no network in v1 per ADR-0003 local-first; rule packs may need a manual import surface or a signed offline distribution channel), and audited. Gated on F2.

- **Followup F4 (vibe-safe to vibe-careful).** UX style guide section on attribution. The four permitted phrasings from § Decision § 3 above, plus forbidden phrasings, plus screen-by-screen examples. Lives in a new `docs/UX_STYLE_GUIDE.md` file, or as an `ARCHITECTURE.md` section. Estimated effort: ~60 min.

- **Followup F5 (vibe-careful).** Vet-visit / appointment-reminder domain model ADR. The `VetVisit` model (or similar), its relationship to `Pet` and `Medication`, its scheduled-followup semantics, vaccine-as-subtype, regional-regulatory-rule semantics. Lands after F1 and before any UI work on the vet-visit-reminder feature.

## Revisit conditions

This ADR moves to Superseded if any of:

- The veterinary advisory board (Followup F2) is rejected as too costly or unfeasible. Moat #4 (chronic-condition overlays) needs re-evaluation; the framework may need to drop the third source pathway and rely only on owner-entered + vet-record-imported.
- A production-safe LLM-content auditability framework emerges that the research community considers usable for medication-adjacent advice (none exists in mid-2026; revisit probability inside the M6 horizon is low).
- A new regulatory framework (e.g., FDA pet-app guidance, state veterinary boards' consumer-app rules) mandates content-source disclosure stricter than this ADR. The framework is amended to match.
- ROADMAP M4 line 159's drug-interaction-warning posture is itself revised (e.g., a vet partner ships a curated rule pack and the storage mechanism is the actual blocker). The application table in this ADR is updated.

## References

- `AGENTS.md` § Vibe-impossible. The refusal list this ADR carves out from, pending Followup F1.
- `CLAUDE.md` § Vibe-impossible. Same; CI parity gate at `tests/agents_claude_parity.sh`.
- `docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md` § 8.1 and § 12 (positioning + threat model). The original anti-AI-vet posture.
- `research/00-feasibility-dossier.md` § 3.3 (moat candidates), § 8.1 (positioning), § 11 Q3 (veterinary advisory board human-decision).
- `docs/ROADMAP.md` M4 line 159. The drug-interaction precedent ("STRICTLY rule-based, vet-curated, NO LLM").
- ADR-0005 (`docs/adr/0005-vibe-dangerous-reminder-firing.md`). The vibe-dangerous classification this framework inherits the posture from.
- AAHA 2025 Referral Guidelines § 4. The shared-portal model the vet-curated path aligns to.
- `docs/issues/v0.1-followups.md`. The granular issue register; entries for F1-F5 land there once each gets a tracking line.
