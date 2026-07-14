# toebeans Wave 2 Kickoff Prompt

**DO NOT START WORK UNTIL THE OPERATOR EXPLICITLY SAYS "GO" OR "BEGIN WAVE 2"**

This prompt prepares you to execute the next wave of toebeans development. Read it thoroughly, understand the context and priorities, but wait for explicit operator authorization before writing any code or making any changes.

---

## Context & Current State

**Repository:** https://github.com/weijia-89/toebeans  
**Current Branch:** `main` (clean, all CI green)  
**Last Shipped:** Wave 1 (Milestone 1) - MVP with SQLDelight persistence, notifications, backup/export

### What Just Shipped (Wave 1 / M1)

✅ **Core Persistence Layer:**
- SqlDelight implementations: `PetRepository`, `MedicationRepository`, `ScheduleRepository`, `DoseEventRepository`
- DI swap from Fake → SqlDelight repositories with ADR-0010 FK callback
- All repository contract tests passing

✅ **Notification System:**
- `DoseAlarmReceiver` with real SQLDelight DB lookup
- `BootReceiver` + 72h-horizon rehydration on BOOT_COMPLETED
- PendingIntent collision mitigation via `RequestCodeAllocator`
- Notification medication enrichment (shows med name + pet name)

✅ **Compose UI Surfaces:**
- Reminder List screen (bottom nav tab between Today and Pets)
- Schedule Detail screen with delete affordance
- Schedule Create with inline calculator error UI (pre-flight validation)
- Midnight-mode UX warning (doses between 00:00-06:00)
- Backup export/import UI (v1 plain JSON per ADR-0016)

✅ **Safety & Quality:**
- `StaleEventGuard` for crash-on-render-of-stale-events
- 6 fitness functions (no-network, no-analytics, scheduler-purity, permission-allowlist, no-PII-in-crash-log, no-duplicate-private-test-class-names, no-em-dash-in-docs)
- Vibe-dangerous pre-commit hook + calibration log (`.codeit/calibration.jsonl`)
- Trainer PR review gate (CI-enforced code review comments)
- Kover 85% line-coverage gate on scheduler + backup

