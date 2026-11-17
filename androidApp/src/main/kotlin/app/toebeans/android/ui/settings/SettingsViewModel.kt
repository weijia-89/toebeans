package app.toebeans.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.android.data.DemoDataIds
import app.toebeans.android.preferences.ThemeMode
import app.toebeans.android.preferences.ThemePreferences
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.notifications.NotificationActuator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings ViewModel — exposes [ThemePreferences] as read/write Compose state and
 * wires the SettingsScreen toggles to the underlying SharedPreferences-backed store.
 * Also owns the "Remove demo data" destructive action (S3).
 *
 * The VM exists so the Composable can be tested without a real Application Context
 * (SharedPreferences requires Context, ThemePreferences can be faked in tests by
 * passing a stub Context).
 *
 * Why a separate VM rather than calling Koin from the Composable directly: keeps the
 * pattern consistent with every other screen (Pets, Home, etc. all use koinViewModel),
 * which means future additions (export data, clear data, etc.) have a clear home.
 */
public class SettingsViewModel(
    private val prefs: ThemePreferences,
    private val petRepository: PetRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
    private val notificationActuator: NotificationActuator,
) : ViewModel() {
    public val themeMode: StateFlow<ThemeMode> = prefs.themeMode
    public val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor

    public fun setThemeMode(mode: ThemeMode): Unit = prefs.setThemeMode(mode)

    public fun setDynamicColor(enabled: Boolean): Unit = prefs.setDynamicColor(enabled)

    /**
     * Remove the demo data (Rufus and Luna, their medications, schedules, and dose
     * history) in one confirmed action.
     *
     * Demo rows are identified by the fixed id set in [DemoDataIds]; the schema does not
     * tag demo rows, so this is exactly the documented demo id set and nothing else.
     *
     * Pending demo dose alarms are cancelled through the actuator BEFORE any row is
     * deleted. Existing in-app delete flows rely on the receiver's row-gone silent
     * cancel; this flow cancels proactively so no demo alarm can fire mid-delete.
     *
     * Schedules are deleted first, then medications, then pets. The database's foreign
     * key cascades remove the demo phases and demo dose rows with them.
     *
     * Safe to call when no demo rows exist: deleting an absent id is a no-op in both the
     * in-memory fakes and the SQLite repositories.
     *
     * [app.toebeans.android.preferences.FirstLaunchPreferences] is NOT reset:
     * `firstLaunchSeen` stays true, so the first-launch dialog does not reappear after a
     * clear. There is no user-facing reset-onboarding surface.
     */
    public fun clearDemoData() {
        viewModelScope.launch {
            doseEventRepository
                .observeAll()
                .first()
                .filter { it.status == DoseStatus.PENDING && it.scheduleId in DemoDataIds.scheduleIds }
                .forEach { notificationActuator.cancel(it.id) }
            DemoDataIds.scheduleIds.forEach { scheduleRepository.delete(it) }
            DemoDataIds.medicationIds.forEach { medicationRepository.delete(it) }
            DemoDataIds.petIds.forEach { petRepository.delete(it) }
        }
    }
}
