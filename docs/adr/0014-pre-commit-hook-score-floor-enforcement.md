# ADR-0014: Should the pre-commit hook enforce calibration score floor?

Date: 2026-05-17
Status: **Proposed — decision deferred to Wei after M1.2**
Deciders: Wei Jia

## Context

The 2026-05-17 adversarial review surfaced a process gap: the pre-commit hook (`scripts/git-hooks/pre-commit`) currently enforces that any vibe-dangerous file change is paired with a new line in `.codeit/calibration.jsonl`. **It does not enforce that the score in the new entry meets the tier floor.**

Evidence in the calibration log: 6 vibe-dangerous entries in the last 14 days shipped with scores 86, 86, 88, 89, 89, 91 — all below the 95 floor specified in AGENTS.md. Each was honestly noted as "below floor" with a falsifier-based rationale. The discipline `AGENTS.md § When confidence < tier-floor` ("Re-investigate. Update the plan. Re-score. After 2 iterations without crossing the floor, escalate with a gap report.") is **honor-system**: nothing in the toolchain blocks the commit when score < floor.

One of those sub-floor commits — `c7f5eff` ("route notification ids through RequestCodeAllocator", score 91) — shipped with **4 Kotlin compile errors** in the touched file. CI would presumably have caught it, but the broken state persisted on origin/main for ~14 hours until the next session noticed. The reviewer's verdict: the floor exists to prevent exactly this; honor-system enforcement is insufficient against tired-author drift.

## Decision

**Deferred until after M1.2 internal beta retention gate clears.**

The decision is whether to extend the pre-commit hook with a mechanical score-floor check:

```bash
# Pseudocode for the proposed extension
SCORE=$(echo "$ADDED" | python3 -c "import sys, json; print(json.loads(sys.stdin.read().lstrip('+')).get('score', 0))")
TIER=$(echo "$ADDED" | python3 -c "import sys, json; print(json.loads(sys.stdin.read().lstrip('+')).get('tier', ''))")
case "$TIER" in
    vibe-dangerous) FLOOR=95 ;;
    vibe-careful)   FLOOR=90 ;;
    vibe-safe)      FLOOR=80 ;;
    *)              FLOOR=0  ;;
esac
if (( SCORE < FLOOR )); then
    echo "BLOCKED — calibration entry scored $SCORE, below $TIER floor $FLOOR."
    echo "Per AGENTS.md, re-investigate the lowest-scoring component before committing."
    exit 1
fi
```

The trade-off has measurable cost on both sides. Wei should decide after M1.2, when there is real-user signal on whether the existing discipline cadence is producing field-quality results.

## Trade-off analysis

### Arguments for tightening (mechanical block)

1. **Would have prevented `c7f5eff`.** That commit honestly self-scored 91 against a 95 floor. A mechanical block would have refused the commit until Wei either re-investigated (likely caught the compile errors) or escalated.
2. **Removes "I'll catch it in CI" rationalization.** Several existing sub-floor entries cite "no JDK17 locally; CI is the verification gate" as the test_verif gap. CI did not catch `c7f5eff`'s broken-on-main state in any visible-to-Wei way (open question whether CI even ran). The local hook is the only gate Wei is guaranteed to observe.
3. **Cheap to add.** ~10 lines in the existing hook. Idempotent. Doesn't change which paths are vibe-dangerous, only what threshold the existing gate enforces.
4. **Aligns hook with AGENTS.md.** The "When confidence < tier-floor" section already says "Re-investigate." The hook's current behavior contradicts that prescription by allowing the commit anyway.

### Arguments against tightening (keep honor-system)

