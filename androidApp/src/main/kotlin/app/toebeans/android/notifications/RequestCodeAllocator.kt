package app.toebeans.android.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * Allocates a stable, collision-free Int request code for each reminder id used as a
 * `PendingIntent` key in [AndroidNotificationActuator].
 *
 * ## Why this exists
 *
 * Before this allocator, `AndroidNotificationActuator` used `reminderId.hashCode()` as the
 * PendingIntent request code. Two different reminder ids can collide on the 32-bit hash; by
 * the birthday paradox, collision probability hits ~50% at ~65,000 concurrent active
 * reminders, but **any** collision in the medication-firing path is unacceptable because the
 * second `schedule()` would silently overwrite the first alarm. A pet on a multi-drug
 * regimen could lose a dose to a hash collision the user has no way to detect.
 *
 * This allocator removes the hash entirely. We issue strictly-monotonic Ints from a counter
 * persisted in [PREFS_FILE_NAME], and we remember the (reminderId → Int) mapping so that
 * re-scheduling or cancelling a reminder reuses its assigned code. The mapping survives
 * process death and reboot.
 *
 * ## Threading
 *
 * `allocate` and `release` are `synchronized(this)` to make the read-then-write
 * (lookup-or-insert) atomic. Contention is low (alarm-firing is human-paced) so the
 * coarse lock is fine. We use `commit()` not `apply()` for the read-then-write because
 * `apply()` is asynchronous and would re-introduce the race we just closed.
 *
 * ## Counter wrap
 *
 * If the counter ever reaches `Int.MAX_VALUE` (2 billion), we wrap back to 0. In practice
 * the user would have to schedule 2 billion distinct reminders without uninstalling — which
 * is ~5,500 years of one-reminder-per-minute. If toebeans ever ships in a vet-clinic-scale
 * deployment we revisit; for caregiver-scale it is impossible to hit.
 *
 * ## Vibe-safety tier
 *
 * This is a **vibe-dangerous** surface per `AGENTS.md` (it sits in
 * `androidApp/.../notifications/` and is on the medication-firing path). Any change requires
 * the test-as-spec protocol: failing test first, human-reviewed assertions, then implementation.
 */
public class RequestCodeAllocator(
    private val prefs: SharedPreferences,
) {
    /**
     * Returns the request code for [reminderId]. If [reminderId] has been seen before, the
     * same code is returned. Otherwise a fresh code is issued from the monotonic counter.
     *
     * Idempotent for repeated calls with the same id.
     */
    @Synchronized
    public fun allocate(reminderId: String): Int {
        val key = reminderKey(reminderId)
        val existing = prefs.getInt(key, SENTINEL_UNASSIGNED)
        if (existing != SENTINEL_UNASSIGNED) {
            return existing
        }
        val next = nextCounter()
        // commit() because we need the read-after-write to be visible synchronously to the
        // next caller; apply() would re-open the race condition.
        prefs.edit().putInt(key, next).putInt(KEY_NEXT_COUNTER, next + 1).commit()
        return next
    }

    /**
     * Removes the (reminderId → Int) mapping. The freed Int is NOT recycled — recycling would
     * risk an ABA condition where the OS still holds a PendingIntent for the prior owner.
     *
     * Calling [release] with an unknown id is a no-op (does not throw).
     */
    @Synchronized
    public fun release(reminderId: String) {
        prefs.edit().remove(reminderKey(reminderId)).commit()
    }

    /**
     * Visible for tests. Returns the next-counter value without allocating. The counter only
     * advances on [allocate].
     */
    internal fun peekNextCounter(): Int = prefs.getInt(KEY_NEXT_COUNTER, INITIAL_COUNTER)

    /**
     * Visible for tests. Returns the assigned code for [reminderId] without allocating, or
     * `null` if none is assigned.
     */
    internal fun peek(reminderId: String): Int? {
        val v = prefs.getInt(reminderKey(reminderId), SENTINEL_UNASSIGNED)
        return if (v == SENTINEL_UNASSIGNED) null else v
    }

    private fun nextCounter(): Int {
        val current = prefs.getInt(KEY_NEXT_COUNTER, INITIAL_COUNTER)
        // Wrap at MAX_VALUE — see KDoc. SENTINEL_UNASSIGNED (Int.MIN_VALUE) is reserved and
        // skipped if we ever land on it on the wrap path.
        return if (current == Int.MAX_VALUE) {
            INITIAL_COUNTER
        } else if (current == SENTINEL_UNASSIGNED) {
            INITIAL_COUNTER
        } else {
            current
        }
    }

    private fun reminderKey(reminderId: String): String = "$KEY_REMINDER_PREFIX$reminderId"

    public companion object {
        public const val PREFS_FILE_NAME: String = "toebeans.notifications.requestcodes"

        // The reminder-id-keyed entries co-exist in the same prefs file as the counter, so
        // prefix them to avoid accidental collision with KEY_NEXT_COUNTER.
        internal const val KEY_REMINDER_PREFIX: String = "rid:"
        internal const val KEY_NEXT_COUNTER: String = "__next"

        // Start at 1, not 0, so the absence of a key (which getInt(key, default) treats as
        // "use the default") is distinguishable from "assigned code 0".
        internal const val INITIAL_COUNTER: Int = 1

        // Int.MIN_VALUE is our "no mapping" sentinel because (a) it's the most negative Int
        // and won't collide with the monotonic counter for ~4.3 billion years, and (b) it's
        // outside the range Android typically uses for request codes.
        internal const val SENTINEL_UNASSIGNED: Int = Int.MIN_VALUE

        /**
         * Factory: builds an allocator backed by the application's private prefs file. Use
         * this from the application/receiver path where you have a [Context] but not a
         * pre-built [SharedPreferences].
         */
        public fun fromContext(context: Context): RequestCodeAllocator =
            RequestCodeAllocator(
                prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE),
            )
    }
}
