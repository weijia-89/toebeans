# Design handoff — toebeans

**Local-only.** No Magic Patterns, no cloud design sync into the Android app.

## Pipeline

```text
docs/style-lab/index.html  →  DECISIONS.md  →  Compose UI  →  emulator / CI
```

Optional offline HTML under `docs/framer/` for reference — not cloud-connected ship path.

## Key files

| File | Purpose |
|------|---------|
| `docs/style-lab/` | Visual sign-off (terracotta-warm, sage-calm, …) |
| `docs/design-handoff/manifest.yaml` | Screen → style-lab pointers |
| `.cursor/rules/ui-design-handoff.mdc` | Agent policy |
| `.cursor/hooks.json` | **Blocks** `magic-patterns` MCP; style-lab nudges |

## Typical workflow (Today tab)

1. Tweak layout/colors in **`docs/style-lab/index.html`**.
2. Log choice in **`docs/style-lab/DECISIONS.md`**.
3. Implement in **Compose** under `androidApp/src/main/kotlin/`.
4. `bash scripts/manual_qa_boot.sh fresh --open-style-lab`
5. `bash scripts/style_lab_handoff_check.sh`

## What is automated

| Trigger | Behavior |
|---------|----------|
| Session start | Reminder: style-lab only |
| **beforeMCPExecution** | **Denies** Magic Patterns MCP |
| **Write** to `androidApp/src/main/**/*.kt` UI | Nudge: update style-lab |

## Why no Magic Patterns

README: no cloud, no telemetry, no third-party services. MP is hosted SaaS.

## Related

- README § Design review
- `~/Projects/docs/framer-prototype-toebeans-buds.md` (Framer HTML in-repo only)
