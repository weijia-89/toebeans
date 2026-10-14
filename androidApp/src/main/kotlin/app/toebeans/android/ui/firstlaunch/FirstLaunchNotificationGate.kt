package app.toebeans.android.ui.firstlaunch

/** API 33 is the first release that requires a runtime notification grant. */
public const val POST_NOTIFICATIONS_MIN_SDK: Int = 33

/**
 * First-run Today copy. There is no fired dose yet, so this is not the
 * ADR-0011 "last dose alarm" sentence.
 */
public const val FIRST_RUN_NOTIFICATION_DENIAL_COPY: String =
    "Notifications are off, so a dose alarm will not show. Tap to fix in Settings."

public enum class FirstLaunchChoice {
    START_EMPTY,
    LOAD_DEMO,
}

/**
 * What a dialog button does before the dialog may close.
 * [finishNow] is null while the permission request is still open.
 */
public data class FirstLaunchTap(
    val requestPermission: Boolean,
    val finishNow: FirstLaunchChoice?,
)

public data class FirstLaunchPermissionFinish(
    val choice: FirstLaunchChoice,
    val showFirstRunBanner: Boolean,
)

public fun firstLaunchTap(
    sdkInt: Int,
    granted: Boolean,
    choice: FirstLaunchChoice,
): FirstLaunchTap {
    val request = sdkInt >= POST_NOTIFICATIONS_MIN_SDK && !granted
    return if (request) {
        FirstLaunchTap(requestPermission = true, finishNow = null)
    } else {
        FirstLaunchTap(requestPermission = false, finishNow = choice)
    }
}

public fun firstLaunchPermissionResult(
    granted: Boolean,
    choice: FirstLaunchChoice,
): FirstLaunchPermissionFinish = FirstLaunchPermissionFinish(choice = choice, showFirstRunBanner = !granted)
