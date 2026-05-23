package app.toebeans.core.notifications

/**
 * Receiver-side port that resolves a firing alarm's [reminderId] (a [DoseEvent.id]) to the
 * [ScheduledReminder] the platform actuator needs at fire-time.
 *
 * Vibe-dangerous: this sits on the medication-critical fire path. Changes MUST follow the
 * test-as-spec protocol in AGENTS.md.
 *
 * **Separate-process constraint.** [android.content.BroadcastReceiver] instances run outside
 * the foreground Koin graph, so implementations MUST open authoritative persistence directly
 * (SQLDelight driver in M1.3) rather than relying on in-memory fakes.
 *
 * **Row-gone race.** When the user deletes a schedule (or the parent medication cascades)
 * between [NotificationActuator.schedule] and the alarm firing, [lookup] returns null.
 * Callers MUST silently cancel the pending alarm and return without crashing — see
 * `docs/issues/v0.1-followups.md` § 3.
 *
 * **ADR-0011 ordering (deferred).** The wire-up PR for post-notifications denial UX writes
 * `DoseEvent.fired_at` synchronously before calling [NotificationActuator.show]. That write
 * belongs in the receiver dispatch path once SQLDelight is reachable here; this port covers
 * only the read side today.
 */
public interface ReminderLookup {
    /**
     * Resolve [reminderId] to the current [ScheduledReminder] snapshot.
     *
     * @return null when no row exists (deleted schedule, stale alarm, or not yet materialized).
     */
    public fun lookup(reminderId: String): ScheduledReminder?
}
