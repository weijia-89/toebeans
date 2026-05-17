package app.toebeans.android.ui.reminders

import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ReminderListViewModel.Companion.joinToUiState], plus the small phase-
 * summary and ends-label helpers.
 *
 * Pure logic — no `viewModelScope`, no coroutine rig, no Robolectric. The function is
 * `internal` on the companion specifically so we can drive it directly.
 *
 * Coverage:
 *   - Empty inputs → empty rows.
 *   - Happy path: pet + medication + schedule yields one well-formed row.
 *   - Sort order: pet name (case-insensitive), then medication name, then scheduleId.
 *   - Phase summary covers 1-, 2-, and 3+-phase cases.
 *   - Ends label covers the today / tomorrow / N-days / dated / ended branches.
 *   - Stale rows (missing medication or pet) trigger StaleEventGuard — matches the
 *     contract from Home Tier A #4.
 */
class ReminderListViewModelTest {
    private val today = LocalDate(2026, 3, 15)
    private val seedCreatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun pet(
        id: String,
        name: String = "Pet-$id",
    ) = Pet(
        id = id,
        name = name,
        species = Species.DOG,
        birthdate = null,
        weightKg = null,
        notes = null,
        createdAt = seedCreatedAt,
        archivedAt = null,
    )

    private fun med(
        id: String,
        petId: String,
        name: String = "Med-$id",
    ) = Medication(
        id = id,
        petId = petId,
        name = name,
        doseAmount = "10mg",
        notes = null,
        createdAt = seedCreatedAt,
        discontinuedAt = null,
    )

    private fun schedule(
        id: String,
        medicationId: String,
        endDate: LocalDate? = null,
    ) = Schedule(
        id = id,
        medicationId = medicationId,
        startDate = today,
        endDate = endDate,
        createdAt = seedCreatedAt,
    )

    private fun phase(
        scheduleId: String,
        phaseOrder: Int = 0,
        durationDays: Int = 14,
        dosesPerDay: Int = 2,
    ): SchedulePhase {
        val times =
            when (dosesPerDay) {
                1 -> listOf(LocalTime(8, 0))
                2 -> listOf(LocalTime(8, 0), LocalTime(20, 0))
                3 -> listOf(LocalTime(8, 0), LocalTime(14, 0), LocalTime(20, 0))
                else -> List(dosesPerDay) { i -> LocalTime((6 + i * 2).coerceAtMost(23), 0) }
            }
        return SchedulePhase(
            id = "phase-$scheduleId-$phaseOrder",
            scheduleId = scheduleId,
            phaseOrder = phaseOrder,
            durationDays = durationDays,
            dosesPerDay = dosesPerDay,
            doseTimesLocal = times,
            doseAmount = null,
        )
    }

    @Test
    fun `empty inputs yield empty rows and loading false`() {
        val state = ReminderListViewModel.joinToUiState(emptyList(), emptyList(), emptyList(), today)
        assertTrue(state.rows.isEmpty())
        assertEquals(false, state.loading)
    }

    @Test
    fun `happy path - one schedule produces one row with all fields populated`() {
        val p = pet(id = "p-luna", name = "Luna")
        val m = med(id = "m-methimazole", petId = "p-luna", name = "Methimazole")
        val s = schedule(id = "s-1", medicationId = "m-methimazole", endDate = LocalDate(2026, 4, 15))
        val swp = ScheduleWithPhases(s, listOf(phase(s.id, dosesPerDay = 2)))

        val state = ReminderListViewModel.joinToUiState(listOf(p), listOf(m), listOf(swp), today)

        assertEquals(1, state.rows.size)
        val row = state.rows.single()
        assertEquals("s-1", row.scheduleId)
        assertEquals("p-luna", row.petId)
        assertEquals("Luna", row.petName)
        assertEquals("m-methimazole", row.medicationId)
        assertEquals("Methimazole", row.medicationName)
        assertEquals("Twice daily for 14 days", row.phaseSummary)
        assertEquals("Ends 2026-04-15", row.endsLabel)
    }

