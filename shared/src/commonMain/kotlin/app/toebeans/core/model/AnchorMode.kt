package app.toebeans.core.model

/**
 * Determines how a schedule's dose times are anchored relative to timezone changes.
 *
 * Per ADR-0007:
 * - [FOLLOW_PHONE]: wall-clock local; shifts with the phone's timezone (default).
 * - [STAY_HOME_TZ]: follows the pet's home timezone; the phone is just the display.
 * - [ELAPSED_INTERVAL]: fixed UTC interval from the first dose; wall-clock irrelevant.
 */
public enum class AnchorMode {
    FOLLOW_PHONE,
    STAY_HOME_TZ,
    ELAPSED_INTERVAL,
}
