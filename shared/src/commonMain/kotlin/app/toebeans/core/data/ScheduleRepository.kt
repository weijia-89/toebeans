package app.toebeans.core.data

import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for [Schedule] and its phases.
 *
 * A schedule is always persisted with its phases atomically — partial schedules are not a
 * valid state. [upsert] takes both the schedule row and the full ordered phase list.
 */
public interface ScheduleRepository {
    /** Observe all schedules for a single medication. */
    public fun observeForMedication(medicationId: String): Flow<List<Schedule>>

    /** Observe all phases for a schedule, ordered by [SchedulePhase.phaseOrder]. */
    public fun observePhases(scheduleId: String): Flow<List<SchedulePhase>>

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
