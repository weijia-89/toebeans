package app.toebeans.core.notifications

import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Abstract test-as-spec for the [ReminderLookup] port (M1 ROADMAP sequencing item 4 prep).
 *
 * **Contract (followups § 3 + ADR-0011 read path):**
 * - [lookup] maps a persisted dose-event id to [ScheduledReminder.id], [ScheduledReminder.scheduleId],
 *   and [ScheduledReminder.scheduledAt].
 * - [lookup] returns null when the row is gone (user deleted the schedule between scheduling
 *   and firing). Callers cancel silently; no exception.
 *
 * **ADR-0011 write path (out of scope here):** `DoseEvent.fired_at = now()` before
 * [NotificationActuator.show] is a separate wire-up slice once SQLDelight is reachable from the
 * receiver process. Tests in this file do not assert fired_at ordering yet.
 *
 * **Phase 1 (this PR):** [InMemoryReminderLookupContractTest] exercises the contract against an
 * in-memory fake so reviewers can approve assertions before SQLDelight lands.
 *
 * **Phase 2 (M1.3 SQLDelight):** [SqlDelightReminderLookupContractTest] in `:shared:jvmTest`
 * exercises the driver-backed impl.
 */
abstract class ReminderLookupContract {
    protected abstract fun createLookup(): ReminderLookup

    protected abstract fun seedReminder(reminder: ScheduledReminder)

    private lateinit var lookup: ReminderLookup

    @BeforeTest
    fun setupLookup() {
        lookup = createLookup()
    }

    @Test
    fun `lookup returns ScheduledReminder fields for a persisted row`() {
        val expected =
            ScheduledReminder(
                id = "evt-lookup-1",
                scheduleId = "sched-luna-methimazole",
                scheduledAt = Instant.parse("2026-05-23T08:00:00Z"),
            )
        seedReminder(expected)

        val found = lookup.lookup("evt-lookup-1")

        assertEquals(expected, found)
    }

    @Test
    fun `lookup returns null when reminderId is unknown (row gone)`() {
        assertNull(
            lookup.lookup("evt-never-seeded"),
            "absent row must map to null so the receiver can silently cancel",
        )
    }

    @Test
    fun `lookup returns null after seeded row is removed`() {
        val reminder =
            ScheduledReminder(
                id = "evt-deleted",
                scheduleId = "sched-1",
                scheduledAt = Instant.parse("2026-05-23T12:00:00Z"),
            )
        seedReminder(reminder)
        removeSeededReminder("evt-deleted")

        assertNull(
            lookup.lookup("evt-deleted"),
            "deleted row must map to null (schedule deleted between schedule and fire)",
        )
    }

    protected open fun removeSeededReminder(reminderId: String) {
        // Default no-op for lookups that cannot simulate deletion yet (stub-throws path).
    }
}

/**
 * GREEN contract subclass backed by an in-memory map. Proves the assertions are well-formed
 * before SQLDelight lands.
 */
class InMemoryReminderLookupContractTest : ReminderLookupContract() {
    private val store = mutableMapOf<String, ScheduledReminder>()

    override fun createLookup(): ReminderLookup =
        object : ReminderLookup {
            override fun lookup(reminderId: String): ScheduledReminder? = store[reminderId]
        }

    override fun seedReminder(reminder: ScheduledReminder) {
        store[reminder.id] = reminder
    }

    override fun removeSeededReminder(reminderId: String) {
        store.remove(reminderId)
    }
}
