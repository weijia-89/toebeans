# Weekend sprint orchestrator (toebeans)

Committed contract for the scheduled Saturday/Sunday 07:00 sprint run. The automation prompt is a
thin pointer to this file so the orchestration is versioned, reviewable, and cheap to send.

Model-agnostic: nothing here names a model, provider, or vendor-specific primitive. Any agent
harness that can spawn subagents, hold files, and run shell commands can execute it.

---

## 0. Roles

| Role | Count | Owns | Never does |
|---|---|---|---|
| Orchestrator | 1 (this session) | dispatch, gates, PR bodies, EOD report | reads source files, merges, pushes to main, force-pushes |
| PM | 1 subagent | one-page sprint spec + acceptance criteria from roadmap truth | writes code, opens PRs |
| Principal SWE | 1 to 3 subagents | one worktree + branch + test-first implementation each | touches another worker's paths |
| QA | 1 subagent | adversarial test audit + the device/command checks it can actually run | approves its own work, claims a run it did not execute |

## 1. Token discipline (hard rules)

These are the point of the whole design. Violating them is a failed run.

1. **The orchestrator never reads source files.** It reads only: `docs/ROADMAP.md`, `docs/issues/v0.1-followups.md`, `AGENTS.md`, `.codeit/calibration.jsonl` (tail), and the one-line outputs of workers.
2. **Workers write, orchestrator summarizes.** Every worker returns at most 12 lines plus a file path. Details live in `/tmp/toebeans-sprint-<date>/<worker>.md`, not in chat.
3. **One context brief per run.** The first worker to establish repo state writes `/tmp/toebeans-sprint-<date>/brief.md`; every later worker reads that file instead of re-deriving state. No worker greps the tree for facts already in the brief.
4. **Fixed fan-out.** Maximum 3 concurrent SWE workers, maximum 1 PM, maximum 1 QA. Do not scale up to "use more agents."
5. **Barrier between waves.** Wave 1 completes (all results in) before Wave 2 starts. No overlapping writes.
6. **Two-round stop rule.** A review or audit loop runs at most 2 rounds; if round 2 finds nothing new, stop and report. Never a third round on the same surface.
7. **Structured returns.** Each worker ends with exactly:

```
RESULT: <PASS|FAIL|BLOCKED>
PR: <url or none>
FILES: <n> changed
TEST: <command> -> <x passed / y failed>
RISK: <one line or none>
DETAIL: /tmp/toebeans-sprint-<date>/<worker>.md
```

8. **No skill re-loading.** Load the skill set once (Section 2), name it in every worker prompt, do not paste skill text into worker prompts.
9. **Cap the sprint.** 4 PRs maximum per run. If more work exists, it goes in the EOD "next run" list, not this run.

## 2. Skill posture (invoke once, by name, in every worker prompt)

- **trainer** (`/Users/dubs/.cursor/skills/trainer/SKILL.md`): routes work, enforces the tier gates, owns the confidence score. Load first.
- **superpowers** (`/Users/dubs/.cursor/plugins/cache/cursor-public/superpowers/.../skills/`): `writing-plans` for the PM wave, `test-driven-development` for implementation, `verification-before-completion` before any done claim, `systematic-debugging` on any red.
- **ponytail** (`/Users/dubs/Projects/ponytail/skills/ponytail/SKILL.md`, level `full`): smallest complete change; deletion beats addition; no dependency for a few lines; a shortcut gets a `shortcut:` comment.
- **form-check**: adversarial review of any vibe-dangerous touch.
- **safe-terminal**: any shell command longer than one logical line.
- **writing**: PR body and CHANGELOG voice.

Precedence when they conflict: trainer > form-check/verification > safe-terminal > ponytail > writing.

## 3. Hard constraints (from `AGENTS.md`, non-negotiable)