1. **Friction during legitimate test-as-spec splits.** AGENTS.md § Test-as-spec rules step 1: "Write the failing test FIRST. Commit it. Open a PR with only the test." That first commit deliberately ships a failing test → test_verif component drops by design → score will be sub-floor → mechanical block would refuse the very commit the discipline requires. Workaround: a `TOEBEANS_ALLOW_SUBFLOOR=1` env var, but that's just adding back the bypass we removed.
2. **Some sub-floor entries are correctly sub-floor.** Example: `c2a931e` ("test(notifications): assert allocator-issued notification ids (fails until $3 lands)") deliberately landed a red-CI test at score 86. The notes say: "Below 95 floor: deliberately red-CI commit per test-as-spec discipline." Blocking this commit would have broken the test-as-spec workflow.
3. **Recalibration risk.** A mechanical block invites score-bumping. The current honor-system discipline produces *honest sub-floor entries with explained rationale*. A block invites the failure mode `AGENTS.md § Confidence-score rule` warns against: "No score-bumping without new evidence." We lose the truth-telling property of the log in exchange for a mechanical gate.
4. **Solo-developer cost.** At the v0.1 stage, Wei is the only committer. The hook is self-enforced. The hazard "tired author commits broken code" is the same with or without the block; the block just changes when the discipline check fires (pre-commit vs. self-reflection).

### Numbers (from the actual calibration log as of 2026-05-17)

- 28 total entries.
- 8 vibe-dangerous entries with score < 95: scores [86, 86, 88, 89, 89, 91, 92, 92].
- 1 retroactive vibe-safe entry below 80: scores [58, 60, 64].
- 0 entries where the sub-floor rationale was "I'm in a hurry."
- 0 entries that appear to be score-bumped (every sub-floor entry names the specific component that pulled it down and the specific evidence that would raise it).

Per-incident analysis:

- `c7f5eff` (score 91): WOULD have been blocked. Compile errors. Block would have helped.
- `c2a931e` (score 86): WOULD have been blocked. Deliberately red-CI test. Block would have hurt the workflow.
- `decdec2` (score 89): WOULD have been blocked. DST test pinning. The sub-floor was JDK17 unavailability, not skipped discipline. Marginal.
- The other 5: same shape — honest sub-floor with evidence-based rationale. Mixed.

## Consequences

### If we tighten (Accept this ADR)

- `c7f5eff`-class incidents become harder to produce.
- Test-as-spec workflow requires an explicit bypass envvar.
- ~10 LOC added to the hook + 1 test case in `scripts/test_pre_commit_hook.sh` for the new block path.
- AGENTS.md § Confidence-score rule paragraph stays the same; the hook now matches its prescriptive text.

### If we keep honor-system (Reject this ADR)

- The status quo continues. Honest sub-floor entries are the norm; some of them are correct (test-as-spec splits), some of them are drift (the `c7f5eff` shape).
- The cost of the next `c7f5eff`-class incident is `~half a session of recovery`. At current pace (~1 per 28 commits = 3.5%), expected cost is ~0.5 session per 30 commits.
- M1.2 internal-beta soak will probably produce more evidence about which kind of incident actually matters in the field.

### Rejected alternatives

- **Block only on score < 80 (any tier).** Too lax for vibe-dangerous (still allows broken-compile-on-main if author scored 81).
- **Block based on which component is below threshold.** E.g., "test_verif < 14 blocks regardless of total score." Too granular; the honest sub-floor entries are diverse in which component is low.
- **Soft block (warning only).** The hook already prints messages; a soft block adds nothing the current behavior doesn't already provide.

## Decision deadline

After the M1.2 retention gate at day 14, Wei should review:

1. How many sub-floor commits landed in M1.2.
2. How many of those produced user-visible incidents during the 30-day soak.
3. Whether any incidents in the soak correlate with sub-floor scores.

If the correlation is meaningful, tighten the hook (this ADR moves to Accepted). If not, keep honor-system (this ADR moves to Rejected). Either resolution should be a one-line `Status:` edit on this file plus the corresponding hook change (or non-change).

## Cross-references

- `AGENTS.md` § Confidence-score rule and § When confidence < tier-floor.
- `scripts/git-hooks/pre-commit` (current implementation).
- `.codeit/calibration.jsonl` entries 14, 17-21, 26 (the sub-floor vibe-dangerous set as of writing).
- 2026-05-17 adversarial review F1-corrected (the broken-on-main analysis).
