# Reminders tab: product audit worksheet

Goal: decide what the **Reminders** tab should be before M2 public polish (keep it, repurpose it, or merge its value into Today/Pets).

This is a **decision worksheet**, not an implementation plan. Do not delete routes in code from this document alone.

---

## 0) Ground truth inventory (10 minutes)

- What Reminders shows today:
- What Today shows today:
- What Pets shows today:
- What Reminders enables that Today/Pets cannot (if anything):

## 1) Jobs-to-be-done check (15 minutes)

For each job, mark where it belongs (Today / Pets / Reminders / Settings):

- Log a dose right now
- See what’s due today
- See what I already logged today
- Find “all schedules” quickly (catalog)
- Edit a medication + its schedule
- Answer “what reminders exist for this pet?”
- Answer “what is the next dose?” (per pet / per medication)

## 2) Confusion audit (10 minutes)

Pick one tester or yourself. Answer:

- If a user asks “Where do I change a reminder?”, where do you point them?
- If a user asks “Where do I see my schedules?”, where do you point them?
- If Reminders disappeared tomorrow, what breaks? What just moves?

## 3) Decision options

### Option A: Merge Reminders value into Today/Pets (demote tab)

Choose this if Reminders duplicates Today/Pets and creates “which screen?” confusion.

- What Reminders content moves to Today:
- What Reminders content moves to Pets (per-pet hub):
- What, if anything, remains as an entry point in Settings:
- What happens to deep links and navigation:

### Option B: Keep tab but repurpose scope

Choose this if the tab should exist but needs clearer differentiation.

- New Reminders tab title / copy:
- New primary content:
- What Reminders explicitly does NOT contain (guardrails):

### Option C: Keep as schedule catalog (clarify differentiation)

Choose this if a global “all schedules” catalog is a real job and is worth a dedicated tab.

- What makes it a catalog (sorting, grouping, search, filters):
- What edits it enables (and what it links out to):
- One sentence “why this is not Today”:

---

## 4) Output (commit message-sized)

Write the final decision in one paragraph:

- Chosen option (A/B/C):
- Reason:
- One concrete next change to reflect the decision:

