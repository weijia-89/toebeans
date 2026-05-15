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
        primary = ToebeansOrange,
        onPrimary = SurfaceLight,
        primaryContainer = ToebeansOrangeContainer,
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
        onSurface = OnSurfaceLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        error = ErrorLight,
        onError = SurfaceLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = ErrorLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = ToebeansOrangeContainer,
        onPrimary = ToebeansBrown,
        primaryContainer = ToebeansOrange,
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
        onSurface = OnSurfaceDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        error = ErrorDark,
        onError = SurfaceDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = ErrorDark,
    )

/**
 * App theme. On Android 12+ devices, [dynamic] = true (default) lets the system extract a
 * Material You palette from the user's wallpaper. We always fall back to the hand-tuned
 * Toebeans palette on older devices and for previews.
 */
@Composable
public fun ToebeansTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
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
