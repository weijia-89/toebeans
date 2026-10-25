package app.toebeans.android.ui.history

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HistoryViewModel.projectHistory] — the pure join of pets +
 * medications + dose events into the History screen's [HistoryUiState] (ADR-0011 UI
 * slice acceptance criteria).
 *
 * Same pattern as [app.toebeans.android.ui.home.HomeViewModelJoinTest]: the projection
 * is `internal` on the companion specifically so it is testable without a
 * viewModelScope. Debug builds (which is what unit tests run under) exercise the
 * StaleEventGuard throw path; the release skip path is covered at the guard level in
 * StaleEventGuardTest.
 */
class HistoryViewModelProjectionTest {
    @Test
    fun `every status renders with its own label, newest first`() {
        // ADR-0011 acceptance 1: an event of every DoseStatus shows with pet name,
        // medication name, local scheduledAt, and status label, newest first.
        val rows =
            HistoryViewModel
                .projectHistory(
                    pets = listOf(pet()),
                    medications = listOf(med()),
                    events =
                        listOf(
                            dose("d-given", DoseStatus.GIVEN, at = T_OLDER, firedAt = T_OLDER),
                            dose("d-skipped", DoseStatus.SKIPPED, at = T_MIDDLE),
                            dose("d-missed", DoseStatus.MISSED, at = T_NEWER),
                            dose("d-pending", DoseStatus.PENDING, at = T_NEWEST),
                        ),
                    since = SINCE,
                    zone = UTC,
                ).rows
        assertEquals(
            listOf("d-pending", "d-missed", "d-skipped", "d-given"),
            rows.map { it.id },
        )
        assertEquals(
            listOf("Pending", "Missed", "Skipped", "Given"),
            rows.map { it.statusLabel },
        )
        val first = rows.first()
        assertEquals("Luna", first.petName)
        assertEquals("Methimazole", first.medicationName)
        assertEquals("Oct 24, 10:00 PM", first.scheduledLabel)
    }

    @Test
    fun `pending event with firedAt shows the fired line and never claims delivery`() {
        // ADR-0011 acceptance 2: "alarm fired, nobody logged it". v1 copy says
        // "alarm fired", never "notification delivered" (notification_audit is out of
        // scope for this slice).
        val state =
            HistoryViewModel.projectHistory(
                pets = listOf(pet()),
                medications = listOf(med()),
                events = listOf(dose("d-1", DoseStatus.PENDING, at = T_MIDDLE, firedAt = T_NEWER)),
                since = SINCE,
                zone = UTC,
            )
        val row = state.rows.single()
        assertEquals("Pending", row.statusLabel)
        assertEquals("alarm fired 10:00 PM", row.firedLabel)
        assertFalse(
            "no delivered-notification claim allowed in v1",
            row.firedLabel!!.contains("notif", ignoreCase = true),
        )
    }

    @Test
    fun `given event without firedAt has no fired line`() {
        val state =
            HistoryViewModel.projectHistory(
                pets = listOf(pet()),
                medications = listOf(med()),
                events = listOf(dose("d-1", DoseStatus.GIVEN, at = T_MIDDLE, firedAt = null)),
                since = SINCE,
                zone = UTC,
            )
        assertNull(state.rows.single().firedLabel)
    }

    @Test
    fun `empty store renders the empty state without crashing`() {
        // ADR-0011 acceptance 3: empty store, no crash.
        val state =
            HistoryViewModel.projectHistory(
                pets = listOf(pet()),
                medications = listOf(med()),
                events = emptyList(),
                since = SINCE,
                zone = UTC,
            )
        assertTrue(state.rows.isEmpty())
        assertFalse("projection completion means loading is over", state.loading)
    }

    @Test
    fun `event older than the 30-day window is dropped by the viewmodel-level filter`() {
        val state =
            HistoryViewModel.projectHistory(
                pets = listOf(pet()),
                medications = listOf(med()),
                events =
                    listOf(
                        dose("d-in", DoseStatus.GIVEN, at = T_MIDDLE),
                        dose("d-out", DoseStatus.GIVEN, at = T_BEFORE_WINDOW),
                    ),
                since = SINCE,
                zone = UTC,
            )
        assertEquals(listOf("d-in"), state.rows.map { it.id })
    }

