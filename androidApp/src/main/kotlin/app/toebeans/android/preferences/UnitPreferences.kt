package app.toebeans.android.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide weight unit preference for D6. One value for the whole app, stored with the
 * same SharedPreferences pattern as [ThemePreferences] — no new Gradle dependency.
 *
 * **Why SharedPreferences:** the app already persists theme and first-launch state this
 * way. DataStore would add a dependency, which is a review-gated change per AGENTS.md.
 *
 * **Why pounds is the default:** US owners are the common case; kilograms stays available
 * as an explicit choice for owners who work from clinic sheets. This is presentation
 * only — storage remains the single `weight_kg` column and conversion happens at the UI
 * edge (see [app.toebeans.android.util.WeightConverter]).
 */
public class UnitPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _weightUnit = MutableStateFlow(readWeightUnit())
    public val weightUnit: StateFlow<WeightUnit> = _weightUnit.asStateFlow()

    public fun setWeightUnit(unit: WeightUnit) {
        prefs.edit().putString(KEY_WEIGHT_UNIT, unit.wireName).apply()
        _weightUnit.value = unit
    }

    private fun readWeightUnit(): WeightUnit {
        val raw = prefs.getString(KEY_WEIGHT_UNIT, null) ?: return WeightUnit.POUNDS
        // Defensive: a corrupted or downgraded prefs file must not crash the app.
        return WeightUnit.fromWireName(raw) ?: WeightUnit.POUNDS
    }

    private companion object {
        const val PREFS_NAME = "toebeans_unit_prefs"
        const val KEY_WEIGHT_UNIT = "weight_unit"
    }
}

/**
 * The two weight units the app can show and accept. `wireName` is the stable token in
 * SharedPreferences; `shortLabel` is the field/display abbreviation and `spokenLabel`
 * is the TalkBack expansion. Keeping the three separate means UI copy can change without
 * invalidating a saved preference.
 */
public enum class WeightUnit(
    public val wireName: String,
    public val shortLabel: String,
    public val spokenLabel: String,
) {
    POUNDS("pounds", "lb", "pounds"),
    KILOGRAMS("kilograms", "kg", "kilograms"),
    ;

    public companion object {
        public fun fromWireName(value: String): WeightUnit? = entries.firstOrNull { it.wireName == value }
    }
}
