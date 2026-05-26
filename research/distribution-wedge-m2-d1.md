# Distribution wedge M2: D1 pet-owner direct

**Date:** 2026-05-26  
**Author:** tb-m12-wedge-research (operator review pending)  
**Status:** DRAFT for M1.2 internal beta  
**Locked decision:** `research/decisions/2026-05-26-m12-beta-gates.md` § Distribution wedge (Q7)

**Scope:** How toebeans reaches its first real users under wedge **D1: pet-owner direct** (owner downloads from Play Store internal testing; no clinic, insurer, or rescue channel). This doc is the M2 work item named in ROADMAP M1.2 definition-of-done.

**Sources (do not invent beyond these):**

- `research/00-feasibility-dossier.md`: market, competitive map, distribution options §4.6, adherence evidence §7, posture §8.1–8.2
- `research/decisions/2026-05-26-m12-beta-gates.md`: locked Q7–Q10 answers
- `docs/ROADMAP.md`: M1.2 internal beta gates
- `docs/soak-test-protocol.md`: 30-day tester protocol
- `docs/play-store-internal-testing-walkthrough.md`: Play Console execution order
- `docs/adr/0003-local-first-no-cloud.md`: v1 network posture

---

## 1. Wedge choice and positioning

### 1.1 Why D1 (direct pet-owner)

The feasibility dossier §4.6 names four distribution paths. **D1 maps to option 1: direct App Store growth** (paid acquisition + ASO; dossier cites **$15–60 CAC** for pet apps as `[inferred]`).

| Wedge | Dossier ref | Why not for M1.2 beta |
|---|---|---|
| **D1: pet-owner direct** | §4.6 option 1 | **Selected.** Zero channel setup; validates reminders + ease-of-use on real hardware before any B2B2C dependency. |
| Clinic partnerships | §4.6 option 2 | Slow; requires AAHA-aligned packet surface not in v1. PetDesk owns clinic rail (~7M users / 1,800+ clinics, dossier §3.2). |
| Insurer co-marketing | §4.6 option 3 | Series-A-scale; claim-packet thesis partially obsoleted by direct vet-pay (dossier §4.4). |
| Rescue/foster licensing | §4.6 option 4 | Near-zero CAC `[normative]` but adds org onboarding; defer until product retention is proven. |

**Operator lock (2026-05-26):** Victoria, operator, spouse as core testers; additional testers via social callout. Success signal: **reminders work; ease of use high** (not install count, not revenue).

### 1.2 Positioning (inherits dossier §8.1)

**Headline:** *Your pet's admin layer.*

| We are | We are not |
|---|---|
| Owner-controlled medication reminders + dose logging | A vet, symptom checker, or "AI diagnostician" |
| Local-first; data stays on device | Cloud sync, accounts, or training on user data |
| Trust + portability axis (opposite of clinic-captive apps, dossier §3.3 / F3) | Clinic-mediated distribution (PetDesk model) |

**Anti-positioning (explicit refusal, dossier §8.1):** no AI symptom checker, no diagnostic suggestions, no treatment recommendations, no "AI vet" framing in store copy or UI.

**D1-specific messaging for beta invite copy:**

> toebeans helps you remember your pet's medications and log doses. Nothing leaves your phone unless you export it.

Do not promise caregiver sharing, document timeline, claim packets, or travel/DST intelligence in beta outreach; those are post-validation surfaces (dossier §8.2 Slices 2–6).

---

## 2. Beta tester script

Hand testers **`docs/soak-test-protocol.md`** plus this section. Total onboarding: ~15 minutes day 0, ~30 seconds/day thereafter.

### 2.1 Cohort

| Tester | Role | Device expectation |
|---|---|---|
| Victoria | Primary external tester | Real daily-driver Android; document OEM if not Pixel |
| Operator (Wei) | Dogfood + crash-log exercise | Pixel a-series preferred (soak protocol baseline) |
| Spouse | Second household, multi-pet realism if applicable | Same as above |
| Social callout (optional) | 1–2 additional pet owners | Must accept Play internal-testing invite; same protocol |

**Minimum cohort for adoption read:** ≥3 testers (locked in `2026-05-26-m12-beta-gates.md` § Adoption metric Q8).

### 2.2 Day-0 onboarding script (read aloud / paste into invite)

