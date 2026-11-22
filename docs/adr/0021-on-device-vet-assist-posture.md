# ADR-0021: On-device veterinary assist posture. DRAFT for operator decision: keep the ADR-0019 ban (Option A) or amend it under strict gates (Option B). Nothing changes until Wei Jia selects.

> Note (2026-11-22): The agent contract files were removed from this public repository. References in this document to those files mean the local working copies kept in a contributor's checkout.

Date: 2026-11-15
Status: Proposed
Deciders: Wei Jia (pending)

## Context

The operator directive of 2026-11-15 asks to remove the "LLM generated advice" refusal and instead research training a very small, local model on high-quality veterinary sources (Merck Vet Manual, official books), giving suggestions that users should check, plus emergency-line links. The emergency-links work is being shipped separately without AI and is out of scope here.

The governing framework is ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`, Status: Proposed, de facto operative since its carve-out landed in `AGENTS.md` line 33). Its four conjunctive constraints admit vet-recommended content only from owner-entered, vet-record-imported, or vet-advisory-board-curated sources, delivered rule-based with strict framing. A model-generated suggestion fails the source constraint and the refusal carve-out as written, regardless of where the model runs or what it was trained on.

The feasibility research (`research/on-device-vet-model-feasibility.md`, same date) establishes, with cited sources:

- The directive's named training corpus (Merck Vet Manual, commercial textbooks) is not licensable for training under the sources' current terms; only US-government public-domain sources, CC BY / CC BY-SA material, and human-verified synthetic data are clean for an AGPL, F-Droid-distributed app.
- The ADR-0008 device floor (2-3 GB RAM, 8 MB APK target, "ML-on-device" named as requiring explicit ADR override) excludes every current model candidate except a 270M-parameter class model, and the best-licensed candidates (Qwen3 0.6B, Apache 2.0) strain that floor.
- The reliability literature (cited in the research doc) measures the worst failure mode, a confident wrong answer about a toxin or dose, at 15-40%+ in medication-adjacent QA even for frontier models, with smaller and distilled models performing worse, and no published evidence at all for sub-1B models on veterinary medication questions.
- F-Droid has no formal on-device AI policy as of September 2026; a moderator position flags non-free-licensed models as NonFreeAsset anti-features (Gemma 3's terms and the Llama 3.2 Community License would qualify); IzzyOnDroid currently excludes AI apps.

ADR-0019's § Revisit conditions reopen the question if "a production-safe LLM auditability framework emerges that the research community considers usable for medication-adjacent advice". The research finds that condition not met in November 2026. This ADR exists because the operator directive is a legitimate exercise of that revisit clause: it records the two live postures honestly and defers the choice to the operator. Until the operator selects an option, ADR-0019 stands unchanged and on-device model work remains vibe-impossible under `AGENTS.md` § Vibe-impossible.

## Decision

This ADR does not decide. It presents two mutually exclusive options. The decision gate is Followup F1.

### Option A: keep ADR-0019 as-is; the on-device model stays forbidden

Under Option A:

- `AGENTS.md` and `CLAUDE.md` § Vibe-impossible are unchanged in effect. On-device models are not an exception to the ADR-0019 carve-out; an LLM running locally is still an LLM, and model-generated suggestions remain vibe-impossible.
- The operator's "qualified suggestions" goal is carried by static vet-curated suggestion packs under ADR-0019 pathway 3, using the rule-pack machinery ADR-0020 § B/§ C already defines (curator and `source_citations` provenance per entry, app-update-only distribution, known-answer tests pinning each entry to its cited source). Toxin emergency cards and "questions to ask your vet" checklists ship in the same pack format. Combined with the separately-shipped emergency-line links, this covers the goal without any inference in the app.
- The hybrid path ("model drafts offline, human curates, ships as static pack", research doc § 8.3) is available only if the operator explicitly rules where the toolchain line sits. Option A's amendment text below makes the ruling explicit and narrow: the LLM may draft; a human verifies every line against its cited source; nothing an LLM touched ships unverified.

Option A's exact `AGENTS.md` / `CLAUDE.md` amendment text (clarifying only; lands in both files in one commit, parity-gated by `tests/agents_claude_parity.sh`; appended after the ADR-0019 carve-out bullet at line 33):

```text
- On-device or offline language models do not qualify for the ADR-0019 carve-out. An LLM running locally is still an LLM: model-generated suggestions remain vibe-impossible. An LLM may be used as an offline drafting aid only when every shipped line is human-verified against a cited source and carries provenance metadata per ADR-0020.
```

What would falsify Option A: the ADR-0019 revisit condition firing, meaning a published and independently replicated auditability framework meeting the bar described in the research doc § 7 (near-zero wrong-answer rate on toxin and dose items, deterministic outputs under identical inputs, refusal behavior surviving adversarial fabrication tests), or a documented demonstration that the static-pack path cannot cover realistic user needs. Absent either, Option A is the conservative default that keeps the medication-critical posture intact.

### Option B: amend the posture with strict gates (the on-device assist model, gated)

Under Option B, an on-device veterinary assist model becomes permitted if and only if every gate below holds at merge time and continuously. Any gate failing reverts the feature to forbidden.

- **G1 On-device only.** Model weights load from APK assets. No network call at inference, model load, or update time; the no-network fitness function is extended to fail any AI dependency that reaches the network. Model updates ship only with app releases (ADR-0003 posture; Google's server-hosted model download path is excluded).
- **G2 Framing.** Every model output renders with a fixed, non-dismissible prefix: "Draft suggestion. Verify with your veterinarian." Outputs are never attributed to a vet, an advisory body, or Merck/AAHA/AVMA. The suggestion surface is read-only: no model output can write to a reminder, a dose, a schedule, or a `VetVisit` field without explicit human entry through existing owner-typed flows.
- **G3 Scope.** Suggestions are limited to a closed, reviewed taxonomy (monitoring questions, "when to contact your veterinarian" categories, general care reminders). Diagnosis requests, dose-change requests, and drug-interaction questions produce a hard-coded refusal to contact a veterinarian, with no generated clinical content.
- **G4 Evaluation merge gate.** A versioned evaluation suite in-repo measures: exact-dose recall against public-domain FDA CVM label text; toxin-escalation correctness (the correct answer is always immediate escalation to a poison line or emergency vet); refusal correctness on out-of-scope questions; output consistency across seeds; and adversarial fabrication resistance (the model must not elaborate a planted false detail). CI fails on metric regression. Ship thresholds: 100% toxin-escalation correctness, zero wrong doses in the sampled set, refusal correctness at or above 95%, for the exact artifact that ships. Metrics are published in-repo with each model version.
- **G5 Data licensing.** The training corpus is restricted to US-government public-domain sources, CC BY / CC BY-SA material, and human-verified synthetic data authored or reviewed by qualified people, with a provenance ledger file naming every source and license. No scraped or bulk-ingested copyrighted sources (no Merck, no textbooks, no association guidelines). Teacher-model terms are respected: no Gemma-terms or Llama-license teacher may generate training content for a shipped model.
- **G6 Artifact integrity.** Model weights, evaluation artifacts, and the provenance ledger are versioned in-repo (or delivered through a signed offline channel) with a reproducible-build record. The shipped artifact hash is recorded in the release notes.
- **G7 F-Droid size and license strategy.** The model license must be OSI-free (Apache 2.0 or MIT; the Gemma 3 terms and the Llama Community License do not qualify). The model ships in its own ABI-split APK or as an accepted size exception; the runtime builds from source for F-Droid reproducibility.
- **G8 ADR-0008 override.** Option B requires a companion ADR explicitly overriding ADR-0008's 8 MB APK target and its "ML-on-device" anti-pattern for this one feature, or the feature targets only devices above the ADR-0008 floor with a documented floor check at runtime.

Option B's exact `AGENTS.md` / `CLAUDE.md` amendment text (lands in both files in one commit, parity-gated; appended after the ADR-0019 carve-out bullet at line 33):

```text
- A single, operator-vetted on-device veterinary assist model is permitted under ADR-0021's eight gates (on-device only, draft-suggestion framing, closed taxonomy with hard refusals, evaluation suite as a CI merge gate with published metrics, licensed data with a provenance ledger, versioned artifact integrity, F-Droid-compatible license and packaging, ADR-0008 override). Any gate failing makes the feature vibe-impossible again. Model-generated suggestions never auto-apply to reminders or doses, and never claim vet authorship.
```

What would falsify Option B: any single wrong toxin or dose answer reaching a user; the evaluation suite failing its thresholds on a model that cannot be improved within the licensing constraints; inability to assemble a corpus deep enough for the taxonomy under G5; a RAM or storage violation on ADR-0008 floor devices; an F-Droid rejection or NonFreeAsset flag that packaging cannot resolve; or the operator losing the appetite to maintain the eval suite across model updates. Option B carries a standing maintenance cost (eval suite upkeep per model version) that Option A does not.

## Consequences

### Positive (both options)

- The operator directive gets a researched answer with named blockers and a decision gate, instead of silent drift or silent refusal.
- Option A keeps the vibe-impossible posture fully intact and delivers the goal's substance through the already-accepted ADR-0020 machinery.
- Option B, if selected, would be the most auditable consumer LLM deployment this posture could reasonably allow: pinned weights, published metrics, deterministic refusal paths, and no network. The gates convert the ADR-0019 objections from philosophical to mechanical wherever possible.

### Negative (both options)

- Option A does not give per-pet, free-text suggestions; owners get curated packs and emergency links, not answers to arbitrary questions. That gap is real and is the honest cost of the posture.
- Option B is a standing liability surface: a published eval report that later proves optimistic becomes a patient-safety incident, on a medication-critical app, on budget hardware, with a legally thin corpus. The research doc's confidence is High that no sub-1B evidence supports the thresholds in G4 today.
- Both options require the contract-file amendment (vibe-dangerous, parity-gated, human review) before any implementation.

### Mitigations

- The falsifying experiment (research doc § 9) costs about one day, touches no app code, and produces the evidence the operator needs before choosing. Prediction: refusal and escalation pass, exact-dose recall fails; if the prediction fails, the operator has positive evidence for Option B.
- The advisory-board path (ADR-0019 F2) remains the long-term cure for the "qualified suggestions" goal under Option A; static packs are the pre-board interim, exactly as ADR-0020 § B already established.

## Rejected alternatives

- **Ship the on-device model and rely on "users should check" disclaimers.** Rejected as an amendment-free path: the disclaimer relocates none of ADR-0019's constraints. The source is still the model; delivery is still non-deterministic; and the literature shows plausibility does not predict correctness (a 70B model's answers were rated maximally plausible while 19.7% were hallucinations; see research doc § 5.3).
- **On-device RAG with verbatim quotes and citations.** Rejected under the current ADR (research doc § 8.2): model-selected quote assembly fails the source, delivery, and framing constraints. It is the first candidate to reconsider if Option B is selected, because verbatim-quote-only output plus deterministic verification is the closest architecture to rule-based delivery. It would still need every Option B gate.
- **Distill training data from Merck Vet Manual or commercial textbooks.** Rejected on three independent grounds (research doc § 4.5): the sources' terms forbid reuse; the USCO Part 3 analysis disfavors fair use where access was unlawful or licensing markets exist (Bartz v. Anthropic held pirated-source training not fair); and Gemma-terms or Llama-license teachers would additionally violate their own use policies by generating veterinary-advice training content.
- **Wait for a future model generation to make this cheap.** Rejected as a plan, kept as a revisit condition: Gemma 4 E2B (Apache 2.0) still needs 2.9 GB at 4-bit, above the ADR-0008 RAM floor. Hardware progress is real but the corpus and reliability problems are not hardware problems.

## Followups

These are proposed by this ADR and NOT created here. Each is sized for its own session.

- **F1 (human decision, the only gate).** The operator selects Option A or Option B. Until this happens, ADR-0019 stands unchanged and on-device model work is refused per `AGENTS.md` § Vibe-impossible. Estimated effort: one operator session after reading the research doc.
- **F2 (Option A path; vibe-dangerous, parity-required).** Land the Option A amendment text (exact text above) in `AGENTS.md` and `CLAUDE.md` in one commit; `tests/agents_claude_parity.sh` must pass; reviewer approval per AGENTS.md § Review gates. Estimated effort: ~30 min.
- **F3 (Option B path; vibe-dangerous, parity-required).** Land the Option B amendment text (exact text above) with the same parity and review gates, plus the companion ADR-0008 override named in G8.
- **F4 (either path; vibe-careful).** The falsifying experiment of research doc § 9: evaluation set build (public-domain sources only), base-model runs, one QLoRA run, results recorded under `research/`. No app code changes.
- **F5 (Option B only; its own ADR).** Evaluation-suite specification ADR: datasets, thresholds, seeds, CI wiring, and the published-metrics format. Also the provenance-ledger schema for G5.
- **F6 (Option A only; existing gate).** Suggestion-pack content flows through ADR-0019 F2 (advisory-board ADR) and ADR-0020's F5b known-answer-test pattern. No new followup created here.
- **ADR-0019's status is not changed by this ADR in any option.** If Option B is selected, the ADR-0019 amendment happens through F3's contract-file edit and a superseding note recorded in a future revision of that ADR, never through a silent edit.

## Revisit conditions

This ADR moves to Superseded (or its selected option is reopened) if any of:

- The ADR-0019 revisit condition fires: a production-safe, community-recognized LLM auditability framework for medication-adjacent advice exists, meeting the concrete bar in research doc § 7. Option B reopens automatically; Option A holders should re-run the falsifying experiment against the new framework.
- A sub-1B-class model ships with a published veterinary or medication-QA evaluation meeting the G4 thresholds, or the falsifying experiment's pass bar is met by a new model. The operator decision gate F1 reopens.
- F-Droid lands a formal on-device AI policy, changing the G7 analysis. The packaging spike is redone before any Option B work resumes.
- ADR-0008's device floor is revised upward, changing the § 2/§ 6 feasibility analysis. Option B's hardware blockers are re-evaluated.
- A rights holder offers a veterinary training-data license at terms compatible with AGPL redistribution of finetuned weights. The G5 corpus constraint is re-evaluated.

## References

- `research/on-device-vet-model-feasibility.md` (2026-11-15). The evidence base for every claim above: model sizes and licenses, runtime maturity, Merck/AAHA/AVMA terms, USCO Part 3 and Bartz v. Anthropic, the reliability literature, and the falsifying experiment spec.
- ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`). The framework both options operate within; Status unchanged (Proposed).
- ADR-0020 (`docs/adr/0020-vet-visit-domain-model.md`). The rule-pack machinery Option A reuses; § B line 61's "public-domain professional guidelines" phrasing is flagged in research doc § 4.3 for the F5b curation session.
- ADR-0003 (`docs/adr/0003-local-first-no-cloud.md`). The local-only posture behind G1.
- ADR-0008 (`docs/adr/0008-performance-class-target.md`). The device floor behind G7/G8; its "ML-on-device" anti-pattern line.
- `AGENTS.md` § Vibe-impossible (line 32-33) and § No AI features (line 13). The refusal text both options amend or clarify, parity-gated with `CLAUDE.md`.
- Merck Vet Manual FAQ, permissions, and disclaimer pages; AAHA guidelines copyright statement; AVMA terms of use; USCO Part 3 report; Bartz v. Anthropic, 787 F. Supp. 3d 1007 (N.D. Cal. 2025); Gemma Terms of Use and Prohibited Use Policy; Qwen3, SmolLM3, Phi-4-mini, Llama 3.2, LFM2, Gemma 3/4 model cards and licenses; MediaPipe LLM Inference and LiteRT-LM documentation; the on-device benchmark in AACL 2026 short paper (aclanthology.org/2026.aacl-short.1.pdf); the reliability citations in research doc § 5.3. Full URLs in the research doc.
- F-Droid forum thread "Does F-Droid have a formal policy on libre AI?" (2025-2026) and the IzzyOnDroid inclusion-policy citation therein.
