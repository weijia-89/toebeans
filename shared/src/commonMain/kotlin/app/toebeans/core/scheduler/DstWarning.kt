package app.toebeans.core.scheduler

/**
 * DST transition warning attached to a [ScheduledDose] when the dose time is affected
 * by a daylight-saving transition.
 *
 * - [DST_SKIP]: the dose was scheduled inside a spring-forward gap (e.g., 02:30 on the
 *   day clocks jump from 02:00 to 03:00). The local wall-clock time does not exist;
 *   kotlinx-datetime resolves it to the next valid instant, shifting the dose forward.
 * - [DST_DUPLICATE_RESOLVED]: the dose was scheduled inside a fall-back overlap
 *   (e.g., 01:30 on the day clocks fall back from 02:00 to 01:00). The local wall-clock
 *   time exists twice; the earlier UTC instant is chosen so the dose fires once only.
 *
 * These warnings are surfaced in the reminder list so the user knows a DST transition
 * has shifted or de-duplicated a dose. They are never blocking (the dose still fires).
 *
 * [AnchorMode.ELAPSED_INTERVAL] doses never carry a DST warning because they are
 * anchored to fixed UTC intervals, not local wall-clock time.
 */
public enum class DstWarning {
    DST_SKIP,
    DST_DUPLICATE_RESOLVED,
}