1. **Accept** the Play Store internal-testing invite (Gmail on the phone you'll actually carry).
2. **Install** toebeans from the internal-testing link. Confirm version matches operator's release note.
3. **Do not** load demo data unless you want a sandbox. Add **one real pet** and **one real medication** with a schedule that matters this month.
4. **Confirm** notifications are enabled for toebeans at OS level. Do **not** disable battery optimization yet (we want default-OEM behavior).
5. **Run** one dose cycle today: wait for notification → tap **Given** / **Skipped** / **Snooze** as appropriate.
6. **Bookmark** the daily log template (operator provides: spreadsheet or shared doc). One row per day: date, doses expected, doses logged, any missed alarm.
7. **Know the escape hatch:** Settings → Export crash log. Send file to operator if anything feels broken; "no log produced" is also useful data.

### 2.3 Weekly check-in prompts (operator → testers)

Send every 7 days:

- Did any scheduled dose fire **more than 5 minutes late** or **not at all** without you noticing first?
- On a 1–5 scale, how annoying is adding/editing a medication? (Ease-of-use signal)
- Are you still opening the app in week 2? (Retention signal: see section 5)
- Anything you wished the app did that you tried to do anyway?

### 2.4 Success criteria (beta, not M2 gate)

From locked decision Q7–Q8:

| Signal | Pass | Fail |
|---|---|---|
| Reminders fire | ≥95% of expected doses either notify on time (±5 min) or are explicitly skipped/snoozed with user awareness | Silent misses: user only discovers dose via manual app check |
| Ease of use | Median tester rating ≥4/5 on add/edit flow by day 14 | Repeated "I gave up entering the med" reports |
| Crash log path | At least one tester successfully exports a crash log (proves ADR-0009 field path) | Export broken or undiscoverable |
| Retention (day 14) | **Alpha posture:** at least one tester still logging doses past day 14 | **Surface issues, fix forward**: not a hard M2 blocker per operator lock; informs whether to proceed to M1.5/M2 feature work |

**Collection method:** written updates from testers; optional shared form `[TBD template]`. **No in-app analytics** (see §3).

---

## 3. v1-only feature boundary

Beta ships **M1 slice only** (dossier §8.2 Slice 1). Wedge D1 must not require network, accounts, or analytics.

### 3.1 In scope for D1 beta

| Feature | Notes |
|---|---|
| Pet profile (manual entry) | Real pets only for soak |
| Add medication + dose schedule | Basic path must work (**beta blocker** (Q9) |
| Reminder notifications + dose logging | Core job-to-be-done; adherence problem validated in dossier section 7 (~50% owners struggle with >3 daily meds, Sciencedirect 2021) |
| Today / Home surface | Primary daily interaction |
| Settings: theme, crash log export | Crash export is M1.2 DoD |
| Local SQLite storage | ADR-0003 posture |
| First-launch empty state + optional demo load | Shipped M1.2 |

### 3.2 Explicitly out of scope (do not promise testers)

| Surface | Why deferred | Reference |
|---|---|---|
| **Cloud sync / accounts / backup to server** | v1 local-first; no network in shipping path | ADR-0003; dossier §8.2 Slice 1 |
| **Analytics, crash reporting SDKs, advertising IDs** | MVP design: "No analytics. No crash reporting in v1" | `docs/superpowers/specs/2026-05-14-toebeans-mvp-design.md` |
| **Anchor-mode prompt / travel-DST intelligence** | M1.5 milestone; blocked on ADR-0007 G1–G3 | ROADMAP M1.5; Q9: do not block beta on anchor enum |
| Caregiver sharing (QR/deep link) | Dossier Slice 2 | §8.2 |
| Document timeline + OCR | Slices 3–4 | §8.2 |
| Claim packet / insurer adapters | Slice 6; direct vet-pay narrows window (§4.4) | §8.2 |
| Vet visit / vaccination rule packs | M2+ expansion | ADR-0019, ADR-0020 |
| Subscription / Play Billing | Post-validation revenue gate | ROADMAP M6 area |
| Clinic or insurer distribution mechanics | Other wedges (§4.6 options 2–3) | This doc §1.1 |

### 3.3 v1 scope check (closes Q7 `[needs_human_review]`)

**Verdict:** D1 pet-owner direct beta requires **no wedge feature that needs network or cloud**. Invite copy, Play Data safety form, and privacy policy must state local-only / no collection, consistent with internal-testing walkthrough minimum privacy copy.

---

## 4. Play Console timing

**Status (2026-05-26):** Not started (Q10).

### 4.1 Start trigger

Begin Play Console walkthrough when **all** are true:

1. **Add Medication smoke path** passes on device (`tb-add-med-smoke` checklist complete).
2. **Release AAB** builds green from `main` (`./gradlew :androidApp:bundleRelease`).
3. **Privacy policy URL** live (GitHub Pages acceptable per walkthrough).
4. **Operator bandwidth:** 3–5 hours wall-clock split across two sessions, plus **1–3 day ID verification wait** (walkthrough estimate).

Parallel work allowed: prose/docs (this file, license ADR) do **not** block starting Phase 0 prerequisites (icon, screenshots, keystore planning).

### 4.2 Sequencing relative to soak

| Phase | When | Outcome |
|---|---|---|
| Prerequisites (Phase 0) | Before opening Console | Artifacts table in walkthrough complete |
| Developer registration + ID verify | Week soak prep | $25; 1–3 day wait |
| Internal testing track + first upload | Before day 0 of any tester's soak | Invite links for Victoria, spouse, social testers |
| Soak window | Day 0 → day 30 per tester | `docs/soak-test-protocol.md` |
| Day-14 retention read | Alpha posture | Inform fix-forward; not hard M2 gate |
| Day-30 structured report | M1.2 evidence bundle | Alarm reliability + ease-of-use summary |

**Do not** open public production track until license posture (AGPL-3.0 locked 2026-05-26) and legal review of store compliance copy are complete.

---

## 5. What to learn in the 30-day soak

The soak exists to answer **product and reliability questions** for D1, not to optimize acquisition metrics we cannot measure without analytics.

### 5.1 Primary learning goals

| Question | Evidence | Dossier / roadmap anchor |
|---|---|---|
| Do reminders fire reliably on real OEMs? | Soak failure definition in soak protocol; crash logs | Technical feasibility §5; F1 partial mitigation |
| Is add-medication easy enough for non-developers? | Weekly 1–5 ease rating; qualitative quotes | Adherence section 7: reminders only valuable if logging is low-friction |
| Do testers keep using the app past day 14? | Daily log + weekly snapshot | ROADMAP M1.2 adoption metric; **alpha posture** per Q8 |
| Does default battery optimization kill alarms? | OEM + optimization notes from testers | Known Android/OEM risk, dossier §5 |
| Does local-first posture build trust? | Tester interview: "would you add real med data?" | Moat candidate #5, dossier §3.3 |

### 5.2 Secondary learning goals (inform M1.5 / M2, not beta blockers)

- DST edge cases observed (feeds ADR-0007 priority; **do not fix in beta** unless safety-critical).
- Multi-pet household friction (informs Slice 2 caregiver sharing).
- DND / notification channel behavior on non-Pixel devices.
- Wording that resonates in social callout (qualitative only; CAC remains `[unknown]`, dossier §6.1).

### 5.3 What we are **not** learning in this soak

| Topic | Why deferred |
|---|---|
| Paid acquisition efficiency | CAC **unknown** (dossier §6.1); no ad spend in M1.2 |
| Conversion to paid tier | No subscription in v1 |
| Clinic or insurer channel fit | Wrong wedge |
| Anchor-mode / travel correctness | M1.5 scope |
| OCR / document extraction quality | Slice 4 decision gate |

### 5.4 Outcomes and next gates

After day 30, operator synthesizes:

1. **Go / fix-forward / pause** on M1.5 travel work (based on alarm reliability + retention signal, not anchor-mode completeness).
2. **M2 public-ship prerequisites:** license ADR published, distribution wedge doc merged (this file), at least one tester completed 30-day soak without trust loss (ROADMAP M1.2 DoD).
3. **D1 continuation for M2:** if retention and ease-of-use pass, invest in ASO + organic social (still direct wedge); do **not** open clinic or insurer channels until a separate wedge decision.

---

## 6. Risks and falsifiers (D1-specific)

Pulled from dossier §6; scoped to direct-owner beta.

| Risk | D1 implication | Mitigation in this wedge |
|---|---|---|
| **F1** Reminders commoditized; no retention | Social callout may churn fast | Measure day-14 continuation (alpha); plan Slice 2+ before expecting defensibility |
| **F3** Covetrus/PetDesk ships same MVP | Low near-term for owner-portable positioning | Messaging emphasizes owner control vs clinic-captive |
| Distribution model score 6/10 (dossier §9) | D1 beta does not prove CAC | Treat soak as product validation only; CAC experiments wait for M2+ budget |

---

## 7. M2 work items spawned by this wedge

When M1.2 DoD is met and this doc is merged:

1. **Organic social playbook**: 3 post templates (problem, local-first, invite-only beta graduate story); no paid spend requirement yet.
2. **ASO baseline**: store listing keywords aligned to "pet medication reminder" (competitive density: fragmented D2C, dossier §3.2); screenshot refresh after style-lab sign-off.
3. **Tester referral loop**: ask satisfied soak testers for one intro each (still D1; not clinic channel).
4. **Revisit wedge decision**: if day-14 retention fails across cohort, re-read §4.6 options 2–4 before scaling D1 spend.

---

## 8. Document map

| Artifact | Role |
|---|---|
| This file | M2 distribution wedge definition for D1 |
| `docs/soak-test-protocol.md` | Tester execution |
| `docs/play-store-internal-testing-walkthrough.md` | Operator Play Console steps |
| `research/decisions/2026-05-26-m12-beta-gates.md` | Locked Q7–Q10 |
| `research/00-feasibility-dossier.md` | Evidence base. Do not cite metrics not present there |

---

## 9. Open items (operator)

1. Shared form template for weekly tester updates `[TBD]`.
2. Social callout copy + date (after internal track live).
3. Legal review of store listing compliance claims (AGPL + local-only).
4. Equity vs sale orientation (dossier section 11 Q1): deferred; does not block D1 beta.
