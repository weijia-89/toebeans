# toebeans — UI design handoff (local-only)

Editor-independent copy of the agent policy. Cursor activates it through the thin wrapper at
`.cursor/rules/ui-design-handoff.mdc`; other agents can read this file directly.

**Manifest:** `docs/design-handoff/manifest.yaml`  
**Style lab:** `docs/style-lab/index.html`  
**Operator doc:** `docs/design-handoff.md`

## Hard rule

**No Magic Patterns**, no Framer cloud handoff into product code. Third-party design SaaS violates README constraints.

The `deny-magic-patterns-mcp.sh` hook **denies** `magic-patterns` MCP in this repo.

## Pipeline

```text
docs/style-lab/ (HTML/CSS variants, git)
    → log in docs/style-lab/DECISIONS.md
    → Jetpack Compose + theme tokens
    → bash scripts/manual_qa_boot.sh / emulator screenshots
```

## When editing Compose UI

1. Open or update `docs/style-lab/index.html` for layout/color experiments.
2. Run `bash scripts/style_lab_handoff_check.sh`.
3. `open docs/style-lab/index.html` for sign-off.

## Framer

`docs/framer/` may exist for **offline HTML reference only** — do not wire cloud sync or analytics.
