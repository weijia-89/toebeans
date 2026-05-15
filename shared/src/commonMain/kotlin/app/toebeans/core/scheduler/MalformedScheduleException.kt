package app.toebeans.core.scheduler

import kotlinx.datetime.Instant

/**
 * Structured exception family for `ScheduleCalculator.computeScheduledDoses` rejections.
 *
 * Designed for machine ingestion (e.g., an AI agent reading a stack trace, a crash reporter,
 * or a UI error mapper):
 *
 *  - Every subclass carries **typed fields** for the offending values, not just a string.
 *  - The string [message] is human-readable but the discriminator is the **subclass type**.
 *  - Every subclass extends [IllegalArgumentException] so legacy `catch` blocks still work.
 *  - [code] returns the stable string identifier (the subclass simple name); tests and log
 *    aggregators should key off this, never off the human-readable message.
 *
 * **Adding a new subclass requires:**
 *  1. A test in `SchedulePhaseRulesTest` (or its sibling test classes) that asserts the new
 *     subclass is thrown.
 *  2. A KDoc entry on the relevant `computeScheduledDoses` clause documenting when it fires.
 *  3. A calibration entry per AGENTS.md (scheduler is vibe-dangerous).
 *
 * Why a sealed class hierarchy over an enum + map:
 *  - Exhaustive `when` matching at the catch-site catches missing handlers at compile time.
 *  - Each subclass can carry distinct fields with their natural Kotlin types.
 *  - IDE and AI-agent code-completion surface the full set of failure modes when reading the
 *    file, without needing to traverse a separate enum + payload mechanism.
 */
public sealed class MalformedScheduleException(
    message: String,
) : IllegalArgumentException(message) {
    /**
     * Stable string identifier for this error. Equal to the subclass simple name (e.g.
     * `"DuplicatePhaseOrder"`). Use this for log keys, metrics dimensions, and AI-agent
     * structured ingestion. **Do not key off [message] — that text may change for clarity.**
     */
    public val code: String
        get() = this::class.simpleName ?: "Unknown"

    /**
     * Two or more phases share the same `phaseOrder`. Schedule semantics require unique
     * ordering; silently honoring one phase and ignoring the other is a medication-critical
     * bug class.
     */
    public class DuplicatePhaseOrder(
        public val phaseOrder: Int,
        public val phaseIds: List<String>,
    ) : MalformedScheduleException(
            "duplicate phaseOrder=$phaseOrder shared by ${phaseIds.size} phases: $phaseIds",
        )

    /**
     * The sorted `phaseOrder` values are not the dense sequence `0, 1, 2, ..., N-1`.
     * E.g., `[0, 2]` (missing 1) or `[1, 2]` (missing 0). The calculator does not interpolate
     * missing phases — that is a UI-level recovery decision, not a calculator decision.
     */
    public class PhaseOrderGap(
        public val phaseOrders: List<Int>,
    ) : MalformedScheduleException(
            "phaseOrders $phaseOrders are not a dense 0..N sequence",
        )

    /**
     * The materialization window `[fromInclusive, toExclusive)` is degenerate or inverted.
     * Specifically, `fromInclusive >= toExclusive`. Treated as a programmer error rather than
     * an empty-result case to surface the bug at the caller site.
     */
    public class WindowNotPositive(
        public val fromInclusive: Instant,
        public val toExclusive: Instant,
    ) : MalformedScheduleException(
            "window must satisfy fromInclusive < toExclusive (was from=$fromInclusive to=$toExclusive)",
        )

    /**
     * The materialization window exceeds the ADR-0008 mechanical cap. Prevents a 30-year query
     * from allocating hundreds of thousands of events on a 96 MB heap budget.
     *
     * The cap value is intentionally exposed on the exception so a UI mapper can show the
     * user "you asked for too long a window; max is N days" without re-parsing the message.
     */
    public class WindowTooLarge(
        public val requestedDays: Long,
        public val maxDays: Int,
    ) : MalformedScheduleException(
            "window=${requestedDays}d exceeds maximum ${maxDays}d (ADR-0008 perf-class cap)",
        )

    /**
     * The calculator would allocate more events than the ADR-0008 safety cap. This can fire
     * even when the window cap is respected, e.g., a 30-day window × 6 doses/day × 10 phases
     * with `dayInterval=1` = 1,800 events (well under the cap), but a future feature that
     * loosens caps could blow this.
     *
     * Defense in depth: the window+dosesPerDay+dayInterval bounds normally make this
     * unreachable. If you see it fire, a calculator invariant is broken upstream.
     */
    public class EventCountExceeded(
        public val attemptedCount: Long,
        public val maxCount: Int,
    ) : MalformedScheduleException(
            "would allocate $attemptedCount events; max is $maxCount (ADR-0008 perf-class cap)",
        )
}
