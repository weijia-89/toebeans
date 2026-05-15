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
 *   1. The failing test (`SchedulePhaseRulesTest`) is already committed.
 *   2. A human reviewer must approve the test signature.
 *   3. Only then may an implementer fill out [computeScheduledTimes] and re-run the test.
 *
 * Throwing [NotImplementedError] is a load-bearing signal: any attempt to call this in
 * production WILL fail loudly. There is no silent fallback.
 */
public class DefaultScheduleCalculator : ScheduleCalculator {
    override fun computeScheduledTimes(
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
