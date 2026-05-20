# ADR-0020: Vet visit and vaccine domain model. VetVisit, Vaccine reference list, and species, age, and region-aware reminder rule packs.

Date: 2026-05-19
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

The MVP design spec (`docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md` § 1) scopes the v1 product to medication and dose reminders. Vet visits and vaccinations are out of v1 scope at that level.

The feasibility dossier `research/00-feasibility-dossier.md` § 3.3 ranks chronic-condition admin overlays (CKD, diabetes, seizure, derm) as moat candidate #4 and notes a veterinary advisory board is required to ship them. § 8.2's Slice 3 names a document timeline as a near-term expansion surface. ROADMAP M2 (`docs/ROADMAP.md` lines 130, 132, 133, 134) names four related expansion items: Argon2id KDF, the document timeline, vet visit and appointment reminders as a first-class domain, and the AAHA-2025-aligned referral packet builder. Two of those rows depend on a `VetVisit` domain model that does not exist yet.

ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`) establishes the framework all vet-related content in toebeans must respect. It defines four conjunctive constraints (source, delivery, framing, refusal scope). It names Followup F5 explicitly: "Vet-visit / appointment-reminder domain model ADR. The `VetVisit` model (or similar), its relationship to `Pet` and `Medication`, its scheduled-followup semantics, vaccine-as-subtype, regional-regulatory-rule semantics. Lands after F1 and before any UI work on the vet-visit-reminder feature."

F1 (the `AGENTS.md` / `CLAUDE.md` carve-out) shipped earlier today via PR #26 and now sits at AGENTS.md line 33. F5 (this ADR) is the next followup. F5 defines the data model and the reminder rule pack shape. The implementation work is deferred to F5a, F5b, and F5c (named in § Followups below).

## Decision

Three artifacts. Each one binds to one or more of ADR-0019's four constraints, named inline.

### A. The `VetVisit` model

A `VetVisit` is an event the user records (past) or schedules (future) at the vet. The model is a sibling of `Medication` and `DoseEvent`, with its own table and its own scheduler integration via F5c.

Fields, in the v1 shape:

- `id`: stable identifier, UUID, NOT NULL. Same pattern as `Medication.id`.
- `pet_id`: foreign key to `Pet.id`, NOT NULL, with `ON DELETE CASCADE` per ADR-0010 SQLite foreign-key enforcement contract.
- `kind`: enumerated type, NOT NULL. Permitted values listed below.
- `scheduled_at`: instant (`TIMESTAMP`), nullable. Non-null for future visits the owner has scheduled. Null for past visits being recorded after the fact.
- `occurred_at`: instant (`TIMESTAMP`), nullable. Non-null for visits the owner is recording as completed. Null for future visits not yet completed.
- `provider`: free-text, nullable. Captures the vet practice name the owner types. A future `Provider` model may normalize this field; out of scope here.
- `notes`: free-text, nullable. Owner-entered content. Satisfies ADR-0019 Source constraint pathway 1 (owner-entered).
- `created_at`: instant (`TIMESTAMP`), NOT NULL. Auditing.

Permitted `kind` values:

- `vaccination`: vaccination administered or scheduled. Links to a `Vaccine` reference row (see B below) via a `VaccinationGiven` association row.
- `wellness`: annual or semi-annual checkup, not driven by a specific complaint.
- `acute`: sick visit, emergency, or urgent care.
- `followup`: recheck following an acute visit or scheduled review of a chronic condition.

A `VetVisit.kind == 'vaccination'` row is paired with a `VaccinationGiven` row that holds the `vaccine_id` foreign key plus the lot number, expiration date, and dose-route fields the owner can type from the discharge paperwork. The `VaccinationGiven` table definition is deferred to F5a (the SQLDelight migration) and lands with the schema work; F5 fixes the shape so F5a writes the migration without ADR re-litigation.

Deferred fields, out of scope for v1 (named here so future work does not require an ADR amendment):

- `cost`: monetary amount the owner paid. Deferred.
- `outcome_codes`: structured outcome tagging (e.g., ICD-style). Deferred.
- `documents`: foreign-key list to a future `Document` model from Slice 3 (document timeline per dossier § 8.2). Document attachment surface is named in ROADMAP M2 line 132 and lands as its own model; this ADR does not define it.
- `recurrence`: explicit "every 12 months" structured recurrence column. The v1 reminder-cadence representation lives in the rule pack (see C below). Per-visit explicit recurrence is reconsidered if the rule-pack approach does not cover all observed cases at M2 internal beta.

### B. The `Vaccine` reference list

A `Vaccine` is a reference entity (not a per-pet event). It describes the vaccine itself (the substance and its standard cadence) and is read at notification-scheduling time to compute the next reminder.

Fields:

- `id`: stable string identifier. Stable across rule-pack versions so `VaccinationGiven.vaccine_id` does not break across an in-app rule-pack update. Naming convention: lowercase short code, e.g., `rabies-us`, `da2ppv`, `fvrcp`, `lepto-4-way`, `lyme`. The string identifier replaces a UUID here because the `Vaccine` rows ship as in-app reference data, not as user-created records.
- `name`: human-readable display name, NOT NULL, e.g., `DA2PPV (canine core combo)`, `FVRCP (feline core combo)`, `Rabies (US-regulated)`.
- `species`: enumerated type, NOT NULL. Permitted values at v1: `dog`, `cat`. The species enum matches the `Pet.species` enum from the MVP design spec § 5 line 137, which is `'dog' | 'cat' at v1 (enum widens later)`. Vaccines are species-specific; a feline `FVRCP` is not interchangeable with a canine `DA2PPV`. When the `Pet.species` enum widens, `Vaccine.species` widens to match in the same migration.
- `core_or_non_core`: enumerated type, NOT NULL. Permitted values: `core`, `non-core`. Sourced from AAHA's 2022 Canine Vaccination Guidelines (with 2025 supplemental updates) for dogs and the AAFP / AAHA Feline Vaccination Advisory Panel for cats. Both classifications are public-domain professional guidelines published by the respective associations. The rule-pack file carries a provenance record naming the guideline edition. `[verified-via-dossier]` for the AAHA referral-guidelines tailwind at § 4.6; the specific vaccination guideline editions are operator-verified at curation time by Wei and pinned in the rule-pack provenance metadata, not by this ADR.
- `default_interval_months`: integer, nullable. The recommended re-administration interval in months for routine boosters. Null for one-time vaccines (e.g., some non-core feline vaccines administered as a single series). The rule pack can override this value per `(species, age_range, regulatory_region)` tuple (see C below).
- `regulatory_region`: list of region codes, NOT NULL, defaulting to an empty list. Non-empty when the vaccine is regulated (e.g., `["US-rabies"]` for rabies in the United States where state-level annual or triennial rabies-booster law applies). Empty for vaccines that are professionally recommended but not legally mandated. The region-code vocabulary is the rule pack's responsibility; this ADR does not enumerate the codes.

A `VaccinationGiven` event is a row in a join table linking a `VetVisit` (where `kind == 'vaccination'`) to a `Vaccine`. The join row holds the `vet_visit_id` foreign key (cascading delete), the `vaccine_id` foreign key, and the lot, expiration, and dose-route fields above. The cardinality is many-to-many because a single vet visit can administer multiple vaccines in one appointment (a routine puppy or kitten visit covers a multi-valent core combo plus rabies, for example).

The `Vaccine` reference list ships in-app as a rule-pack file under the same posture as ADR-0003 (local-first, no cloud, no network fetch). Per ADR-0019 Source constraint pathway 3 (vet-advisory-board-curated), the rule pack is a board deliverable once the board exists per F2. Pre-board, the rule pack is operator-curated by Wei against published AAHA and AAFP guidelines. The pack carries provenance metadata naming Wei as the curator and citing the guideline editions consulted. This pre-board posture is the only acceptable interim path; the alternative (no rule pack, or ad-hoc owner-typed vaccine reminders) does not establish the chronic-condition admin overlay moat per dossier § 3.3.

### C. The reminder rule pack shape

A rule pack is a structured-data file that maps `(species, age_range, regulatory_region) -> [Vaccine reminders + cadence]`. It is the deterministic lookup table the notification scheduler reads at reminder-scheduling time, determining which vaccines apply to a given pet and the cadence at which to remind.

Format choice: JSON. The existing backup codec uses JSON (ADR-0016), so the import and serializer code already handles it. YAML's indentation rules and anchor / alias resolution add implementation ambiguity that JSON does not have. The `kotlinx.serialization.json` dependency already sits in the catalog, so the rule-pack parser requires no new dependency. The trade-off (less human-readable than YAML for hand-edits) is mitigated by the rule pack being operator-curated in small, reviewed batches.

Per-entry schema:

- `species`: enumerated, NOT NULL. Same enum as `Vaccine.species`.
- `age_min_months`: integer, NOT NULL. The minimum pet age at which this entry applies (e.g., 2 for the first 12-week puppy DHPP).
- `age_max_months`: integer, nullable. The upper bound of applicability. Null means "lifelong" (e.g., annual rabies for adult dogs has no upper age cutoff).
- `vaccine_id`: stable string, NOT NULL. Foreign reference into the `Vaccine` list (see B).
- `cadence_months`: integer (or fractional, decided by F5b), NOT NULL. Months between reminders for this `(age_range, vaccine)` tuple. May differ from the vaccine's `default_interval_months` (e.g., the puppy series gives DHPP at 6, 9, and 12 weeks; the cadence is 3 weeks, encoded as `cadence_months: 0.75` if the schema permits fractional months, or as discrete entries per shot if not; the F5b first-pack work picks the encoding).
- `regulatory_region`: list of region codes, NOT NULL. Matches the `Vaccine.regulatory_region` semantics. An empty list means "applies in all regions where the pet's home address matches a region this rule pack covers"; a non-empty list scopes the entry.
- `provenance`: nested object, NOT NULL. Fields: `curator` (string, e.g., `Wei Jia` pre-board, `<advisory body name>` post-F2), `source_citations` (list of strings, e.g., `["AAHA 2022 Canine Vaccination Guidelines § 4", "AVMA Model Rabies Control Ordinance 2024"]`), `signed_hash` (optional string, holding a future board-signing hash for post-F2 packs; absent for pre-board packs and carries no cryptographic guarantee in the pre-board interim).

Versioning: each rule pack carries a top-level `version` field (integer, monotonic). The app carries the currently-compiled-in pack version. Updates ship via app update only in v1. Over-the-air pack distribution is deferred to F3 (the rule-pack storage and update ADR), which itself is gated on F2 (the advisory-board ADR). The version field exists so future F3 work can compare a downloaded pack against the in-app pack and decide which one wins.

Delivery: rule-based lookup at notification-scheduling time. The scheduler reads the pack, applies the `(species, age_range, regulatory_region)` filter for each pet, and materializes reminders into the same `DoseEvent`-like notification slots the medication path uses. No LLM is involved in the lookup, in the filter logic, or in the reminder text. The reminder content is the `Vaccine.name` field plus a small templated prose blob per `Vaccine` (e.g., "AAHA recommends an annual booster for `DA2PPV` for dogs over one year of age."), where the prose blob is part of the pack and is operator-curated under the same provenance metadata as the rule entries. This respects ADR-0019 Delivery constraint (rule-based, deterministic, never inferred).

Framing: per ADR-0019 Framing constraint, the reminder text uses attribution phrasing that names the source. Permitted shapes:

- "AAHA recommends an annual booster for `[Vaccine.name]` for dogs over one year of age."
- "Your last `[Vaccine.name]` was on `[VaccinationGiven.occurred_at]` per your notes; the next recommended booster falls around `[computed date]`."
- "Your state requires a rabies booster every three years. Your last was on `[date]`; the next is due `[date]`."

Forbidden shapes (per ADR-0019):

- "We recommend a booster."
- "The app recommends `[Vaccine]` for your pet."
- "Your pet is overdue for a vaccine." (the word "overdue" without naming the recommending body implies app-side judgment)

A UX style guide section codifying the permitted and forbidden shapes is the F4 followup of ADR-0019 (already named; not duplicated here). Feature work on the F5c scheduler integration applies the F4 wording rules at notification-render time.

## Consequences

### Positive

- Vaccine reminders are unblocked for ROADMAP M2 once F5a (the migration) and F5b (the first rule pack) land. F5 (this ADR) records the design; the implementation work runs as three subsequent sessions, F5a for the SQLDelight migration, F5b for the first operator-curated rule pack, and F5c for the scheduler integration.
- Chronic-condition admin overlays (dossier § 3.3 moat #4) derive from the same `VetVisit` model in M4. The `kind == 'followup'` value covers the per-condition recheck cadence, and a future per-condition rule pack reuses the rule-pack shape defined in §C.
- The AAHA-2025-aligned referral packet builder (ROADMAP M2 line 134, dossier § 3.3 moat #3) cites the `VetVisit` rows directly; the referral packet is a read-only export of a date-bounded `VetVisit` window plus the linked `VaccinationGiven` rows.
- The `Document` model that Slice 3 introduces (ROADMAP M2 line 132) has a clean attachment point at `VetVisit` via a future `documents` field. The deferral is named explicitly in § Decision § A above.
- The rule-pack shape is uniform across vaccines, regulatory rules, and (future) chronic-condition overlays. F3 (the storage and update ADR) writes one storage mechanism, not three.

### Negative

- Rule-pack maintenance is operator-curation work in the pre-board interim. Each rule-pack revision requires Wei to verify the source citations and update the provenance metadata. This is a real recurring cost and a real liability surface: an incorrect cadence in the rule pack surfaces as a missed or doubled vaccine reminder for every user the entry applies to. F2 (the advisory-board ADR) shifts the curation work to a formal body, and the liability shifts with it. Until F2 lands, Wei is the named curator.
- Out-of-region vaccines are a coverage gap. The v1 `regulatory_region` enum implicitly assumes a single owner region per pet, or a small set of regions the pack covers. A pet that travels internationally between regulatory regimes (e.g., a US-resident dog that summers in the EU) does not get region-aware reminders for both regimes. The mitigation is a future per-pet `home_region` field on `Pet` plus a multi-region rule-pack lookup; this is deferred and is not a v1 ship blocker.
- Rule-pack updates require an app update in v1. A vaccine guideline change between app releases produces stale reminders until the next ship. The mitigation is F3 (over-the-air pack distribution), which itself is gated on the board signing infrastructure F2 names. The interim cost is bounded by the multi-year cadence at which AAHA and AAFP guideline editions update.
- Pre-board curation by Wei is the only viable pathway today. If Wei misreads a guideline or transcribes a cadence incorrectly, the failure mode reaches every user of the pack. The mitigation is a paired review at curation time (Wei plus one other person who can read the source guidelines) and a known-answer test in the F5b session that pins each rule against the source citation verbatim, which serves as the audit trail.

### Operational

- F5a (the SQLDelight migration adding the `VetVisit`, `Vaccine`, and `VaccinationGiven` tables) is a vibe-dangerous change per AGENTS.md § Vibe-safety tiers (SQLDelight schema migration). It lands in its own session with test-as-spec discipline, calibration entry at the vibe-dangerous floor of 95, and human review at PR-open and PR-merge time.
- F5b (the first operator-curated rule pack) is a vibe-careful change at the file level (no code change, only a new data file under `shared/src/commonMain/resources/`), but the content correctness sits at the same liability level as the medication-firing path. F5b ships with the source-citation known-answer test described above. The test is the calibration evidence.
- F5c (the scheduler integration that reads the rule pack at notification-scheduling time) is a vibe-dangerous change because it touches `androidApp/src/main/kotlin/app/toebeans/android/notifications/` per the AGENTS.md path table. Test-as-spec discipline applies, and the failing-test-first PR is the first commit of the F5c sequence.
- The four ADR-0019 constraints carry forward into every downstream F5* session. Each PR cites which constraint it satisfies. The boilerplate template lives in the F5a PR body and copies forward to F5b and F5c.

## Rejected alternatives

### Alternative A: skip the rule pack; let the owner type every vaccine reminder

Rejected. The vaccine-reminder feature without a rule pack is a thin layer over the existing `Schedule` and `DoseEvent` model. It does not establish the chronic-condition admin overlay moat that dossier § 3.3 names, and it does not give the AAHA-2025 referral packet a structured source to draw from. The user typing "rabies recheck in 3 years" by hand is the same UX as typing a medication recheck reminder; it does not differentiate the product. Building the rule-pack shape now makes the moat path additive: M2 ships the model, and M4 ships the per-condition overlays on the same machinery.

### Alternative B: pull rule packs from a remote endpoint at notification-scheduling time

Rejected. ADR-0003 § Decision (local-first, no cloud in v1) and AGENTS.md § Posture (no network calls from v1 code) both forbid this design. The no-network fitness function `scripts/test_no_network.sh` would fail the build the moment a network library entered the dependency graph. F3 (the storage and update ADR) addresses the update channel under whatever non-network mechanism the advisory board produces (manual import surface, signed offline distribution, or app-update-only delivery).

### Alternative C: let an LLM generate per-pet vaccine recommendations

Rejected. ADR-0019 § Refusal carve-out and AGENTS.md § Vibe-impossible both name this as forbidden. The LLM would become the source of medical judgment, which is precisely the failure mode ADR-0019 exists to prevent. The rule-based lookup defined in §C is the deterministic alternative.

### Alternative D: store the recurrence cadence as a structured column on `VetVisit` itself instead of in the rule pack

Rejected for the species, age, and region cross-product case. A per-visit recurrence column works for the simple owner-typed case ("recheck in 6 weeks"), but it does not give a clean place for the AAHA-recommended adult booster cadence that differs by age range and by regulatory region. The rule pack centralizes the cross-product; the per-visit field would be either redundant (when the rule pack covers the case) or insufficient (when the case is outside the rule pack's coverage). The v1 path is rule-pack-first. If M2 internal beta surfaces cases the rule pack cannot cover, a per-visit `recurrence` field gets added in a follow-on ADR.

## Followups

These artifacts are proposed by this ADR but NOT created here. Each one is sized for its own session.

- **F5a (vibe-dangerous).** SQLDelight schema migration adding the `VetVisit`, `Vaccine`, and `VaccinationGiven` tables. Per AGENTS.md § Vibe-safety tiers, SQLDelight migrations sit at the vibe-dangerous tier with a confidence floor of 95 and required human review at every change. Test-as-spec discipline applies: the failing test commits first, the implementation follows. Out of scope for F5.
- **F5b (vibe-careful at the file level, content correctness at vibe-dangerous-equivalent stakes).** First operator-curated rule pack covering US dogs and US cats, sourced from AAHA's 2022 Canine Vaccination Guidelines (with 2025 supplemental updates) and the AAFP / AAHA Feline Vaccination Advisory Panel publications. Carries Wei-as-curator provenance metadata per §B. Pre-board content. Ships with a known-answer test pinning each rule entry against its source citation. Out of scope for F5.
- **F5c (vibe-dangerous).** Notification scheduler integration that reads the rule pack at reminder-scheduling time, applies the `(species, age_range, regulatory_region)` filter per pet, and materializes vaccine reminders into the existing notification slot machinery. Touches `androidApp/src/main/kotlin/app/toebeans/android/notifications/`; test-as-spec discipline applies. Out of scope for F5.

## Revisit conditions

This ADR moves to Superseded if any of:

- The advisory-board ADR (F2) chooses a board structure that requires a different rule-pack provenance schema (e.g., per-board-member signing rather than per-pack signing). The §B `provenance` shape is amended in a follow-on ADR.
- M2 internal beta user research surfaces vaccine-reminder cases the rule pack cannot cover (e.g., per-clinic non-standard cadences). The Alternative D rejection is reconsidered and a per-visit `recurrence` field lands in a follow-on ADR.
- The AAHA or AAFP guidelines move to a paywalled or non-redistributable license that the pre-board curation path cannot use. The pre-board interim shrinks or ends, and the F2 board decision becomes a near-term ship blocker for vaccine reminders.
- A new regulatory framework (e.g., FDA pet-app guidance, state veterinary boards' consumer-app rules) constrains how a consumer app can display vaccine recommendations. The framing shapes in §C are amended to match.

## References

- ADR-0003 (`docs/adr/0003-local-first-no-cloud.md`). The local-first posture this ADR inherits for rule-pack distribution.
- ADR-0010 (`docs/adr/0010-sqlite-foreign-keys.md`). The SQLite foreign-key enforcement contract that `VetVisit.pet_id` and `VaccinationGiven.vet_visit_id` depend on.
- ADR-0016 (`docs/adr/0016-plain-json-backup-encryption-deferred.md`). The JSON format choice precedent for the rule pack.
- ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`). The framework this ADR derives from. Four conjunctive constraints (source, delivery, framing, refusal scope) all named inline.
- `AGENTS.md` § Vibe-impossible (line 33). The carve-out boundary that admits rule-based vet-curated content under ADR-0019.
- `AGENTS.md` § Vibe-safety tiers. The SQLDelight migration row that classifies F5a as vibe-dangerous.
- `docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md` § 5 line 137. The `Pet.species` enum (`'dog' | 'cat' at v1`) the `Vaccine.species` enum aligns to.
- `docs/ROADMAP.md` lines 130, 132, 133, 134. The M2 Expansion items this ADR unblocks.
- `research/00-feasibility-dossier.md` § 3.3. Moat candidates ranking; chronic-condition admin overlays (#4) and AAHA-2025-aligned referral packets (#3) derive from this ADR.
- `research/00-feasibility-dossier.md` § 8.2. Slice timeline; Slice 3 (document timeline) attaches to the future `VetVisit.documents` field named in § Decision § A.
- AAHA 2022 Canine Vaccination Guidelines (with 2025 supplemental updates), AAFP / AAHA Feline Vaccination Advisory Panel publications. The professional-guideline sources F5b pulls from. `[verified-via-dossier]` for the AAHA referral-guidelines tailwind; specific vaccination guideline editions are operator-verified by Wei at F5b curation time.
- `docs/issues/v0.1-followups.md`. The granular issue register; F5a, F5b, and F5c entries land there once each gets a tracking line.
