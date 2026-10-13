package app.toebeans.core.notifications

/**
 * ADR-0011 Today banner. The sentence is fixed.
 */
public const val NOTIFICATION_DENIAL_BANNER_COPY: String =
    "Your last dose alarm did not show as a notification because notifications are disabled for toebeans. Tap to fix in Settings, or open the History tab to see what fired."

/**
 * One of the latest dose rows. [fired] is `fired_at` set. [delivered] is a
 * notification_audit row for that dose.
 *
 * Callers pass newest first. Only the first ten rows count.
 */
public data class RecentDoseDelivery(
    val fired: Boolean,
    val delivered: Boolean,
)

public fun notificationDenialBannerVisible(recent: List<RecentDoseDelivery>): Boolean =
    recent.take(10).any { it.fired && !it.delivered }

/** Reads whether the Today banner should show. The Android module binds SQLDelight. */
public fun interface UndeliveredDoseAlarmQuery {
    public fun hasUndeliveredFiredDose(): Boolean
}