✅ **Tooling & CI:**
- Dependabot exemptions for automated dependency updates
- PR body lint (requires ## Summary + ## Test plan)
- Macrobenchmark module (`:macrobench`) with startup benchmarks

### Recent CI Fixes (Just Merged)

PR #91: Missed dose sweeper + notification enrichment  
PR #92: Dependabot gradle update (merged, all 12 CI checks green)  
PR #93: Medication name search (Item 3 from roadmap) - MERGED

**All CI gates are currently green on main.**

---

## Next Priority: Wave 2 Items

According to `docs/ROADMAP.md` and `docs/issues/v0.1-followups.md`, here's what's next:

### **Tier 1: Anchor Mode & Timezone (ADR-0007 closure)**

**Source:** ADR-0007 (Accepted with G1-G3 downgrade gates), v0.1-followups #2  
**Milestone:** 1.5 (gated on ADR-0007 verification gates)  
**Vibe Tier:** **vibe-dangerous** (scheduler surface + timezone logic)

**What's Missing:**
1. **G1: Plumb's drug-list validation** - Compare toebeans dose calculations against Plumb's Veterinary Drug Manual reference data
2. **G2: DST Sprinkler Test** - `SchedulePhaseDstRulesTest` (NOT YET CREATED) to pin calculator behavior across DST transitions
3. **G3: User research (n=3-5)** on timezone onboarding prompt copy

**Implementation Work:**
- Add `homeTimezone: String?` to `Pet` entity (migration + schema update)
- Add `travelMode: TravelMode` enum to `Schedule` entity (`FOLLOW_PHONE` | `STAY_HOME_TZ` | `ELAPSED_INTERVAL`)
- Update `ScheduleCalculator.computeScheduledDoses` to respect these settings
- First-launch onboarding screen explaining timezone behavior
- Pet profile UI: timezone selector + travel mode toggle
- Integration test simulating `ACTION_TIMEZONE_CHANGED` broadcast

**Test-as-Spec Requirements:**
- `SchedulePhaseDstRulesTest` with cases for:
  - Spring-forward gap (2:30 AM → 3:00 AM skip)
  - Fall-back duplicate (1:30 AM occurs twice)
  - `DST_SKIP` warning surface on `ScheduledDose`
  - `DST_DUPLICATE_RESOLVED` warning surface
- Must cross-check against Plumb's examples (if available) or worked examples from ADR-0007

**Why This Blocks Further Work:**
Without timezone/travel-mode, the app cannot be used by pet owners who travel or have pets in different timezones. This is a core M1.5 requirement before broader beta.

---

### **Tier 2: Dose Unit Picker (Item 4 from PR #93 description)**

**Source:** PR #93 follow-ups, medication management UX  
**Milestone:** 1.5  
**Vibe Tier:** **vibe-safe** (UI-only, no scheduler logic)

**What:** Add unit picker (mg, mL, tablets, drops, etc.) to medication/phase creation  
**Acceptance:**
- PhaseEditorCard: dropdown for dose amount units
- Validation: unit must be selected before save
- Display: "5 mg" or "2 tablets" in reminder list / notifications

**Note:** This is mechanical UI work, good for warming up before tackling timezone.

---

### **Tier 3: Schedule Scroll Macrobenchmark**

**Source:** ADR-0008 (perf budget), deferred from M1  
**Milestone:** 1.5  
**Vibe Tier:** **vibe-careful** (test-only, no prod code)

**What:** Add Compose UI test for Reminder List scroll performance  
**Acceptance:**
- `ReminderListScrollBenchmark.kt` in `:macrobench`
- Metrics: 99th percentile frame time, total scroll duration
- Baseline: run on Pixel a-series device
- Budget: <16ms frame time for smooth scroll

**Dependency:** Requires `androidx.compose.ui:ui-test-junit4-android` (vibe-dangerous dep add, needs human approval)

---

## Operating Constraints

### Trainer PR Review Gate (MECHANICAL, CI-ENFORCED)

**Every PR must have a canonical trainer review comment:**

```markdown
<!-- trainer-codereview-toebeans-{branch-name-with-dashes} -->
<!-- head={7-char-sha} verdict=APPROVE round={N} -->

## Code Review – Round {N} (...)

### Bug Inventory
| Severity | File | Finding | Status |
|---|---|---|---|

### ...

### Trainer notes

**Program notes:**
- ...

**Your form:**
- Confidence: X/20
- ...

**Next session:**
- ...
```

**Script:** `bash scripts/trainer_pr_review_post.sh <pr_num> <verdict> <round> review.md`  
**Post BEFORE pushing** so `head=` matches first CI run.

### Vibe-Dangerous Protocol

For any change to **scheduler/**, **backup/**, **notifications/**, **SQLDelight schema/**, or **Gradle dependencies**:

1. **Write failing test FIRST** (commit separately)
2. **Get human review** of test signature
3. **Implement**
4. **Run mutation tests** (pitest) on scheduler/backup surfaces
5. **Score** per `code-helper` §5 (9 components, weighted)
6. **Log** to `.codeit/calibration.jsonl`
7. **Score must meet floor:** ≥95 for vibe-dangerous

### Confidence Score Components

| # | Component | Weight | Tier Floor |
|---|---|---|---|
| 1 | Code-read depth | 15 | vibe-dangerous: 95 |
| 2 | Test verification | 20 | vibe-safe: 80 |
| 3 | Hallucination check | 15 | vibe-careful: 90 |
| 4 | Bug-class coverage | 12 | |
| 5 | Adversarial pass | 10 | |
| 6 | Reversibility | 8 | |
| 7 | Doc accuracy | 8 | |
| 8 | Blast radius | 7 | |
| 9 | Threat model | 5 | |

**Total:** 100 points

---

## Operator Authorization Required

**DO NOT BEGIN WORK** until the operator explicitly authorizes one of the following:

**Option A: Full Wave 2 Launch**
> "GO - begin Wave 2 with Tier 1 (timezone/anchor mode)"

**Option B: Warmup Task**
> "GO - start with Tier 2 (dose unit picker) as warmup"

**Option C: Incremental**
> "GO - create SchedulePhaseDstRulesTest first (G2 closure)"

The operator may also specify:
- Which milestone to target (M1.5 vs M2)
- Whether to batch items or do one commit per item
- Any constraints on dep additions or ADR amendments

---

## Your First Actions (After Authorization)

Once the operator says "GO":

1. **Load skills:**
   ```
   skill_view(name='trainer')
   skill_view(name='form-check')
   skill_view(name='code-quality-guardrails')
   ```

2. **Read load-bearing docs:**
   - `docs/ROADMAP.md` (full)
   - `docs/adr/0007-timezone-travel-mode.md` (if exists)
   - `docs/issues/v0.1-followups.md` (relevant items)
   - `AGENTS.md` (test-as-spec rules, vibe tiers)

3. **Create investigation scratchpad:**
   ```markdown
   # Wave 2 Investigation - {DATE}
   
   ## Authorized By
   Operator: {name}, {timestamp}
   
   ## Tier 1: Timezone/Anchor Mode
   
   ### Current State
   - ADR-0007 status: Accepted with G1-G3 open
   - G1 (Plumb's validation): NOT STARTED
   - G2 (DST test): NOT STARTED - SchedulePhaseDstRulesTest MISSING
   - G3 (user research): NOT STARTED
   
   ### Files to Read
   - shared/src/commonMain/kotlin/app/toebeans/core/scheduler/...
   - Pet.kt, Schedule.kt (entities)
   - ScheduleCalculator.kt
   - TimeZone.currentSystemDefault() call sites
   ```

4. **Begin systematic investigation:**
   - Read all scheduler files end-to-end
   - Trace `TimeZone.currentSystemDefault()` call sites
   - Review ADR-0007 references + Plumb's examples
   - Design `SchedulePhaseDstRulesTest` signature

5. **Present findings to operator:**
   - What's the smallest test-as-spec commit?
   - What are the 3 weakest assumptions?
   - What's the confidence score projection?

---

## Important Patterns

### Branch Naming
- `feature/timezone-anchor-mode` (descriptive, dashes not slashes)
- `test/d1-dst-rules-failing-tests` (test-as-spec first)

### Commit Structure
- Test-as-spec: failing test ONLY (get human review)
- Implementation: code + tests (must turn tests green)
- Docs: ADR amendments + ROADMAP updates
- **Never batch** vibe-dangerous changes with unrelated work

### CI Gates (12 Total)
1. PR description format
2. Gradle build + shared tests
3. ktlint (androidApp + shared)
4. detekt (androidApp)
5. Kover ≥85% (scheduler + backup)
6. Fitness functions (6 scripts)
7. Secrets scan (gitleaks)
8. Dependency review
9. Trainer PR review gate
10. Vibe-dangerous gate (--no-verify defense)
11. CodeQL
12. submit-gradle

**All must pass before merge.**

### Verification Before Completion

Per `superpowers:verification-before-completion`:

**Never claim "done" without:**
- Scope ID (e.g., "Wave 2 Tier 1 G2")
- Evidence matrix (Git, disk, operator steps)
- PASS/FAIL checklist for each row
- CI green (all 12 gates)
- Calibration entry logged (if vibe-dangerous)

---

## Reference: Recent Work Patterns

**Example: Medication Name Search (PR #93)**
1. Autonomous code review loops (3 passes, 100% coverage)
2. Fixed 6 bugs found during review
3. Wrote 9 unit tests
4. Passed all CI gates (including trainer + vibe-dangerous)
5. Added calibration entry (score 96/100)

**Example: Dependabot CI Fixes**
1. Discovered exemption scripts not firing in CI
2. Tried script-level fix → didn't work (causes unclear, possibly checkout timing)
3. Added workflow-level exemption (GitHub Actions `if:` conditional)
4. Verified: all 12 gates passing for Dependabot PRs

**Lesson:** When in doubt, prefer explicit workflow-level conditionals over script logic for CI exemptions.

---

## Ready State

You are now prepared to begin Wave 2 work **upon operator authorization**.

**Current state summary:**
- ✅ Main branch clean, CI green
- ✅ All M1 items shipped (SQLDelight, notifications, backup, UI surfaces)
- ✅ Tooling stabilized (Dependabot exemptions working, trainer gate robust)
- ✅ Next priority clear: ADR-0007 closure (timezone/anchor mode)

**Awaiting operator command:** "GO" + scope authorization.