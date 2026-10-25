package app.toebeans.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-authored history (clock) ImageVector used as the History-tab icon.
 *
 * We don't pull in material-icons-extended (~5 MB) just for one glyph — that would be a
 * vibe-dangerous dep add per AGENTS.md. Same pattern as [PawIcon]: a 24x24-viewport
 * vector that composes cleanly with the rest of Material's Filled icon set, black-filled
 * so the `Icon(tint = ...)` callsite's ColorFilter replaces the fill with
 * LocalContentColor exactly like every other Material Filled icon.
 *
 * Anatomy:
 *  - A 2dp-thick ring (outer radius 9, inner radius 7, even-odd fill) for the clock face.
 *  - An "L"-shaped pair of hands meeting near the center: vertical hand pointing to
 *    12, horizontal hand pointing to 3. Drawn as one polygon with a shared corner.
 */
public val HistoryIcon: ImageVector by lazy { buildHistoryIcon() }

private fun buildHistoryIcon(): ImageVector =
    ImageVector
        .Builder(
            name = "HistoryClock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        // Ring: two circular subpaths in one even-odd path. EvenOdd guarantees the inner
        // disc is a hole regardless of winding direction.
        .path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            addCircleSubpath(12f, 12f, 9f)
            addCircleSubpath(12f, 12f, 7f)
        }
        // Clock hands: vertical to 12, horizontal to 3, 1.6f wide, sharing the corner
        // at (12.8, 12.8) so the two hands read as one continuous glyph.
        .path(fill = SolidColor(Color.Black)) {
            moveTo(11.2f, 7f)
            lineTo(12.8f, 7f)
            lineTo(12.8f, 11.2f)
            lineTo(15.5f, 11.2f)
            lineTo(15.5f, 12.8f)
            lineTo(11.2f, 12.8f)
            close()
        }.build()

/** Adds one full circular subpath centered at ([cx], [cy]) with radius [r]. */
private fun PathBuilder.addCircleSubpath(
    cx: Float,
    cy: Float,
    r: Float,
) {
    val k = r * 0.5522847f
    moveTo(cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    close()
}
