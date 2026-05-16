package app.toebeans.android.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Theme + display preferences, persisted to SharedPreferences and exposed as Compose-
 * observable [StateFlow]s.
 *
 * **Why SharedPreferences, not DataStore.** DataStore would be the modern choice, but
 * adding `androidx.datastore:datastore-preferences` is a Gradle-dep add — vibe-dangerous
 * per AGENTS.md and requires an ADR + human review. SharedPreferences has been stable
 * for 15 years, ships with the platform, and for ~5 boolean/string preferences the
 * Flow-based ergonomics of DataStore don't justify the dep cost. If we ever grow into
 * dozens of preferences or need cross-process safety, swap to DataStore in a follow-up.
 *
 * **Why StateFlow, not Flow + .collectAsState.** Compose can subscribe to a StateFlow
 * directly with [androidx.compose.runtime.collectAsState] and immediately receive the
 * current value (no flicker on first composition). SharedPreferences's own listener
 * APIs are clunky; we mirror writes into a MutableStateFlow on the same thread so
 * subscribers see updates instantly without an async hop.
 */
public class ThemePreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    public val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(readDynamicColor())
    public val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    public fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.wireName).apply()
        _themeMode.value = mode
    }

    public fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.AUTO
        // Defensive: a corrupted prefs file (or a downgrade after we add a new enum
        // value) shouldn't crash. Fall back to AUTO on anything we don't recognize.
        return ThemeMode.fromWireName(raw) ?: ThemeMode.AUTO
    }

    private fun readDynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)

    private companion object {
        const val PREFS_NAME = "toebeans_theme_prefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

/**
 * Three-state theme override: follow the system (default), force light, or force dark.
 *
 * `wireName` is the stable string written to SharedPreferences. The enum ordinal would
 * be unstable across version upgrades if values are reordered — string wire names give
 * us safe schema evolution for free.
 */
public enum class ThemeMode(
    public val wireName: String,
) {
    AUTO("auto"),
    LIGHT("light"),
    DARK("dark"),
    ;

    public companion object {
        public fun fromWireName(value: String): ThemeMode? = entries.firstOrNull { it.wireName == value }
    }
}
