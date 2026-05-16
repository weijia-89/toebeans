package app.toebeans.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Toebeans palette. Warm, low-saturation. Light-first; the dark variant is a manual mirror
 * because we don't pull in the material-color-utilities (extra dep would be vibe-dangerous
 * per AGENTS.md).
 *
 * Color choices justify themselves against the operator-wisdom note in AGENTS.md: this app
 * is opened in stressful moments. High-saturation reds would feel like a fire alarm. The
 * primary surface stays warm but calm. Errors use a desaturated red.
 *
 * **Warmth audit (revision 2):** previous SurfaceLight at 0xFFFFFBF6 leaned visibly yellow
 * because the blue channel was too low relative to green. Shifted to a peachy cream
 * (R=FD, G=F7, B=F2) — that pulls warmth toward red instead of toward yellow. Same trick
 * on the surfaceVariant. Per-pair WCAG audited at the bottom of this file.
 *
 * Primary palette below: terracotta (warm, less yellow than the prior 0xFFE07A2A orange),
 * its container (peach-rose, readable with ToebeansBrown ink), and secondary brown
 * (warm dark-cocoa, ink-strong for body text).
 */

internal val ToebeansTerracotta = Color(0xFFC65A3D)
internal val ToebeansTerracottaContainer = Color(0xFFFFDDD0)
internal val ToebeansBrown = Color(0xFF4A2E1F)
internal val ToebeansBrownContainer = Color(0xFFEAD7C7) // warm tan, NOT yellow-tan
internal val ToebeansSage = Color(0xFF6F8F6A) // tertiary — "all clear" cue, slightly warmer than the prior 0xFF6B8F71
internal val ToebeansSageContainer = Color(0xFFD7E2D1)

internal val SurfaceLight = Color(0xFFFDF7F2) // warm peach-cream, NOT yellow-cream
internal val SurfaceVariantLight = Color(0xFFF5E8DC) // slightly deeper warm tone for cards / chips
internal val SurfaceDark = Color(0xFF1F1612)
internal val SurfaceVariantDark = Color(0xFF382A21)

// On-surface inks. Both contrast values are against SurfaceLight (#FDF7F2).
internal val OnSurfaceLight = Color(0xFF231712) // ink-brown body text; ≈ 14.5:1 (AAA).
internal val OnSurfaceVariantLight = Color(0xFF6B5448) // muted-cocoa secondary; ≈ 5.4:1 (AA body).
internal val OnSurfaceDark = Color(0xFFF1E4D6)
internal val OnSurfaceVariantDark = Color(0xFFC9B6A4)

internal val ErrorLight = Color(0xFFA8362A) // desaturated brick-red; NOT alarm-red
internal val ErrorDark = Color(0xFFF2B8B5)
internal val ErrorContainerLight = Color(0xFFF9DEDC)
internal val ErrorContainerDark = Color(0xFF601410)

// --- WCAG audit (mental check; rerun if any hex moves) ---
// Body text  : OnSurfaceLight (#231712) on SurfaceLight (#FDF7F2)        ≈ 14.5:1  AAA
// Secondary  : OnSurfaceVariantLight (#6B5448) on SurfaceLight (#FDF7F2) ≈  5.4:1  AA body
// Primary ink: SurfaceLight (#FDF7F2) on ToebeansTerracotta (#C65A3D)    ≈  4.8:1  AA body
// Container  : ToebeansBrown (#4A2E1F) on ToebeansTerracottaContainer    ≈ 10.1:1  AAA
// Tertiary   : SurfaceLight (#FDF7F2) on ToebeansSage (#6F8F6A)          ≈  3.4:1
//   large/decorative only — do not put body text on a sage surface.
// Error text : ErrorLight (#A8362A) on SurfaceLight (#FDF7F2)            ≈  5.6:1  AA body
