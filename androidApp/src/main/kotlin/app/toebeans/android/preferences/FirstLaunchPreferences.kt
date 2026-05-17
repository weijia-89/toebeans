package app.toebeans.android.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the user has seen the first-launch onboarding dialog.
 *
 * **Why this exists.** Before M1.2 the in-memory fake repositories always launched with
 * the Luna + Rufus demo pets seeded. That was useful for stakeholder review but confusing
 * for real testers who saw pre-existing pets they had not created. This preference lets
 * us default to a clean slate and ask the user once whether they want to load demo data.
 *
 * **Storage.** Same SharedPreferences pattern as [ThemePreferences] (see that file for the
 * SharedPreferences-vs-DataStore rationale). One boolean: `first_launch_seen`. Once true,
 * stays true forever — re-prompting on every clear-data event would be annoying.
 *
 * **Why not use the demo-loaded flag too?** The demo data is in-memory only (the fake
 * repos lose state on process death) so persisting `demo_loaded = true` would lie about
 * whether the demo data is actually still resident. We only persist the `seen` bit.
 */
public class FirstLaunchPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _firstLaunchSeen = MutableStateFlow(prefs.getBoolean(KEY_FIRST_LAUNCH_SEEN, false))

    /**
     * True once the user has acknowledged the first-launch dialog (whichever button they
     * tapped). Compose surfaces should subscribe via [androidx.compose.runtime.collectAsState]
     * and only render the dialog when this is false.
     */
    public val firstLaunchSeen: StateFlow<Boolean> = _firstLaunchSeen.asStateFlow()

    /** Idempotent; safe to call from either dialog button or after demo seeding. */
    public fun markSeen() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_SEEN, true).apply()
        _firstLaunchSeen.value = true
    }

    /**
     * Visible-for-tests. Resets the flag so the next test case sees a clean first-launch
     * state. Production code MUST NOT call this — there is no user-facing "reset
     * onboarding" surface (and we don't want one in v0.1; re-prompting on every reinstall
     * is a soak-test annoyance).
     */
    internal fun resetForTest() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_SEEN, false).commit()
        _firstLaunchSeen.value = false
    }

    private companion object {
        const val PREFS_NAME = "toebeans_first_launch_prefs"
        const val KEY_FIRST_LAUNCH_SEEN = "first_launch_seen"
    }
}
