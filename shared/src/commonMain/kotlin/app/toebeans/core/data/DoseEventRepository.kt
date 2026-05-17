package app.toebeans.core.data

import app.toebeans.core.model.DoseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Repository contract for [DoseEvent] CRUD and observation.
 *
 * Scoping at the read API surface — by pet for "all this pet's history" and by
 * medication for "the last given dose of this specific drug" — is intentional: callers
 * don't want to reason about schedule IDs. The SQLDelight queries
 * (`selectDoseEventsForPetSince`) join through medication + schedule under the hood.
 *
 * **Materialization posture.** DoseEvents are NOT pre-generated. A future scheduling
 * service (in `core/scheduler`, not here) will project the next 72 hours of pending
 * doses lazily on demand. Pre-generating an entire phase would be a write-storm
 * anti-pattern flagged as **vibe-impossible** in AGENTS.md.
 *
 * **v0.1 quick-log scope.** The full "pending → given/skipped/missed" lifecycle waits
 * for the schedule materializer (next milestone). For v0.1, [recordGivenNow] inserts
 * a synthetic GIVEN event tied to an existing schedule, with `scheduledAt = resolvedAt
 * = the moment the user tapped "Log dose"`. This serves the central daily action
 * — "I just gave Luna her pill" — without requiring the schedule calculator to
 * project upcoming doses first. The owner gets a working log; the projection layer
 * arrives in a follow-up.
 */
public interface DoseEventRepository {
    /**
     * Observe dose events for a single pet, newest first, since [sinceInclusive].
     *
     * Use [Instant.DISTANT_PAST] for "everything ever recorded" — but Pet detail
     * history typically wants a 30-day window for performance.
     */
    public fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>>

    /**
     * Observe the most recent GIVEN dose for a medication. Returns null until the
     * first dose is recorded. Skipped and missed events do not count as a "last dose".
     *
     * Powers the "Last dose: 2h ago" indicator on Pet detail medication rows.
     */
    public fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?>

    /**
     * Observe all GIVEN dose events across every pet since [sinceInclusive], newest
     * first. Skipped and missed events are excluded — the Home "Logged today" surface
     * is a record of completed care, not a worklist.
     *
     * Powers the Today-screen "Logged today" card. Callers typically pass
     * `today at 00:00 local`, but the API accepts any window so future surfaces
     * ("last 7 days") can share the same query.
     *
     * Implementations should bound the returned list to a reasonable size if the
     * underlying store could be very large. For v0.1 the in-memory store is small;
     * the SQLDelight impl will add a `LIMIT 50` and matching ORDER BY index.
     */
    public fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>>

    /**
     * Record an ad-hoc GIVEN dose against [scheduleId] at [at]. Caller supplies the
     * unique [doseEventId] (UUID). status=GIVEN, scheduledAt=resolvedAt=at.
     *
     * The conflation of `scheduledAt` and `resolvedAt` is intentional for the Pet
     * Detail "Log dose now" surface, which has no slot context — the user is just
     * recording "I gave Luna her pill, more or less now." For mark-taken on a known
     * schedule slot (Today screen worklist) use [recordGivenForSlot] instead, so the
     * stored `scheduledAt` reflects the intended slot and `resolvedAt` reflects the
     * actual wall-clock tap.
     *
     * v0.1 caveat: the caller is responsible for ensuring [scheduleId] exists. We
     * don't validate FK here — that's the SQLDelight backing's job and will fail
     * loudly via [IllegalArgumentException] in the real impl. The fake impl is
     * permissive.
     */
    public suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        at: Instant,
        note: String? = null,
    ): DoseEvent

    /**
     * Record a GIVEN dose against a specific scheduled slot. [scheduledAt] is the
     * slot's intended time as computed by [app.toebeans.core.scheduler.ScheduleCalculator];
     * [resolvedAt] is the wall-clock tap time (typically `Clock.System.now()`). status=GIVEN.
     *
     * Used by the Today-screen worklist to fulfill a specific calculator-projected dose
     * row. Keeping the slot time on `scheduledAt` is what lets the worklist match a
     * pending [app.toebeans.core.scheduler.ScheduledDose] against a recorded
     * [DoseEvent] for the same slot.
     *
     * If a GIVEN event already exists for this `(scheduleId, scheduledAt)` pair the
     * implementation should treat the call as idempotent — replace the existing event
     * with the new [resolvedAt]. This is more useful than throwing: a user double-tap
     * shouldn't crash, and there's no reason to support multiple GIVEN events for the
     * same slot.
     */
    public suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String? = null,
    ): DoseEvent

    /** Delete a recorded dose event. Used to support an Undo affordance. */
    public suspend fun delete(doseEventId: String)
}
