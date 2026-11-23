# Design handoff: toebeans

**Local-only.** No Magic Patterns, no cloud design sync into the Android app.

## Pipeline

```text
docs/style-lab/index.html  →  DECISIONS.md  →  Compose UI  →  emulator / CI
```

Optional offline HTML under `docs/framer/` for reference; not cloud-connected ship path.

## Key files

| File | Purpose |
|------|---------|
| `docs/style-lab/` | Visual sign-off (terracotta-warm, sage-calm, …) |
| `docs/design-handoff/manifest.yaml` | Screen → style-lab pointers |
| `scripts/agent-hooks/ui-design-handoff.md` | Agent policy (editor-independent) |
| `scripts/agent-hooks/` | Executable hooks: deny `magic-patterns` MCP, session context, style-lab nudge |
| `.cursor/hooks.json` | Thin Cursor registration pointing at `scripts/agent-hooks/` |
| `.cursor/rules/ui-design-handoff.mdc` | Thin Cursor rule wrapper pointing at the policy above |

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
- `docs/style-lab/` (local design surface)