    @Test
    fun `boundary event exactly at the window start is kept`() {
        val state =
            HistoryViewModel.projectHistory(
                pets = listOf(pet()),
                medications = listOf(med()),
                events = listOf(dose("d-edge", DoseStatus.GIVEN, at = SINCE)),
                since = SINCE,
                zone = UTC,
            )
        assertEquals(listOf("d-edge"), state.rows.map { it.id })
    }

    @Test
    fun `event whose medication no longer resolves triggers StaleEventGuard in debug`() {
        // ADR-0011 acceptance 4, debug half: throws with the StaleEventGuard message.
        val orphan = dose("d-orphan", DoseStatus.GIVEN, at = T_MIDDLE, medicationId = "med-deleted")
        val ex =
            assertThrows(IllegalStateException::class.java) {
                HistoryViewModel.projectHistory(
                    pets = listOf(pet()),
                    medications = listOf(med()),
                    events = listOf(orphan),
                    since = SINCE,
                    zone = UTC,
                )
            }
        assertTrue(
            "the exception must name the stale row + missing field",
            ex.message?.contains("d-orphan") == true && ex.message?.contains("med-deleted") == true,
        )
    }

    @Test
    fun `event whose pet no longer resolves triggers StaleEventGuard in debug`() {
        val strayMed = med(id = "med-stray", petId = "pet-gone")
        val orphan = dose("d-orphan", DoseStatus.GIVEN, at = T_MIDDLE, medicationId = "med-stray")
        val ex =
            assertThrows(IllegalStateException::class.java) {
                HistoryViewModel.projectHistory(
                    pets = listOf(pet()),
                    medications = listOf(strayMed),
                    events = listOf(orphan),
                    since = SINCE,
                    zone = UTC,
                )
            }
        assertTrue(
            "the exception must name the stale row + missing field",
            ex.message?.contains("d-orphan") == true && ex.message?.contains("pet-gone") == true,
        )
    }

    @Test
    fun `window start is exactly thirty days before now`() {
        assertEquals(
            Instant.parse("2026-09-24T22:00:00Z"),
            HistoryViewModel.windowStart(NOW),
        )
    }

    // ---------- builders ----------

    private fun pet(
        id: String = "p-luna",
        name: String = "Luna",
    ): Pet =
        Pet(
            id = id,
            name = name,
            species = Species.CAT,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 4.1,
            notes = null,
            createdAt = T0,
            archivedAt = null,
        )

    private fun med(
        id: String = "med-luna",
        petId: String = "p-luna",
        name: String = "Methimazole",
    ): Medication =
        Medication(
            id = id,
            petId = petId,
            name = name,
            doseAmount = "2.5",
            doseUnit = DoseUnit.MG,
            notes = null,
            createdAt = T0,
            discontinuedAt = null,
        )

    private fun dose(
        id: String,
        status: DoseStatus,
        at: Instant,
        firedAt: Instant? = null,
        medicationId: String = "med-luna",
    ): DoseEvent =
        DoseEvent(
            id = id,
            scheduleId = "sched-x",
            medicationId = medicationId,
            scheduledAt = at,
            firedAt = firedAt,
            resolvedAt = if (status == DoseStatus.PENDING) null else at,
            status = status,
            note = null,
        )

    companion object {
        private val UTC = TimeZone.UTC
        private val T0 = Instant.parse("2024-01-01T00:00:00Z")

        /** "Now" for these tests: 2026-10-24 22:00 UTC. */
        private val NOW = Instant.parse("2026-10-24T22:00:00Z")

        /** 30 days before NOW. */
        private val SINCE = Instant.parse("2026-09-24T22:00:00Z")
        private val T_BEFORE_WINDOW = Instant.parse("2026-09-24T21:59:59Z")
        private val T_OLDER = Instant.parse("2026-10-01T12:00:00Z")
        private val T_MIDDLE = Instant.parse("2026-10-10T12:00:00Z")
        private val T_NEWER = Instant.parse("2026-10-20T22:00:00Z")
        private val T_NEWEST = Instant.parse("2026-10-24T22:00:00Z")
    }
}
