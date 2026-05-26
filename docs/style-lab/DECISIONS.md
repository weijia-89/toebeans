# toebeans style lab: decisions log

**Status:** signed off (2026-05-26). Compose alignment landed (`Theme.kt` dark tune, Today Log dose pill).

## Purpose

Compare visual variants for **Today**, **bottom nav**, and **Settings** chrome before a Compose
theme polish pass. Behavior and navigation stay locked in code and ADRs; this lab is color,
density, and component shape only.

Canonical shipped colors: `androidApp/src/main/kotlin/app/toebeans/android/ui/theme/Color.kt`.

## Chosen pack

**terracotta-warm** matches current `Color.kt` light roles. Sage remains tertiary for "Given ✓".

## Resolved decisions

| # | Question | Decision |
|---|----------|----------|
| 1 | Default pack for v1 internal beta | **terracotta-warm** |
| 2 | Given-state color | **Keep** tertiary sage (`ToebeansSage`) for "Given ✓" |
| 3 | Log dose control | **Match lab:** filled primary pill on Today rows (replace `FilledTonalButton`) |
| 4 | Dark theme | **Tune it:** adjust `darkColorScheme` in the same theme PR as light + lab alignment |
| 5 | Material You dynamic color | **No new work:** already shipped (see below) |

### Material You (#5): already done

No style-lab or sign-off build-out required.

- **Settings → Material You** toggle exists (`SettingsScreen`, `SettingsViewModel`, `ThemePreferences`).
- **Default off:** app uses static `LightColors` / `DarkColors` (terracotta-warm pack).
- **When user enables it** (API 31+), `ToebeansTheme(dynamic = true)` swaps the entire scheme to `dynamicLightColorScheme` / `dynamicDarkColorScheme` (wallpaper-derived). That **replaces** the hand-tuned pack; it does not blend or tint within terracotta bounds. Documented in `Theme.kt` KDoc (revision 2).

Post-sign-off theme PR does **not** need to change this behavior unless product direction changes.

## Variants not chosen

| Pack | Notes |
|------|-------|
| sage-calm | Rejected as default; sage kept for tertiary / Given only |
| high-contrast | Accessibility probe only; not v1 default |

## Surfaces rendered (lab)

- Today dose row: pending (**Log dose**) vs given (**Given ✓**)
- Chip / label density on dose rows
- Settings card header + primary button (export affordance mock)

## Compose follow-up PR (after this sign-off)

Scope for the theme polish PR:

1. Today **Log dose** → filled pill matching `docs/style-lab/index.html` `.btn`
2. **Dark** `darkColorScheme` pass aligned to terracotta-warm intent
3. No Material You changes unless explicitly requested later

## Sign-off gate

- [x] Open `docs/style-lab/index.html` offline and review all three packs.
- [x] Wei sets **`Chosen:`** below.
- [x] `tokens-snapshot.json` reflects terracotta-warm (no hex change vs `Color.kt`).
- [ ] Record completion in `docs/ROADMAP.md` when this commit lands.
- [x] Compose theme alignment PR (items 3–4 above).

**Chosen:** terracotta-warm (2026-05-26)
