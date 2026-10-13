package app.toebeans.android.ui.reliability

import app.toebeans.core.notifications.NotificationDegraded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * API level where exact-alarm permission exists.
 * Matches `Build.VERSION_CODES.S`. A plain Int so the rule can be tested
 * without an Android runtime.
 */
public const val EXACT_ALARM_PERMISSION_MIN_SDK: Int = 31

/**
 * True when this device requires exact-alarm permission and the user has not granted it.
 * Below API 31 the permission does not exist, so the banner stays hidden.
 */
public fun exactAlarmDenied(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
): Boolean = sdkInt >= EXACT_ALARM_PERMISSION_MIN_SDK && !canScheduleExactAlarms

/**
 * Process-wide flag for the downgraded-reliability banner (ADR-0002).
 *
 * Two inputs, one flag:
 *  - [refresh] reads the current exact-alarm permission. Today calls this on resume
 *    so granting the permission in system Settings clears the banner.
 *  - [onDegraded] receives [NotificationDegraded.EXACT_ALARM_DENIED] when the
 *    actuator falls back to a 10 minute window. That signal turns the banner on
 *    immediately, without waiting for the next resume.
 *
 * The banner is not dismissible. It stays up while the flag is true.
 */
public class ExactAlarmReliability(
    private val sdkInt: Int,
    private val canScheduleExactAlarms: () -> Boolean,
) {
    private val _denied = MutableStateFlow(exactAlarmDenied(sdkInt, canScheduleExactAlarms()))

    public val denied: StateFlow<Boolean> = _denied.asStateFlow()

    public fun refresh() {
        _denied.value = exactAlarmDenied(sdkInt, canScheduleExactAlarms())
    }

    public fun onDegraded(signal: NotificationDegraded) {
        if (signal == NotificationDegraded.EXACT_ALARM_DENIED) {
            _denied.value = true
        }
    }
}
