package app.toebeans.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom paw-print ImageVector used as the Pets-tab icon.
 *
 * We don't pull in material-icons-extended (~5 MB) just for one glyph — that would be a
 * vibe-dangerous dep add per AGENTS.md. This vector is hand-authored against a 24×24
 * viewport so it composes cleanly with the rest of Material's Filled icon set.
 *
 * Anatomy:
 *  - 1 large main pad (lower middle): a rounded shape centered on (12, 16) with a
 *    flattened bottom — the classic dog/cat-pad silhouette.
 *  - 4 toe beans (ovals) arranged in a slight fan above:
 *      • outer-left  toe at (5, 8)
 *      • inner-left  toe at (9, 4.5)
 *      • inner-right toe at (15, 4.5)
 *      • outer-right toe at (19, 8)
 *
 * The toe beans are NOT identical circles — the inner two sit slightly higher than the
 * outer two, which is biologically accurate (dog and cat paws fan their toes), and is
 * also the de-facto Western pictographic convention for "paw" (U+1F43E follows the same
 * arrangement).
 *
 * Color: the vector's path uses `SolidColor(Color.Black)` — same convention as Material
 * Icons. The visible color is determined by the `Icon(tint = ...)` callsite, which
 * applies a `ColorFilter` that REPLACES (not blends) the vector's fill. So an Icon
 * wrapping this vector gets tinted to whatever `LocalContentColor.current` is, exactly
 * matching the selected/unselected state behavior of every other Material Filled icon.
 *
 * Don't use `Color.Unspecified` as fill — it's interpreted as "no color set" by the
 * vector renderer and paints nothing at all (the toes show up as blank space, which
 * I discovered by adversarial-screenshotting the first attempt).
 */
public val PawIcon: ImageVector by lazy { buildPawIcon() }

private fun buildPawIcon(): ImageVector {
    // Toe-bean radii (rx, ry). Slightly oval because cat/dog toe pads are taller than
    // they are wide; pure circles read as "spots" not "toes".
    val toeRx = 1.8f
    val toeRy = 2.2f

    return ImageVector
        .Builder(
            name = "Paw",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        // Each ellipse is drawn as a path using two cubic Bezier arcs (Compose's path
        // DSL doesn't have a native ellipse op; this is the M3 / Material Symbols
        // convention).
        .addEllipse(cx = 5f, cy = 8f, rx = toeRx, ry = toeRy)
        .addEllipse(cx = 9f, cy = 4.5f, rx = toeRx, ry = toeRy)
        .addEllipse(cx = 15f, cy = 4.5f, rx = toeRx, ry = toeRy)
        .addEllipse(cx = 19f, cy = 8f, rx = toeRx, ry = toeRy)
        // Main pad — wider than tall, with a subtly rounded square footprint. Designed
        // by hand: the top is convex (bumps up between the toes), the bottom is gently
        // flat (the paw "sits" on the ground). Coordinates form a closed path.
        .addPath {
            moveTo(7f, 13f)
            // Top of pad — convex curve up between the toes.
            curveTo(7f, 11f, 9f, 10f, 12f, 10f)
            curveTo(15f, 10f, 17f, 11f, 17f, 13f)
            // Right side — slight curve out.
            curveTo(17f, 14.5f, 18f, 15f, 18f, 16.5f)
            // Bottom-right corner — rounded.
            curveTo(18f, 18.5f, 16f, 19.5f, 14f, 19.5f)
            // Across the bottom.
            curveTo(13f, 19.5f, 11f, 19.5f, 10f, 19.5f)
            // Bottom-left corner — rounded.
            curveTo(8f, 19.5f, 6f, 18.5f, 6f, 16.5f)
            // Left side — back up to start.
            curveTo(6f, 15f, 7f, 14.5f, 7f, 13f)
            close()
        }.build()
}

// -- helpers --------------------------------------------------------------------

/**
 * Adds an ellipse to the ImageVector builder using 4 cubic Bezier arcs. Compose's path
 * DSL doesn't expose ellipse natively; this is the standard approximation (Kappa = 0.5522)
 * which produces a curve indistinguishable from a true ellipse at icon resolutions.
 */
private fun ImageVector.Builder.addEllipse(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
): ImageVector.Builder {
    val kx = rx * 0.5522847f
    val ky = ry * 0.5522847f
    return addPath {
        moveTo(cx - rx, cy)
        curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        close()
    }
}

// Black-fill convention matches Material Icons. The actual rendered color comes from
// Icon's `tint` ColorFilter at the callsite (which replaces, not blends, the fill).
private fun ImageVector.Builder.addPath(block: PathBuilder.() -> Unit): ImageVector.Builder =
    path(fill = SolidColor(Color.Black)) { block() }
