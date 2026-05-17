package app.toebeans.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.toebeans.android.preferences.FirstLaunchPreferences
import app.toebeans.android.preferences.ThemeMode
import app.toebeans.android.preferences.ThemePreferences
import app.toebeans.android.ui.ToebeansAppShell
import app.toebeans.android.ui.firstlaunch.FirstLaunchDialogHost
import app.toebeans.android.ui.theme.ToebeansTheme
import org.koin.android.ext.android.inject

/**
 * Single-Activity host. All screens live inside the [ToebeansAppShell] NavHost.
 *
 * Per AGENTS.md, this file lives on a vibe-safe UI surface (no scheduler logic). The
 * notifications package and AndroidManifest changes are the vibe-dangerous surfaces in
 * this module; the UI here just renders state from Koin-injected ViewModels.
 *
 * Reads [ThemePreferences] from Koin so user toggles in Settings (Auto/Light/Dark +
 * dynamic-color on/off) take effect on the very next recomposition without an app
 * restart. The Theme uses the resolved darkTheme + dynamic values; the SharedPreferences-
 * backed StateFlows emit synchronously inside the same Composable thread, so flipping a
 * toggle in Settings repaints the rest of the app immediately.
 */
class MainActivity : ComponentActivity() {
    private val themePrefs: ThemePreferences by inject()
    private val firstLaunchPrefs: FirstLaunchPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by themePrefs.themeMode.collectAsState()
            val dynamicEnabled by themePrefs.dynamicColor.collectAsState()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ToebeansTheme(darkTheme = darkTheme, dynamic = dynamicEnabled) {
                ToebeansAppShell()
                // Layered on top of the shell so it sits above whichever tab is active.
                // The host short-circuits when the seen-flag is true, so this is a
                // ~free no-op after the first launch is acknowledged.
                FirstLaunchDialogHost(prefs = firstLaunchPrefs)
            }
        }
    }
}
