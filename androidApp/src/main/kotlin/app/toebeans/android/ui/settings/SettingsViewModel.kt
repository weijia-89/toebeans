package app.toebeans.android.ui.settings

import androidx.lifecycle.ViewModel
import app.toebeans.android.preferences.ThemeMode
import app.toebeans.android.preferences.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings ViewModel — exposes [ThemePreferences] as read/write Compose state and
 * wires the SettingsScreen toggles to the underlying SharedPreferences-backed store.
 *
 * No additional state is kept here; ThemePreferences already owns the StateFlows and
 * write methods. The VM exists so the Composable can be tested without a real
 * Application Context (SharedPreferences requires Context, ThemePreferences can be
 * faked in tests by passing a stub Context).
 *
 * Why a separate VM rather than calling Koin from the Composable directly: keeps the
 * pattern consistent with every other screen (Pets, Home, etc. all use koinViewModel),
 * which means future additions (export data, clear data, etc.) have a clear home.
 */
public class SettingsViewModel(
    private val prefs: ThemePreferences,
) : ViewModel() {
    public val themeMode: StateFlow<ThemeMode> = prefs.themeMode
    public val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor

    public fun setThemeMode(mode: ThemeMode): Unit = prefs.setThemeMode(mode)

    public fun setDynamicColor(enabled: Boolean): Unit = prefs.setDynamicColor(enabled)
}
