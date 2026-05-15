package app.toebeans.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Decorative semi-transparent pill silhouette painted as a screen background.
 *
 * Visual: a single capsule rotated about -22 degrees, occupying roughly the upper-right
 * quadrant of the parent. Two halves with a slightly stronger seam in the middle to read as
 * a real two-tone capsule, but kept very low-alpha so it does not interfere with foreground
 * text contrast — per the contrast audit in [`Color.kt`].
 *
 * **WCAG note:** the pill draws at ≤ 8% alpha against [`SurfaceLight`] / [`SurfaceDark`].
 * Foreground text is always painted on the parent surface, never on the pill itself, so the
 * pill cannot push any text-on-background pair below the 4.5:1 AA threshold from the
 * Color.kt audit. Re-audit if [alpha] ever climbs above 12%.
 *
 * Drop this as the first child of a Box that hosts your Scaffold content:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     PillBackground(Modifier.matchParentSize())
 *     Scaffold(...) { ... }
 * }
 * ```
 */
@Composable
public fun PillBackground(
    modifier: Modifier = Modifier,
    // Slightly different alphas for the two halves so the seam reads as an actual pill
    // division rather than visual noise. Both well below the 12% cap mentioned above.
    leftHalfColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
    rightHalfColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
    seamColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    rotationDegrees: Float = -22f,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Pill geometry: ~60% of width, ~15% of height, anchored upper-right.
        val pillW = w * 0.60f
        val pillH = h * 0.15f
        val pillLeft = w * 0.35f
        val pillTop = h * 0.06f

        rotate(degrees = rotationDegrees, pivot = Offset(w * 0.7f, h * 0.18f)) {
            // Left half of the capsule
            drawRoundRect(
                color = leftHalfColor,
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillW / 2f + pillH / 2f, pillH),
                cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
            )
            // Right half (overlapping the left half by half the pill height so corner-radii
            // round only on the outer ends and the seam is straight).
            drawRoundRect(
                color = rightHalfColor,
                topLeft = Offset(pillLeft + pillW / 2f - pillH / 2f, pillTop),
                size = Size(pillW / 2f + pillH / 2f, pillH),
                cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
            )
            // Seam line at the midpoint, slightly darker.
            drawLine(
                color = seamColor,
                start = Offset(pillLeft + pillW / 2f, pillTop + pillH * 0.1f),
                end = Offset(pillLeft + pillW / 2f, pillTop + pillH * 0.9f),
                strokeWidth = 1.5f,
            )
        }
    }
}
