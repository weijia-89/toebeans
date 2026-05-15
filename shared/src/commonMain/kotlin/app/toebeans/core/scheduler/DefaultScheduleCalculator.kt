package app.toebeans.core.scheduler

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Default implementation of [ScheduleCalculator].
 *
 * **STATUS: STUB.** This class exists so the v0.1 scaffold compiles. The real implementation is
 * the first concrete piece of vibe-dangerous work that must follow the AGENTS.md test-as-spec
 * protocol:
 *
 *   1. The failing tests (`SchedulePhaseRulesTest`) are already committed.
 *   2. A human reviewer has approved the test signatures (2026-05-15 review; see ADR-0004).
 *   3. An implementer may now fill out [computeScheduledDoses] in a SEPARATE PR.
 *
 * The implementation must:
 *   - Honor all decisions in `ADR-0004` § Test-as-spec review (D1–D7, F5).
 *   - Enforce the mechanical bounds from `ADR-0008` (window ≤ 30d, event-count ≤ 100,000).
 *   - Throw the structured [MalformedScheduleException] subclasses for malformed input.
 *   - Support [SchedulePhase.dayInterval] for skip-day dosing.
 *   - Remain pure-functional and timezone-agnostic at the API surface (the caller chooses TZ
 *     per ADR-0007).
 *
 * Throwing [NotImplementedError] is a load-bearing signal: any attempt to call this stub in
 * production WILL fail loudly. There is no silent fallback.
 */
public class DefaultScheduleCalculator : ScheduleCalculator {
    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> =
        throw NotImplementedError(
            "DefaultScheduleCalculator is a v0.1 stub. " +
                "Implement after the failing test in SchedulePhaseRulesTest is human-approved. " +
                "See AGENTS.md § Test-as-spec rules.",
        )
}
