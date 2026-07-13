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
 * **ADR-0011 write path:** Write-before-show ordering is integration-tested only in
 * [app.toebeans.android.notifications.DoseAlarmReceiverLookupTest]. Subclasses here prove
 * [assertAdr0011MarkFiredPersists] (SqlDelight: real `markFired`; in-memory: scaffolding only).
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

    protected lateinit var lookup: ReminderLookup

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
                medicationName = "Lookup Med",
                petName = "Lookup Pet",
            )
        seedReminder(expected)

        val found = lookup.lookup("evt-lookup-1")

        assertEquals(expected.id, found?.id)
        assertEquals(expected.scheduleId, found?.scheduleId)
        assertEquals(expected.scheduledAt, found?.scheduledAt)
        // Names come from the seeded parent chain (pet + medication)
        assertEquals("Lookup Med", found?.medicationName)
        assertEquals("Lookup Pet", found?.petName)
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
                medicationName = "Lookup Med",
                petName = "Lookup Pet",
            )
        seedReminder(reminder)
        removeSeededReminder("evt-deleted")

        assertNull(
            lookup.lookup("evt-deleted"),
            "deleted row must map to null (schedule deleted between schedule and fire)",
        )
    }

    @Test
    fun `lookup returns null after parent schedule delete cascades dose event`() {
        val reminder =
            ScheduledReminder(
                id = "evt-cascade-gone",
                scheduleId = "sched-cascade",
                scheduledAt = Instant.parse("2026-05-23T11:00:00Z"),
                medicationName = "Lookup Med",
                petName = "Lookup Pet",
            )
        seedReminder(reminder)
        removeSeededSchedule(reminder.scheduleId)

        assertNull(
            lookup.lookup("evt-cascade-gone"),
            "schedule delete must CASCADE to dose event (ADR-0010 row-gone race at fire time)",
        )
    }

    @Test
    fun `lookup returns null when parent medication is discontinued`() {
        val reminder =
            ScheduledReminder(
                id = "evt-discontinued-med",
                scheduleId = "sched-discontinued",
                scheduledAt = Instant.parse("2026-05-23T13:00:00Z"),
                medicationName = "Lookup Med",
                petName = "Lookup Pet",
            )
        seedReminder(reminder)
        discontinueSeededMedication()

        assertNull(
            lookup.lookup(reminder.id),
            "discontinued medication must not surface at fire time; receiver cancels stale alarm",
        )
    }

    @Test
    fun `lookup returns null when parent pet is archived`() {
        val reminder =
            ScheduledReminder(
                id = "evt-archived-pet",
                scheduleId = "sched-archived-pet",
                scheduledAt = Instant.parse("2026-05-23T14:00:00Z"),
                medicationName = "Lookup Med",
                petName = "Lookup Pet",
            )
        seedReminder(reminder)
        archiveSeededPet()

        assertNull(
            lookup.lookup(reminder.id),
            "archived pet must not surface at fire time; receiver cancels stale alarm",
        )
    }

    @Test
    fun `lookup returns medication and pet names for notification display`() {
        val expected =
            ScheduledReminder(
                id = "evt-enriched-names",
                scheduleId = "sched-enriched",
                scheduledAt = Instant.parse("2026-05-25T09:00:00Z"),
                medicationName = "Lookup Med",
                petName = "Lookup Pet",
            )
        seedReminder(expected)

        val found = lookup.lookup("evt-enriched-names")

        assertEquals("Lookup Med", found?.medicationName, "medication name must be populated")
        assertEquals("Lookup Pet", found?.petName, "pet name must be populated")
    }

    protected open fun removeSeededReminder(reminderId: String) {
        // Default no-op for lookups that cannot simulate deletion yet (stub-throws path).
    }

    protected open fun removeSeededSchedule(scheduleId: String) {
        // Default no-op; SQLDelight subclass deletes the schedule row to exercise FK CASCADE.
    }

    protected open fun discontinueSeededMedication() {
        // Default no-op; SQLDelight subclass stamps discontinued_at on the seeded medication.
    }

    protected open fun archiveSeededPet() {
        // Default no-op; SQLDelight subclass stamps archived_at on the seeded pet.
    }

    /**
     * ADR-0011 persistence: [SqlDelightDoseEventRepository.markFired] stamps the row.
     * Receiver write-before-show ordering is integration-tested in androidApp only.
     */
    @Test
    fun `ADR-0011 markFired persists fired_at on dose row`() {
        assertAdr0011MarkFiredPersists()
    }

    protected abstract fun assertAdr0011MarkFiredPersists()
}

/**
 * GREEN contract subclass backed by an in-memory map. Proves read-path assertions are well-formed
 * before SQLDelight lands. ADR-0011 write-before-show ordering is **not** simulated here — see
 * [app.toebeans.android.notifications.DoseAlarmReceiverLookupTest].
 */
class InMemoryReminderLookupContractTest : ReminderLookupContract() {
    private val store = mutableMapOf<String, ScheduledReminder>()
    private var medicationDiscontinued = false
    private var petArchived = false

    override fun createLookup(): ReminderLookup {
        medicationDiscontinued = false
        petArchived = false
        store.clear()
        return object : ReminderLookup {
            override fun lookup(reminderId: String): ScheduledReminder? {
                if (medicationDiscontinued || petArchived) return null
                return store[reminderId]
            }
        }
    }

    override fun seedReminder(reminder: ScheduledReminder) {
        store[reminder.id] = reminder
    }

    override fun removeSeededReminder(reminderId: String) {
        store.remove(reminderId)
    }

    override fun removeSeededSchedule(scheduleId: String) {
        store.entries.removeIf { (_, reminder) -> reminder.scheduleId == scheduleId }
    }

    override fun discontinueSeededMedication() {
        medicationDiscontinued = true
    }

    override fun archiveSeededPet() {
        petArchived = true
    }

    override fun assertAdr0011MarkFiredPersists() {
        // sdk-review F2: in-memory cannot prove receiver ordering; androidApp owns that falsifier.
        val reminder =
            ScheduledReminder(
                id = "evt-adr-inmem",
                scheduleId = "sched-adr",
                scheduledAt = Instant.parse("2026-05-26T08:00:00Z"),
                medicationName = "TestMed",
                petName = "TestPet",
            )
        seedReminder(reminder)
        assertEquals(reminder, lookup.lookup(reminder.id))
    }
}
