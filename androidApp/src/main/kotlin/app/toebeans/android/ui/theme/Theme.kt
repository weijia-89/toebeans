package app.toebeans.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors =
    lightColorScheme(
        primary = ToebeansTerracotta,
        onPrimary = SurfaceLight,
        primaryContainer = ToebeansTerracottaContainer,
        onPrimaryContainer = ToebeansBrown,
        secondary = ToebeansBrown,
        onSecondary = SurfaceLight,
        secondaryContainer = ToebeansBrownContainer,
        onSecondaryContainer = ToebeansBrown,
        tertiary = ToebeansSage,
        onTertiary = SurfaceLight,
        tertiaryContainer = ToebeansSageContainer,
        onTertiaryContainer = ToebeansBrown,
        surface = SurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurface = OnSurfaceLight,
        // onSurfaceVariant is the M3-correct token for secondary/label text. Using this
        // everywhere instead of onSurface.copy(alpha = 0.6f|0.7f) gives us a guaranteed
        // contrast pairing audited in Color.kt.
        onSurfaceVariant = OnSurfaceVariantLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        error = ErrorLight,
        onError = SurfaceLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = ErrorLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = ToebeansTerracottaContainer,
        onPrimary = ToebeansBrown,
        primaryContainer = ToebeansTerracotta,
        onPrimaryContainer = SurfaceLight,
        secondary = ToebeansBrownContainer,
        onSecondary = ToebeansBrown,
        secondaryContainer = ToebeansBrown,
        onSecondaryContainer = ToebeansBrownContainer,
        tertiary = ToebeansSageContainer,
        onTertiary = ToebeansBrown,
        tertiaryContainer = ToebeansSage,
        onTertiaryContainer = SurfaceLight,
        surface = SurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurface = OnSurfaceDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        error = ErrorDark,
        onError = SurfaceDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = ErrorDark,
    )

/**
 * App theme.
 *
 * **Dynamic color disabled by default** (revision 2). Material You's dynamic palette pulls
 * from the user's wallpaper, which on a green/blue wallpaper would override our hand-tuned
 * warm terracotta look with something cool and "off-brand." For a record-keeping app opened
 * in stressful moments, palette consistency matters more than wallpaper-matching. Caller
 * can still opt in by passing dynamic = true on a settings toggle later.
 */
@Composable
public fun ToebeansTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ToebeansTypography,
        content = content,
    )
}
