package app.toebeans.android.ui.history

import app.toebeans.android.data.FakeMedicationRepository
import app.toebeans.android.data.FakePetRepository
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ADR-0011 acceptance 5 at the ViewModel level: the History surface exposes no write
 * path. The VM is constructed and observed against a [DoseEventRepository] wrapper whose
 * every write method throws; if the ViewModel (or anything it drives) called
 * recordGivenNow / recordGivenForSlot / delete / upsert / markStalePendingAsMissed, the
 * test would fail with that AssertionError instead of a clean state snapshot.
 *
 * The read-only property is structural as well: [HistoryRowUi] and [HistoryUiState]
 * carry no function types, so the surface cannot grow an action affordance without
 * breaking this pin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelNoWritePathTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observing history never touches a repository write path`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            petRepo.upsert(pet())
            medRepo.upsert(med())
            val doseRepo = WriteThrowingDoseEventRepository()
            doseRepo.seedReadRow()

            val viewModel = HistoryViewModel(petRepo, medRepo, doseRepo)
            // stateIn replays initialValue (loading=true) synchronously; wait for the
            // real projection so the assertion covers the actual data path.
            val state = viewModel.uiState.first { !it.loading }

            assertFalse(state.loading)
            assertEquals(1, state.rows.size)
            assertEquals("Luna", state.rows.single().petName)
            assertEquals("Given", state.rows.single().statusLabel)
        }

    @Test
    fun `history UI state models carry no function types`() {
        // Java reflection (no kotlin-reflect dep): every Kotlin function type implements
        // the kotlin.Function marker interface, so a declared field whose type is
        // assignable from Function::class.java means a callback snuck onto the read-only
        // surface. HistoryRowUi / HistoryUiState must stay callback-free.
        val stateFields = HistoryUiState::class.java.declaredFields
        val rowFields = HistoryRowUi::class.java.declaredFields
        assertTrue(stateFields.none { Function::class.java.isAssignableFrom(it.type) })
        assertTrue(rowFields.none { Function::class.java.isAssignableFrom(it.type) })
    }

    private fun pet(): Pet =
        Pet(
            id = "p-luna",
            name = "Luna",
            species = Species.CAT,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 4.1,
            notes = null,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            archivedAt = null,
        )

    private fun med(): Medication =
        Medication(
            id = "med-luna",
            petId = "p-luna",
            name = "Methimazole",
            doseAmount = "2.5",
            doseUnit = DoseUnit.MG,
            notes = null,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            discontinuedAt = null,
        )

    /**
     * Read passthrough + write tripwire. Any write call fails the test with the method
     * name in the message. Reads are backed by a [MutableStateFlow] so the VM's combine
     * sees an initial value synchronously on subscription, like the production fakes.
     */
    private class WriteThrowingDoseEventRepository : DoseEventRepository {
        private val events = MutableStateFlow<Map<String, DoseEvent>>(emptyMap())

        /** Seed one GIVEN row so the read path has something to project. */
        fun seedReadRow() {
            events.value =
                mapOf(
                    "d-1" to
                        DoseEvent(
                            id = "d-1",
                            scheduleId = "sched-x",
                            medicationId = "med-luna",
                            scheduledAt = Instant.parse("2026-10-24T12:00:00Z"),
                            firedAt = null,
                            resolvedAt = Instant.parse("2026-10-24T12:05:00Z"),
                            status = DoseStatus.GIVEN,
                            note = null,
                        ),
                )
        }

        override fun observeForPet(
            petId: String,
            sinceInclusive: Instant,
        ): Flow<List<DoseEvent>> =
            events.map { all ->
                all.values.filter { it.medicationId == "med-luna" && it.scheduledAt >= sinceInclusive }
            }

        override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
            events.map { all ->
                all.values.lastOrNull { it.status == DoseStatus.GIVEN }
            }

        override fun observeAll(): Flow<List<DoseEvent>> =
            events.map { all -> all.values.sortedByDescending { it.scheduledAt } }

        override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
            events.map { all ->
                all.values
                    .filter { it.status == DoseStatus.GIVEN && it.scheduledAt >= sinceInclusive }
                    .sortedByDescending { it.scheduledAt }
            }

        override fun observeRecentForHistory(
            sinceInclusive: Instant,
            limit: Int,
        ): Flow<List<DoseEvent>> =
            events.map { all ->
                all.values
                    .asSequence()
                    .filter { it.scheduledAt >= sinceInclusive }
                    .sortedByDescending { it.scheduledAt }
                    .take(limit)
                    .toList()
            }

        override suspend fun recordGivenNow(
            doseEventId: String,
            scheduleId: String,
            medicationId: String,
            at: Instant,
            note: String?,
        ): DoseEvent = writeBlocked("recordGivenNow")

        override suspend fun recordGivenForSlot(
            doseEventId: String,
            scheduleId: String,
            medicationId: String,
            scheduledAt: Instant,
            resolvedAt: Instant,
            note: String?,
        ): DoseEvent = writeBlocked("recordGivenForSlot")

        override suspend fun delete(doseEventId: String) {
            writeBlocked("delete")
        }

        override suspend fun upsert(event: DoseEvent) {
            writeBlocked("upsert")
        }

        override suspend fun markStalePendingAsMissed(cutoff: Instant): Int = writeBlocked("markStalePendingAsMissed")

        private fun writeBlocked(method: String): Nothing =
            throw AssertionError("History surface must not write: $method was called")
    }
}
