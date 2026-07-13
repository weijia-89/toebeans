package app.toebeans.core.notifications

import kotlinx.datetime.Instant

/**
 * Schedules and surfaces medication-reminder notifications. The KMP interface; platform-specific
 * implementations live in `:androidApp` (and a future iOS source set).
 *
 * Vibe-dangerous: changes here MUST follow the test-as-spec protocol in AGENTS.md.
 *
 * Contract:
 *  - **Idempotency.** Calling [schedule] with the same [ScheduledReminder.id] twice MUST result
 *    in exactly one pending alarm at the second call's [ScheduledReminder.scheduledAt]. The
 *    implementation may achieve this by cancel-then-set or by overwriting.
 *  - **Exactness.** Reminders MUST fire within ±60 seconds of [ScheduledReminder.scheduledAt]
 *    on a device whose battery optimization is disabled for toebeans (the documented user
 *    instruction). Implementations are free to fall back to less-exact APIs when the user
 *    has denied the `SCHEDULE_EXACT_ALARM` permission, but MUST raise a [NotificationDegraded]
 *    callback (TBD slice 1) so the UI can surface a warning.
 *  - **No payload.** The reminder content is built at fire-time from the database, not embedded
 *    in the alarm intent. This avoids stale data and keeps the on-disk PendingIntent small.
 */
public interface NotificationActuator {
    /**
     * Schedule a reminder. Replaces any prior reminder with the same [ScheduledReminder.id].
     */
    public fun schedule(reminder: ScheduledReminder)

    /**
     * Cancel any pending reminder with the given [reminderId]. No-op if none is scheduled.
     */
    public fun cancel(reminderId: String)

    /**
     * Surface the notification immediately. Called from the platform alarm callback.
     *
     * Implementations should populate the notification's content from the current database
     * state (medication name, dose amount, pet name) rather than from the [ScheduledReminder]
     * captured at scheduling time — the data may have changed.
     */
    public fun show(reminder: ScheduledReminder)
}

/**
 * The minimal data the actuator carries from schedule-time to fire-time.
 *
 * Intentionally NOT a snapshot of the full [app.toebeans.core.model.DoseEvent] —
 * implementations re-fetch authoritative data at fire-time. See [NotificationActuator.show].
 *
 * **Pedagogical note on denormalization:** `medicationName` and `petName` are denormalized
 * onto this struct (computed at lookup time via JOIN) rather than stored as foreign keys on
 * DoseEvent. This is the right design for the receiver lookup path because:
 * - The receiver runs in a separate process where joining is cheap (local SQLite).
 * - We avoid a three-table join (DoseEvent → Schedule → Medication → Pet) at display time.
 * - The tradeoff: if the medication/pet is renamed after scheduling, the notification shows
 *   the new name. For address verification (Lob's use case), this is analogous to how you
 *   want the latest address on file, not a frozen snapshot.
 *
 * **JOIN-at-read vs denormalize-at-write trade-off:**
 * - JOIN-at-read (what we do): Always fresh data, no sync burden, simpler schema.
 * - Denormalize-at-write: Faster reads, but requires updating historical rows when names change.
 * At our current scale (~100 dose events), JOIN cost is negligible. At 10x scale (10K+ rows),
 * we'd denormalize at write time to keep notification delivery fast.
 */
public data class ScheduledReminder(
    val id: String,
    val scheduleId: String,
    val scheduledAt: Instant,
    val medicationName: String,
    val petName: String,
)
