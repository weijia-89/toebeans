package app.toebeans.android.ui.home

import app.toebeans.android.notifications.AndroidNotificationActuator
import app.toebeans.core.notifications.NotificationDegraded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Today-screen flag for ADR-0002 exact-alarm denial.
 *
 * Two writers, one flag:
 * - [note] is the actuator signal ([NotificationDegraded.EXACT_ALARM_DENIED])
 *   from `setDegradedHandler`.
 * - [setDenied] is a foreground read of `AlarmManager.canScheduleExactAlarms`.
 *   Boot and the dose receiver build their own actuators, so the signal alone
 *   would stay invisible until the next in-process schedule. The read shows the
 *   banner on open and clears it after the user grants the permission.
 */
public class ExactAlarmReliability {
    private val _exactAlarmDenied = MutableStateFlow(false)
    public val exactAlarmDenied: StateFlow<Boolean> = _exactAlarmDenied.asStateFlow()

    public fun note(reason: NotificationDegraded) {
        if (reason == NotificationDegraded.EXACT_ALARM_DENIED) {
            _exactAlarmDenied.value = true
        }
    }

    public fun setDenied(denied: Boolean) {
        _exactAlarmDenied.value = denied
    }

    public companion object {
        /**
         * API 31+ is the first release where the user can deny exact alarms.
         * [canScheduleExactAlarms] is ignored when that gate is closed.
         */
        public fun denied(
            requiresExactAlarmPermission: Boolean,
            canScheduleExactAlarms: Boolean,
        ): Boolean = requiresExactAlarmPermission && !canScheduleExactAlarms
    }
}

/**
 * Copy for the Today banner. The minute count is the actuator fallback window
 * (ADR-0002), so the sentence stays aligned with `setWindow`.
 */
public val EXACT_ALARM_DENIED_MESSAGE: String =
    "Exact alarms are off, so a dose can fire up to " +
        "${AndroidNotificationActuator.FALLBACK_WINDOW_MILLIS / 60_000} minutes late. " +
        "Turn on Alarms and reminders for toebeans."
