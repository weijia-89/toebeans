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
 */

internal val ToebeansOrange = Color(0xFFE07A2A) // primary — toe-bean warm
internal val ToebeansOrangeContainer = Color(0xFFFFE0C8)
internal val ToebeansBrown = Color(0xFF5A3925) // secondary — paw-pad accent
internal val ToebeansBrownContainer = Color(0xFFE6D2C0)
internal val ToebeansSage = Color(0xFF6B8F71) // tertiary — "all clear" cue
internal val ToebeansSageContainer = Color(0xFFD2E0D4)

internal val SurfaceLight = Color(0xFFFFFBF6)
internal val SurfaceDark = Color(0xFF1A140F)

internal val OnSurfaceLight = Color(0xFF1A140F)
internal val OnSurfaceDark = Color(0xFFF1E8DC)

internal val ErrorLight = Color(0xFFB3261E)
internal val ErrorDark = Color(0xFFF2B8B5)
internal val ErrorContainerLight = Color(0xFFF9DEDC)
internal val ErrorContainerDark = Color(0xFF601410)
