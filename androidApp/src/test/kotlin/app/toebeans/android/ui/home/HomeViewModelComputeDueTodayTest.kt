package app.toebeans.android.ui.home

import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import app.toebeans.core.scheduler.ScheduleCalculator
import app.toebeans.core.scheduler.ScheduledDose
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HomeViewModel.Companion.computeDueToday] — the pure projection of
 * calculator output + recorded GIVEN events into the Home worklist's [DueDoseUi] rows.
 *
 * Strategy: hand-rolled [ScheduleCalculator] fake. The calculator's own contract is
 * locked down by the test-as-spec in `:shared`; here we focus on the projection +
 * matching logic that lives in the ViewModel layer.
 *
 * Test cases cover:
 *  - The slot-identity match rule `(scheduleId, scheduledAt)` for pending↔given flips.
 *  - Stale-row guard behavior in debug builds — a schedule referencing a missing
 *    medication or pet throws via `StaleEventGuard` to surface join bugs in CI. Release
 *    mode's log+skip path is tested at the guard's own test surface.
 *  - The doseAmount fallback to the Medication when a phase doesn't override.
 *  - The cross-schedule global sort by `scheduledAt`.
 *  - The status filter — only GIVEN events match (SKIPPED/MISSED don't flip a row).
 */
class HomeViewModelComputeDueTodayTest {
    @Test
    fun `empty schedules yields empty worklist`() {
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = emptyList(),
                medications = emptyList(),
                pets = emptyList(),
                recentDoses = emptyList(),
                calculator = FakeCalculator(emptyMap()),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `pending doses are projected with null givenEventId`() {
        val swp = scheduleBundle("s-1", "m-1")
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = listOf(swp),
                medications = listOf(med("m-1", "p-1", name = "Methimazole")),
                pets = listOf(pet("p-1", "Luna")),
                recentDoses = emptyList(),
                calculator = FakeCalculator(mapOf("s-1" to listOf(SLOT_8AM, SLOT_8PM))),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertEquals(2, rows.size)
        assertTrue("first row pending", rows[0].givenEventId == null)
        assertTrue("second row pending", rows[1].givenEventId == null)
        assertEquals("Luna", rows[0].petName)
        assertEquals("Methimazole", rows[0].medicationName)
        assertNull("pending row has no resolvedAt", rows[0].resolvedAt)
    }

    @Test
    fun `GIVEN event matching the slot flips the row to given`() {
        val swp = scheduleBundle("s-1", "m-1")
        val resolved = Instant.parse("2026-05-16T08:23:00Z")
        val given =
            doseEvent(
                id = "d-1",
                scheduleId = "s-1",
                scheduledAt = SLOT_8AM,
                resolvedAt = resolved,
                status = DoseStatus.GIVEN,
            )
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = listOf(swp),
                medications = listOf(med("m-1", "p-1")),
                pets = listOf(pet("p-1")),
                recentDoses = listOf(given),
                calculator = FakeCalculator(mapOf("s-1" to listOf(SLOT_8AM, SLOT_8PM))),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertEquals(2, rows.size)
        val morning = rows.single { it.scheduledAt == SLOT_8AM }
        val evening = rows.single { it.scheduledAt == SLOT_8PM }
        assertEquals("d-1", morning.givenEventId)
        assertEquals(resolved, morning.resolvedAt)
        assertTrue(morning.isGiven)
        assertNull("evening still pending", evening.givenEventId)
    }

    @Test
    fun `non-GIVEN events do not match (SKIPPED is not a flip)`() {
        val swp = scheduleBundle("s-1", "m-1")
        val skipped =
            doseEvent(
                id = "d-skip",
                scheduleId = "s-1",
                scheduledAt = SLOT_8AM,
                resolvedAt = Instant.parse("2026-05-16T08:30:00Z"),
                status = DoseStatus.SKIPPED,
            )
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = listOf(swp),
                medications = listOf(med("m-1", "p-1")),
                pets = listOf(pet("p-1")),
                recentDoses = listOf(skipped),
                calculator = FakeCalculator(mapOf("s-1" to listOf(SLOT_8AM))),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertEquals(1, rows.size)
        assertNull(
            "SKIPPED event must not flip the row to given",
            rows.single().givenEventId,
        )
    }

    @Test
    fun `cross-schedule rows are globally sorted by scheduledAt`() {
        // Schedule A produces 10 AM and 10 PM; schedule B produces 9 AM and 9 PM.
        // Result must interleave to: 9 AM (B), 10 AM (A), 9 PM (B), 10 PM (A).
        val a = scheduleBundle("sched-a", "med-a")
        val b = scheduleBundle("sched-b", "med-b")
        // Use UTC slots inside the half-open [TODAY_START, TODAY_END) window so the
        // fake calculator's window-filter doesn't accidentally drop them.
        val slotA1 = Instant.parse("2026-05-16T10:00:00Z")
        val slotA2 = Instant.parse("2026-05-16T22:00:00Z")
        val slotB1 = Instant.parse("2026-05-16T09:00:00Z")
        val slotB2 = Instant.parse("2026-05-16T21:00:00Z")
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = listOf(a, b),
                medications =
                    listOf(
                        med("med-a", "p-1", name = "Med-A"),
                        med("med-b", "p-2", name = "Med-B"),
                    ),
                pets = listOf(pet("p-1", "Alpha"), pet("p-2", "Bravo")),
                recentDoses = emptyList(),
                calculator =
                    FakeCalculator(
                        mapOf(
                            "sched-a" to listOf(slotA1, slotA2),
                            "sched-b" to listOf(slotB1, slotB2),
                        ),
                    ),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertEquals(listOf(slotB1, slotA1, slotB2, slotA2), rows.map { it.scheduledAt })
    }

    @Test
    fun `schedule whose medication is missing triggers StaleEventGuard in debug`() {
        // Tier A #4 contract change: a schedule that references a nonexistent medication
        // is treated as a bug surface, not a silent skip. Debug builds (which is what
        // unit tests run under) throw to surface join-bug regressions in CI. Release
        // builds log + skip; that path is exercised at the guard level.
        val ghost = scheduleBundle("sched-ghost", medId = "med-ghost")
        val ex =
            assertThrows(IllegalStateException::class.java) {
                HomeViewModel.computeDueToday(
                    schedulesWithPhases = listOf(ghost),
                    medications = emptyList(),
                    pets = emptyList(),
                    recentDoses = emptyList(),
                    calculator = FakeCalculator(mapOf("sched-ghost" to listOf(SLOT_8AM))),
                    timeZone = ZONE,
                    todayStart = TODAY_START,
                    todayEnd = TODAY_END,
                )
            }
        assertTrue(
            "the exception must name the stale schedule + missing medicationId",
            ex.message?.contains("sched-ghost") == true && ex.message?.contains("med-ghost") == true,
        )
    }

    @Test
    fun `schedule whose medication-pet is missing triggers StaleEventGuard in debug`() {
        // Symmetric to the above: medication exists but its pet does not. Same
        // contract — surface it, don't swallow it.
        val swp = scheduleBundle("s-1", "m-1")
        val ex =
            assertThrows(IllegalStateException::class.java) {
                HomeViewModel.computeDueToday(
                    schedulesWithPhases = listOf(swp),
                    medications = listOf(med("m-1", petId = "p-ghost")),
                    pets = emptyList(),
                    recentDoses = emptyList(),
                    calculator = FakeCalculator(mapOf("s-1" to listOf(SLOT_8AM))),
                    timeZone = ZONE,
                    todayStart = TODAY_START,
                    todayEnd = TODAY_END,
                )
            }
        assertTrue(
            "the exception must name the stale schedule + missing petId",
            ex.message?.contains("s-1") == true && ex.message?.contains("p-ghost") == true,
        )
    }

    @Test
    fun `dose amount falls back to Medication doseAmount when phase override is null`() {
        val swp = scheduleBundle("s-1", "m-1")
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = listOf(swp),
                medications = listOf(med("m-1", "p-1", doseAmount = "10mg")),
                pets = listOf(pet("p-1")),
                recentDoses = emptyList(),
                calculator =
                    FakeCalculator(
                        // doseAmount=null in the ScheduledDose → row should pull from Medication.
                        mapOf("s-1" to listOf(SLOT_8AM)),
                        doseAmountOverride = null,
                    ),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertEquals("10mg", rows.single().doseAmount)
    }

    @Test
    fun `dose amount uses phase override when present`() {
        val swp = scheduleBundle("s-1", "m-1")
        val rows =
            HomeViewModel.computeDueToday(
                schedulesWithPhases = listOf(swp),
                medications = listOf(med("m-1", "p-1", doseAmount = "10mg")),
                pets = listOf(pet("p-1")),
                recentDoses = emptyList(),
                calculator =
                    FakeCalculator(
                        mapOf("s-1" to listOf(SLOT_8AM)),
                        doseAmountOverride = "5mg (taper)",
                    ),
                timeZone = ZONE,
                todayStart = TODAY_START,
                todayEnd = TODAY_END,
            )
        assertEquals(
            "phase override beats medication default",
            "5mg (taper)",
            rows.single().doseAmount,
        )
    }

    // ---------- fixtures + builders ----------

    /** Fake calculator that returns canned slot lists by scheduleId, ignoring date math. */
    private class FakeCalculator(
        private val byScheduleId: Map<String, List<Instant>>,
        private val doseAmountOverride: String? = null,
    ) : ScheduleCalculator {
        override fun computeScheduledDoses(
            schedule: Schedule,
            phases: List<SchedulePhase>,
            timeZone: TimeZone,
            fromInclusive: Instant,
            toExclusive: Instant,
        ): List<ScheduledDose> =
            (byScheduleId[schedule.id] ?: emptyList())
                .filter { it in fromInclusive..<toExclusive }
                .map { ScheduledDose(scheduledAt = it, phaseOrder = 0, doseAmount = doseAmountOverride) }
    }

    private fun scheduleBundle(
        scheduleId: String,
        medId: String,
    ): ScheduleWithPhases =
        ScheduleWithPhases(
            schedule =
                Schedule(
                    id = scheduleId,
                    medicationId = medId,
                    startDate = LocalDate(2026, 5, 1),
                    endDate = null,
                    createdAt = T0,
                ),
            phases = emptyList(),
        )

    private fun pet(
        id: String,
        name: String = "Pet-$id",
    ): Pet =
        Pet(
            id = id,
            name = name,
            species = Species.DOG,
            birthdate = null,
            weightKg = null,
            notes = null,
            createdAt = T0,
            archivedAt = null,
        )

    private fun med(
        id: String,
        petId: String,
        name: String = "Med-$id",
        doseAmount: String = "1 tablet",
    ): Medication =
        Medication(
            id = id,
            petId = petId,
            name = name,
            doseAmount = doseAmount,
            notes = null,
            createdAt = T0,
            discontinuedAt = null,
        )

    @Suppress("LongParameterList") // Test builder; explicit args are clearer than wrapping in a config object.
    private fun doseEvent(
        id: String,
        scheduleId: String,
        scheduledAt: Instant,
        resolvedAt: Instant?,
        status: DoseStatus,
        medicationId: String = "m-1",
    ): DoseEvent =
        DoseEvent(
            id = id,
            scheduleId = scheduleId,
            medicationId = medicationId,
            scheduledAt = scheduledAt,
            firedAt = null,
            resolvedAt = resolvedAt,
            status = status,
            note = null,
        )

    companion object {
        private val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
        private val ZONE: TimeZone = TimeZone.UTC
        private val TODAY_START: Instant = Instant.parse("2026-05-16T00:00:00Z")
        private val TODAY_END: Instant = Instant.parse("2026-05-17T00:00:00Z")

        // Both slots inside the [TODAY_START, TODAY_END) UTC window.
        private val SLOT_8AM: Instant = Instant.parse("2026-05-16T08:00:00Z")
        private val SLOT_8PM: Instant = Instant.parse("2026-05-16T20:00:00Z")
    }
}
