# toebeans full-launch checklist

> Note (2026-11-22): The agent contract files were removed from this public repository. References in this document to those files mean the local working copies kept in a contributor's checkout.

Point first: this doc separates what machines can still do from what only the operator can unblock, and it defines what an honest claim of "full feature launch" requires.

Basis: ROADMAP.md (Milestone 1.2, updated 2026-11-11), ADR-0020 (vet visit, Accepted), ADR-0019 (vet content framework), ADR-0007 (timezone and travel), docs/issues/v0.1-followups.md, and the open pull requests on 2026-11-15 at main 20ce2b2. The decisions live in those files; this doc only triages them.

## Standing rule

"Machine-doable" means the design decision already exists in an accepted ADR, so a machine agent can write the failing test and the code. It never means human-free: vibe-dangerous changes keep their AGENTS.md review gate, and schema work keeps test-as-spec order (failing test PR first, a human reviews the test, then the implementation lands). Two slices below sit on the roadmap's "Not this run" list, so starting them also needs an operator roadmap edit; each is marked.

## Machine-doable next slices, ranked by user-visible value

### 1. Dose history survives schedule delete, after the PR #155 call

- What: make DoseEvent rows survive `scheduleRepository.delete`, or formally reject that behavior and remove the red test.
- Why machine-eligible: the design tension is fully documented. The DoseEvent.sq header names SET NULL as the intended policy, while the table declares `ON DELETE CASCADE`. PR #155 pins the desired behavior in a deliberately red test.
- Gate: starts only after the operator picks a side on PR #155 (operator list item 3, which also carries the demo-clear cascade interaction).
- Files: DoseEvent.sq plus migration 4, the DoseEvent model and KDoc, the in-memory fake, and contract tests. If rejected: the test file only.
- Tier: vibe-dangerous if yes (schema migration row in AGENTS.md, floor 95, human review every change, red-test PR first). Vibe-safe if no (test-only change).
- Size: half to one session.

### 2. ADR-0020 vet-visit and vaccine feature

The biggest accepted, unimplemented feature. Slice order:

1. **F4 style guide first** (ADR-0019 followup): attribution wording rules for reminder text, in docs. Vibe-safe, about an hour, and F5c applies these rules at render time.
2. **F5a schema**: SQLDelight migration adding VetVisit, Vaccine, and VaccinationGiven per ADR-0020 §A and §B. Tier: vibe-dangerous (schema migration, floor 95, human review at every change). Files: new .sq tables plus 4.sqm, repositories, contract tests. Red-test PR first per test-as-spec. Size: one to two sessions across two machine passes.
3. **F5b rule pack**: machine scaffolds the JSON pack format, version field, provenance schema, and the known-answer test harness under shared/src/commonMain/resources/. The content entries are Wei's curation against the AAHA 2022 canine and AAFP/AAHA feline guidelines, with paired review, because Wei is the named pre-board curator while ADR-0019 F2 is pending. Tier: vibe-careful at the file level, dangerous-equivalent content stakes. Size: one machine session plus operator curation hours.
4. **F5c scheduler integration**: read the pack at reminder-scheduling time, apply the species, age, and region filter per pet, materialize vaccine reminders into the existing notification slots. Tier: vibe-dangerous (touches androidApp notifications/, the alarm path). Red test first. Size: one to two sessions.
5. **Record-visit UI**: past visits, future visits, and vaccination logging screens. Tier: vibe-safe (Compose screens, no scheduler logic, CI gates only). Size: one to two sessions plus manual QA cases.

Roadmap note: "Vet-visit tables stay in the vet-visit ADR-0020" is on the M1.2 "Not this run" list. Starting F5a needs the operator to lift that deferral.

### 3. ADR-0007 timezone surface, remaining pieces

Already shipped: the AnchorMode enum with all three values, the `anchor_mode` column defaulting FOLLOW_PHONE, the ELAPSED_INTERVAL calculator path, DstWarning with DST_SKIP and DST_DUPLICATE_RESOLVED surfaced on reminder rows, SchedulePhaseDstRulesTest, and the anchor-mode picker in Schedule Create.

Remaining and independent of G1 and G3, so machine-eligible:

- **Pet.homeTimezone plus the STAY_HOME_TZ caller policy**: optional column on Pet, and materialization passes the pet's home timezone when set. G1 gates drug-list copy and G3 gates prompt-copy research; neither gates this field. Tier: vibe-dangerous (Pet.sq migration), red-test first. Size: one schema session plus one caller and UI session.
- **TimezoneChangeReceiver**: listens for ACTION_TIMEZONE_CHANGED and ACTION_TIME_CHANGED, cancels and re-registers the 72-hour alarm horizon. Needs no new permission, so the manifest allowlist is untouched, but alarm re-registration is the reminder-firing path. Tier: vibe-dangerous (notifications surface, ADR-0005 posture). Size: one session including the Robolectric test.
- **Onboarding screen**: at first pet creation, explain that reminders follow the phone's timezone. Tier: vibe-safe. Size: half a session.

