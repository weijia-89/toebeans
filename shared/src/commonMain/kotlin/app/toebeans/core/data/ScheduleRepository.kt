package app.toebeans.core.data

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Repository contract for [Schedule] and its phases.
 *
 * A schedule is always persisted with its phases atomically — partial schedules are not a
 * valid state. [upsert] takes both the schedule row and the full ordered phase list.
 */
public interface ScheduleRepository {
    /** Observe all schedules for a single medication. */
    public fun observeForMedication(medicationId: String): Flow<List<Schedule>>

    /**
     * Observe a single schedule by id, or `null` if it no longer exists.
     *
     * Mirrors [PetRepository.observeById]. Used by surfaces that hold a schedule id
     * directly (e.g. the Schedule Detail screen reached via the Reminder List), where
     * we want the consumer to react to deletes without manually polling.
     */
    public fun observeById(id: String): Flow<Schedule?>

    /** Observe all phases for a schedule, ordered by [SchedulePhase.phaseOrder]. */
    public fun observePhases(scheduleId: String): Flow<List<SchedulePhase>>

    /**
     * Observe every schedule whose effective range overlaps [onOrAfter], bundled with its
     * phases. "Active" means `endDate == null OR endDate >= onOrAfter` — a schedule that
     * ended yesterday is NOT active for today's worklist.
     *
     * This is the cross-pet read that the Today screen's due-doses worklist runs through
     * the [app.toebeans.core.scheduler.ScheduleCalculator]. The SQLDelight impl will use
     * a single `WHERE end_date IS NULL OR end_date >= ?` query with a LEFT JOIN to phases.
     * Phase 2 of the UI build calls this; the persistence-layer SQL implementation lands
     * in milestone 1.
     */
    public fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>>

    /**
     * Insert or update a schedule with its phases. The phases must form a valid
     * dense 0..N-1 phaseOrder sequence — caller-side validation responsibility per the
     * test-as-spec contract on the scheduler. The repository will throw
     * IllegalArgumentException if the phases are inconsistent.
     */
    public suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    )

    /** Hard delete a schedule and its phases. */
    public suspend fun delete(id: String)
}

/**
 * A schedule with its phases attached. Bundled so the
 * [app.toebeans.core.scheduler.ScheduleCalculator] (which takes both arguments) can be
 * fed from a single repository emission instead of joining two flows in the consumer.
 */
public data class ScheduleWithPhases(
    public val schedule: Schedule,
    public val phases: List<SchedulePhase>,
)