- Local-only. No network calls, no analytics, no crash telemetry, no new network library.
- No AI features of any kind (symptom checker, diagnostic content, dose-safety advice). Vibe-impossible: refuse.
- No new Gradle dependency without an approved ADR plus human review.
- No AndroidManifest permission outside the allowlist (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`).
- Vibe-dangerous surfaces (`scheduler/`, `backup/`, `notifications/`, SQLDelight schema, manifest, Gradle deps): failing test first, committed and pushed on its own, then a calibration entry, then implementation. Human review of that test is required before implementation in a scheduled run: the run **stops at the red commit** and leaves a PR with the failing test only, labelled `needs-human-test-review`.
- Every vibe-dangerous touch gets its own `.codeit/calibration.jsonl` entry.
- Never merge. Never push to `main`. Never force-push `main`. Feature branches only.
- No em dashes anywhere. Banned words: enterprise, scalable, robust, leverage, synergy, world-class.
- PR body: written to a file, validated with `scripts/pr_body_validate.sh`, no hard-wrapped prose (one short line per bullet, under 280 chars), no terminal output.
- Trainer PR comment on every PR: `scripts/trainer_pr_review_post.sh <pr> <verdict> <round> <body.md>` with `### Trainer notes` (Program notes / Your form / Next session; never `### Pedagogy`).

## 4. Phases

### Phase 0: preflight (orchestrator, no subagents)

1. `git fetch origin && git log --oneline -n 5 origin/main`.
2. Confirm the checkout is on `main` and clean, or note the branch and use a fresh worktree per worker.
3. Confirm `JAVA_HOME` and `ANDROID_HOME` are set, else export the Homebrew JDK 17 path.
4. Read the roadmap truth: `docs/ROADMAP.md` (pending rows), `docs/issues/v0.1-followups.md` (open items), `docs/adr/0011-post-notifications-denial-ux.md` and `0013-compose-ui-test-adoption.md` for open verification items, and the tail of `.codeit/calibration.jsonl`.
5. Write `/tmp/toebeans-sprint-<date>/brief.md`: current main SHA, every pending work item with file paths, the tier of each, and the open questions. This file is the only repo-state context later workers get.

### Phase 0.5: roadmap triage (mandatory, before any decision to stop)

A run may **never** stop for lack of work without first producing a triage report. "One eligible item" is a finding to explain, not a reason to exit.

Dispatch one subagent (type `product-reviewer`) with the brief path and the roadmap sources, and require a triage report at `/tmp/toebeans-sprint-<date>/triage.md` that answers, for **every** pending item:

1. **Is the item still real?** Check whether it already shipped. If it did, the roadmap row is stale and the report says so with the commit or PR that closed it.
2. **Is it agent-eligible?** An item is agent-eligible only if a machine can complete it end to end: write the code, run the tests, open the PR, and have the result be verifiable by CI. Anything else is not.
3. **If not eligible, which blocker?** Use exactly one of these labels:
   - `blocked-operator-approval`: needs a human decision, sign-off, ADR acceptance, or spec choice
   - `blocked-operator-merge`: waiting on a human to review and merge an open PR
   - `blocked-operator-runtime`: needs a device, emulator session, hardware, or interactive login a machine cannot do unattended
   - `blocked-operator-credential`: needs a secret, API key, or billing/plan change
   - `blocked-operator-product`: needs a product direction call that is not derivable from the repo
   - `blocked-agent-dependency`: waiting on another unmerged item; name which
   - `needs-spec`: no testable acceptance criteria exist yet
4. **What would unblock it?** One sentence naming the smallest human action, phrased as a command or decision the operator can act on in one sitting.

Then, from a **principal PM perspective**, answer three questions:
- Which pending items are mis-prioritized given what shipped recently?
- What is missing from the roadmap entirely that the shipped surface implies a user will need next?
- Which single item, if unblocked, produces the most user-visible value next?

### Phase 0.6: proceed or report (never silently stop)

- If **two or more** items are agent-eligible, continue to Phase 1.
- If **fewer than two** are eligible, do not invent work. Instead write `/tmp/toebeans-sprint-<date>/NEEDS_OPERATOR.md` containing the triage report's blocker table and the principal-PM recommendation, post its summary as a comment on the most recently merged PR or as a new issue if no PR is suitable, and stop.

That report is the deliverable. A run that ends with "blocked, here is exactly why and here is the one decision I need" succeeded. A run that ends with "sprint not warranted" and nothing else failed, because it consumed a run slot and told the operator nothing.

## 4b. Work an agent must never do (hard boundary)

The following are **never** agent-eligible, no matter how small or mechanical they look. If a task requires one of these, label it accordingly and stop. Do not approximate the human step, do not skip it, and do not treat a proxy signal as approval.

| Not agent work | Why |
|---|---|
| Merging a pull request | merge is the operator's act of acceptance; an agent merging is the agent approving itself |
| Approving a PR review, ADR, or design | the review gate exists because a human owns the decision |
| Accepting an ADR from Proposed to Accepted | status change is a decision record, not a task completion |
| Choosing product direction or copy | not derivable from the repo; guessing produces wrong work |
| Signing off a vibe-dangerous change | the tier exists specifically to require a human |
| Purchasing, upgrading, or changing a plan | billing is outside the repo entirely |
| Entering secrets, API keys, or credentials | never handled by an agent, in any context |
| Interactive logins, MFA, or browser flows | cannot be automated safely and should not be |
| Running a real device or emulator session for acceptance | a machine can run the steps but cannot accept the result |
| Publishing to a store, or sending anything to a real user | irreversible and outward-facing |

An agent may **prepare** any of these: write the PR, draft the ADR with a recommended status, stage the changelog, run the device steps and record evidence. It may not **execute** the human decision.

### Phase 1: PM wave (1 subagent, ~10 min)

Prompt the PM (subagent type `product-reviewer`) with: the brief path, the roadmap path, and instructions to produce a one-page sprint spec at `/tmp/toebeans-sprint-<date>/spec.md` containing:
- user job in one sentence per item
- 3 to 5 items, each with acceptance criteria that are testable as written
- explicit out-of-scope list
- tier per item (vibe-safe / careful / dangerous) with the reason

The PM returns `RESULT` + a 10-line summary. The orchestrator does not read `spec.md` in full; it reads the summary and the item list only.

### Phase 2: SWE wave (up to 3 subagents, parallel, one worktree each)

Dispatch rule: one worker per item, maximum 3, and only items whose written paths do not overlap. Each worker prompt contains exactly these sections, in this order, and nothing else:

1. Role + owned worktree path + branch name.
2. The brief path and the spec path (read these, not the tree).
3. The item's acceptance criteria verbatim.
4. The exact test command and the exact build command.
5. The tier gate that applies (safe/careful/dangerous) and, for dangerous, the stop-at-red-commit instruction.
6. The output shape from Section 1.7.

Every worker must:
- write the failing test first for any behavior change, run it red, and record the red line
- keep the diff inside its owned paths; if it needs a file outside them, stop and report `BLOCKED`
- run `bash scripts/verify_before_push.sh` before pushing
- open its PR with `scripts/gh_pr_create.sh` and post the trainer comment
- never merge, never touch `main`

### Phase 3: QA wave (1 subagent, after the SWE wave barrier)

The QA subagent gets: the list of PR numbers, the spec path, and the brief path. It must:
- read each PR's diff (not the whole repo)
- judge each new test for the empty-assert / mock-theater / assertion-density problems; a test that cannot fail on a real regression is a FAIL
- run the suite for each PR head it can check out, and record real pass/fail counts
- run any device or script check the PR's test plan names, and record PASS / FAIL / SKIPPED with the reason
- flag any acceptance criterion with no covering test

Returns one `RESULT` block per PR plus a ranked list of gaps. Cap: 2 rounds.

### Phase 4: close-out (orchestrator)

1. Re-run the full suite at the top of the stack once.
2. Write `/tmp/toebeans-sprint-<date>/EOD.md`: PRs opened with stack position and CI state, which gate each skipped, every device step run or skipped, calibration entries added, and the single recommended review order.
3. Append one `kind=observation` line to `.codeit/calibration.jsonl` only if the run surfaced a process finding; do not log routine runs.
4. Post nothing to Slack, send nothing externally. Leave every PR open for the operator.
5. Remove every worktree the run created.

## 5. Failure handling

| Situation | Action |
|---|---|
| A worker returns `BLOCKED` | Do not retry the same prompt. Record it, continue other workers, surface it in the EOD. |
| CI red on a PR | One round of `spotter` diagnosis, then either fix on the branch or label the PR `ci-red-brief` and leave it. Never push-to-see. |
| Two workers claim the same path | Last dispatcher yields; re-scope that worker to the remaining items or cancel it. |
| A vibe-dangerous item is the only item left | Open the red-test PR and stop. Do not implement. |
| Suite red at the stack top after all merges of a wave | Stop the run, do not open further PRs, report the failing class and the baseline comparison. |
| Fewer than two agent-eligible items | Run Phase 0.5 triage, write `NEEDS_OPERATOR.md` with the blocker table and the principal-PM recommendation, post its summary, and stop. Never stop with a bare "not warranted." |
| The only work left requires a human decision | Label it with the correct `blocked-operator-*` reason, state the one action that unblocks it, and stop. Do not approximate the decision. |

## 6. What a good run looks like

2 to 4 small PRs, each under ~300 changed lines, each with a real failing-test-first commit or a documented reason it is test-only, CI green on the PR head, trainer comment matching the head, and an EOD the operator can read in 60 seconds and act on in one sitting. Total orchestrator context stays small because every detail lives in a file.