Gated on humans: G1 (Plumb's 9th edition page citations) before any TIGHT-drug explainer copy ships; G3 (n=3-5 prompt research) once M1.2 testers exist; the one-week G2 emulator dogfood sign-off.

Roadmap note: "ADR-0007 G1 and G3 stay in docs/issues/v0.1-followups.md" is on "Not this run"; the field, receiver, and onboarding work above needs the same lift.

### 4. Beta-path support docs

Manual QA cases for any new flow, and a soak-report template for docs/soak-reports/. Tier: vibe-safe. Size: small. This is the only machine slice the current roadmap fully sanctions this run.

## Operator one-sitting list

Each item names its exact unblocking action.

1. **GitHub Actions billing.** All three workflows (ci, pr-body-lint, trainer gate rerun) report startup_failure with zero jobs, on main and on PR #154. Action: fix repo Actions billing or enablement in Settings, then re-run the main workflow. Until then CI evidence is local-only and the trainer PR gate cannot run remotely.
2. **F-Droid signed release.** Action: build and publish the signed release, attach it to the GitHub release for Obtainium, add it to the small F-Droid repo, and send both to Victoria, the operator, and the spouse. This starts the tester installs, the one-reminder confirmations, and the 30-day soak and day-14 clocks. No machine work moves those clocks. The Play Store walkthrough stays unused unless a tester cannot install from the F-Droid path.
3. **PR #155 product call.** Does dose history survive schedule delete? Action: decide on the PR. Yes means slice 1 (SET NULL migration). No means delete or invert the red test. Cascade interaction with #154: demo-clear relies on foreign-key cascades to remove demo dose rows; under SET NULL, schedule delete alone stops removing dose rows, and demo doses still fall to the medication_id cascade (DoseEvent.medication_id is NOT NULL with ON DELETE CASCADE), so the demo-clear end state holds. The #155 reviewer should confirm clearDemoData's delete order keeps medication deletes in the chain. Note the PR merges only together with its resolution; its suite is red by design (211 tests, exactly 1 failure).
4. **Merge PR #154** (remove demo data in Settings). Locally verified green per its body; CI cannot second it while Actions is down.
5. **ADR-0021 acceptance.** No ADR-0021 exists on main or any branch as of 2026-11-15. If the parallel research track lands one, review and accept it before any work it names starts.
6. **Advisory board decision** (ADR-0019 F2, dossier section 11 Q3). Stand up a board or formally decline. It gates curated packs, chronic-condition overlays, and post-board rule-pack signing. Pre-board, Wei stays the named curator for the F5b pack.
7. **ADR-0007 G1.** Validate the TIGHT drug list against Plumb's 9th edition with page citations, before any TIGHT-drug copy ships.
8. **ADR-0007 G3 and G2 sign-off.** Run the prompt-copy research once M1.2 testers exist; soak timezone observations feed it. Do the one-week emulator DST dogfood sign-off.
9. **M1.2 closure.** Read the day-30 reports, create docs/soak-reports/v0.1.0-internal-1.md (the directory does not exist yet), and decide the day-14 retention gate and milestone close.
10. **Roadmap deferral lift.** Vet-visit tables and the timezone surface are "Not this run". Lifting that deferral is the operator edit that starts slices 2 and 3.

## Definition of done for full feature launch

An honest "full feature launch" claim requires all of these:

- **M1.2 closed** per the soak protocol: at least one tester reaches day 30 without trust loss, the day-14 retention check passed, soak findings are aggregated in docs/soak-reports/v0.1.0-internal-1.md, crash-log export is field-proven by at least one tester, and the silently-missed count is zero or its P0 fix shipped.
- **Distribution live**: the signed F-Droid release is published and every named tester confirmed one reminder on device.
- **Feature honesty**: the accepted ADR-0020 feature is either shipped through its slice order (F4, F5a, F5b, F5c, UI) or the roadmap states launch ships without it. An accepted, unimplemented feature with no descope note breaks the claim.
- **Timezone surface live**: homeTimezone with the STAY_HOME_TZ policy, TIMEZONE_CHANGED re-materialization, and the onboarding screen shipped; G1 citations done before TIGHT-drug copy; G3 research done or scheduled with soak evidence.
- **CI green on GitHub Actions**, so the trainer PR gate, pr-body-lint, and the fitness functions run remotely and not only on a laptop.
- **Fitness posture unchanged**: no-network, no-analytics, permission allowlist, scheduler purity, scheduler coverage, and no-PII-in-crash-log all pass on main.
