# UI wave 2 backlog

Deferred UX/IA work after M1.2 internal beta. Not in scope for the Today med-tap stale-form hotfix (`fix/today-medication-edit-tap-v2`).

Last updated: 2026-05-28.

## Nav IA

- **Target bottom-nav order (Wei 2026-05-28):** **Today → Reminders → Pets → Settings** (Reminders adjacent to Today; separate PR `feat/nav-ia-reminders-adjacent-today` after med-tap merge).
- Current `main` order: Today → Pets → Reminders → Settings ([#74](https://github.com/weijia-89/toebeans/pull/74)).

## Reminders screen

- Tapping a **medicine** row or **schedule** control on Reminders should open edit UI with **bubbles** for both **medicine** and **schedule** (not dead / non-navigating controls).
- **Date range** and **phase** controls on that surface should use the same edit entry (medicine + schedule context), not placeholders.

## Medication entry (Wave 2)

- **Medicine name search:** automatic lookup wired into the Add/Edit medication form (local index; no cloud).
- **Dose unit picker:** separate dropdown for units (tbsp, tsp, g, mL, tablet, etc.) alongside free-text dose amount.

## Medicine info from manual

- Autopopulate medication info fields from a parsed package insert / manual (operator-supplied source text).
- Re-run pipeline: full paraphrase/reword via **deai** skill (no RLHF register in stored copy).
- Persist in a retrieval-optimized, **maintainable** local store (schema TBD; no cloud).
- **Page-level cache** until the medication record changes (invalidate on med id / content version).
- **Operator note:** Wei may need to re-supply manual source text when labels change or cache is cold; document the re-ingest path in the feature spec.

## Related

- Canonical milestone tracker: [`docs/ROADMAP.md`](ROADMAP.md).
- Wave 2 kickoff handoff: [`docs/wave2-handoff-2026-05-28.md`](wave2-handoff-2026-05-28.md) (session context; not a product spec).