    @Test
    fun `sort order - pet name case-insensitive then medication name then scheduleId`() {
        val pBella = pet("p-bella", "bella")
        val pArthur = pet("p-arthur", "Arthur") // capital A; case-insensitive sort puts Arthur first
        val pCharlie = pet("p-charlie", "Charlie")
        val mBella = med("m-bella", "p-bella", "Apoquel")
        val mArthurA = med("m-arthur-a", "p-arthur", "Apoquel")
        val mArthurB = med("m-arthur-b", "p-arthur", "Bravecto")
        val mCharlie = med("m-charlie", "p-charlie", "Carprofen")

        val swps =
            listOf(
                ScheduleWithPhases(schedule("s-3", "m-charlie"), listOf(phase("s-3"))),
                ScheduleWithPhases(schedule("s-2", "m-arthur-b"), listOf(phase("s-2"))),
                ScheduleWithPhases(schedule("s-1b", "m-arthur-a"), listOf(phase("s-1b"))),
                ScheduleWithPhases(schedule("s-1a", "m-arthur-a"), listOf(phase("s-1a"))),
                ScheduleWithPhases(schedule("s-bella", "m-bella"), listOf(phase("s-bella"))),
            )

        val state =
            ReminderListViewModel.joinToUiState(
                pets = listOf(pBella, pArthur, pCharlie),
                meds = listOf(mBella, mArthurA, mArthurB, mCharlie),
                schedules = swps,
                today = today,
            )

        assertEquals(
            "Arthur first (case-insensitive), then his Apoquel before Bravecto, then Bella, then Charlie",
            listOf("s-1a", "s-1b", "s-2", "s-bella", "s-3"),
            state.rows.map { it.scheduleId },
        )
    }

    @Test
    fun `phase summary - single phase produces simple cadence + duration`() {
        assertEquals(
            "Once daily for 7 days",
            ReminderListViewModel.summarizePhases(listOf(phase("s", durationDays = 7, dosesPerDay = 1))),
        )
        assertEquals(
            "Twice daily for 14 days",
            ReminderListViewModel.summarizePhases(listOf(phase("s", durationDays = 14, dosesPerDay = 2))),
        )
        assertEquals(
            "3× daily for 5 days",
            ReminderListViewModel.summarizePhases(listOf(phase("s", durationDays = 5, dosesPerDay = 3))),
        )
    }

    @Test
    fun `phase summary - two phases formatted as 'first, then second'`() {
        val phases =
            listOf(
                phase("s", phaseOrder = 0, durationDays = 7, dosesPerDay = 3),
                phase("s", phaseOrder = 1, durationDays = 14, dosesPerDay = 2),
            )
        assertEquals(
            "3× daily for 7 days, then Twice daily for 14 days",
            ReminderListViewModel.summarizePhases(phases),
        )
    }

    @Test
    fun `phase summary - three or more phases folds extras into (+N more)`() {
        val phases =
            listOf(
                phase("s", phaseOrder = 0, durationDays = 7, dosesPerDay = 3),
                phase("s", phaseOrder = 1, durationDays = 7, dosesPerDay = 2),
                phase("s", phaseOrder = 2, durationDays = 7, dosesPerDay = 1),
                phase("s", phaseOrder = 3, durationDays = 7, dosesPerDay = 1),
            )
        assertEquals(
            "3× daily for 7 days, then Twice daily for 7 days (+2 more)",
            ReminderListViewModel.summarizePhases(phases),
        )
    }

    @Test
    fun `ends label - null endDate returns null (row hides the label)`() {
        assertNull(ReminderListViewModel.endsLabel(endDate = null, today = today))
    }

    @Test
    fun `ends label - branches by remaining days`() {
        assertEquals("Ended", ReminderListViewModel.endsLabel(today.minus(1), today))
        assertEquals("Ends today", ReminderListViewModel.endsLabel(today, today))
        assertEquals("Ends tomorrow", ReminderListViewModel.endsLabel(today.plus(1), today))
        assertEquals("Ends in 3 days", ReminderListViewModel.endsLabel(today.plus(3), today))
        assertEquals("Ends in 6 days", ReminderListViewModel.endsLabel(today.plus(6), today))
        // 7 days hits the "else" branch and shows the absolute date
        assertEquals(
            "Ends ${today.plus(7)}",
            ReminderListViewModel.endsLabel(today.plus(7), today),
        )
    }

    @Test
    fun `schedule whose medication is missing triggers StaleEventGuard in debug`() {
        // Tier A #4 contract: stale rows are bugs to be surfaced in debug, not silently
        // dropped. We pin that contract for the Reminders surface too.
        val swp = ScheduleWithPhases(schedule("s-ghost", "m-missing"), listOf(phase("s-ghost")))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                ReminderListViewModel.joinToUiState(
                    pets = emptyList(),
                    meds = emptyList(),
                    schedules = listOf(swp),
                    today = today,
                )
            }
        assertTrue(ex.message?.contains("s-ghost") == true)
        assertTrue(ex.message?.contains("m-missing") == true)
    }

    @Test
    fun `schedule whose medication-pet is missing triggers StaleEventGuard in debug`() {
        val m = med("m-1", "p-ghost")
        val swp = ScheduleWithPhases(schedule("s-1", "m-1"), listOf(phase("s-1")))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                ReminderListViewModel.joinToUiState(
                    pets = emptyList(),
                    meds = listOf(m),
                    schedules = listOf(swp),
                    today = today,
                )
            }
        assertTrue(ex.message?.contains("s-1") == true)
        assertTrue(ex.message?.contains("p-ghost") == true)
    }
}

private fun LocalDate.plus(days: Int): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() + days)

private fun LocalDate.minus(days: Int): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() - days)
