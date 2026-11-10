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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * History tab feed: all statuses for 30 days, row cap, and in-page pet filter.
 * The read is [DoseEventRepository.observeRecentForHistory]. Writes still throw.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelFeedTest {
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
    fun `history reads observeRecentForHistory for 30 days at the row limit`() =
        runTest {
            val repo = RecordingDoseRepository()
            repo.seed(inWindowDoses() + beforeWindowDose())
            val viewModel = started(repo)
            val call = repo.historyCalls.single()
            val expectedStart = Clock.System.now() - HistoryViewModel.WINDOW_DAYS.days
            val skew = (call.sinceInclusive - expectedStart).absoluteValue
            assertTrue(
                "sinceInclusive ${call.sinceInclusive} skew $skew",
                skew <= 2.seconds,
            )
            assertEquals(HistoryViewModel.HISTORY_ROW_LIMIT, call.limit)
            assertEquals(500, call.limit)
            assertEquals(0, repo.allRecentCalls)
            viewModel.cancelWatch()
        }

    @Test
    fun `pending given skipped and missed inside the window all show`() =
        runTest {
            val repo = RecordingDoseRepository()
            repo.seed(inWindowDoses())
            val viewModel = started(repo)
            val labels =
                viewModel.uiState.value.rows
                    .map { it.statusLabel }
            assertEquals(4, labels.size)
            assertEquals(setOf("Pending", "Given", "Skipped", "Missed"), labels.toSet())
            viewModel.cancelWatch()
        }

    @Test
    fun `a dose scheduled before the window does not appear`() =
        runTest {
            val repo = RecordingDoseRepository()
            repo.seed(inWindowDoses() + beforeWindowDose())
            val viewModel = started(repo)
            val ids =
                viewModel.uiState.value.rows
                    .map { it.id }
            assertFalse(ids.contains("d-old"))
            assertEquals(4, ids.size)
            viewModel.cancelWatch()
        }

    @Test
    fun `pet filter keeps one pet then a clear or a missing id shows every row`() =
        runTest {
            val repo = RecordingDoseRepository()
            repo.seed(inWindowDoses())
            val viewModel = started(repo)
            viewModel.selectPetFilter("p-luna")
            assertEquals(setOf("Luna"), petNames(viewModel))
            viewModel.clearPetFilter()
            assertEquals(setOf("Luna", "Milo"), petNames(viewModel))
            viewModel.selectPetFilter("p-milo")
            assertEquals(setOf("Milo"), petNames(viewModel))
            viewModel.selectPetFilter("pet-missing")
            assertEquals(setOf("Luna", "Milo"), petNames(viewModel))
            viewModel.cancelWatch()
        }

    @Test
    fun `empty state body names every status in the 30 day window`() {
        assertEquals(
            "Doses from the last 30 days show here, including pending, skipped, and missed.",
            EMPTY_HISTORY_BODY,
        )
    }

    @Test
    fun `history sources do not send the owner back to Today to log a dose`() {
        val retired = "Log a dose from Today" + " and it will show up here."
        val dir = File("src/main/kotlin/app/toebeans/android/ui/history")
        val hits =
            dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { retired in it.readText() }
                .map { it.name }
                .toList()
        assertEquals(emptyList<String>(), hits)
        val sources =
            dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }
        assertTrue(sources.contains("HistoryPetChip"))
    }

    private fun petNames(viewModel: WatchedHistory): Set<String> =
        viewModel.uiState.value.rows
            .map { it.petName }
            .toSet()

    private suspend fun CoroutineScope.started(repo: RecordingDoseRepository): WatchedHistory {
        val pets = FakePetRepository()
        val meds = FakeMedicationRepository()
        pets.upsert(pet("p-luna", "Luna"))
        pets.upsert(pet("p-milo", "Milo", Species.DOG))
        meds.upsert(med("med-luna", "p-luna", "Methimazole"))
        meds.upsert(med("med-milo", "p-milo", "Amlodipine"))
        val viewModel = HistoryViewModel(pets, meds, repo)
        val watch = launch(testDispatcher) { viewModel.uiState.collect {} }
        viewModel.uiState.first { !it.loading }
        return WatchedHistory(viewModel, watch)
    }

    private fun inWindowDoses(): List<DoseEvent> {
        val now = Clock.System.now()
        return listOf(
            dose("d-pending", DoseStatus.PENDING, "med-luna", now - 1.days),
            dose("d-given", DoseStatus.GIVEN, "med-luna", now - 2.days),
            dose("d-skipped", DoseStatus.SKIPPED, "med-milo", now - 3.days),
            dose("d-missed", DoseStatus.MISSED, "med-milo", now - 4.days),
        )
    }

    private fun beforeWindowDose(): DoseEvent =
        dose(
            "d-old",
            DoseStatus.GIVEN,
            "med-luna",
            Clock.System.now() - (HistoryViewModel.WINDOW_DAYS + 1).days,
        )

    private fun pet(
        id: String,
        name: String,
        species: Species = Species.CAT,
    ): Pet =
        Pet(
            id = id,
            name = name,
            species = species,
            birthdate = LocalDate(2020, 1, 1),
            weightKg = 4.1,
            notes = null,
            createdAt = T0,
            archivedAt = null,
        )

    private fun med(
        id: String,
        petId: String,
        name: String,
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
        medicationId: String,
        at: Instant,
    ): DoseEvent =
        DoseEvent(
            id = id,
            scheduleId = "sched-x",
            medicationId = medicationId,
            scheduledAt = at,
            firedAt = null,
            resolvedAt = if (status == DoseStatus.PENDING) null else at,
            status = status,
            note = null,
        )

    private class WatchedHistory(
        private val viewModel: HistoryViewModel,
        private val watch: Job,
    ) {
        val uiState get() = viewModel.uiState

        fun selectPetFilter(petId: String) = viewModel.selectPetFilter(petId)

        fun clearPetFilter() = viewModel.clearPetFilter()

        fun cancelWatch() = watch.cancel()
    }

    /**
     * Records the history read. [observeAllRecent] counts a call and returns nothing,
     * so a leftover given-only read fails both the counter and the status assertions.
     */
    private class RecordingDoseRepository : DoseEventRepository {
        private val events = MutableStateFlow<List<DoseEvent>>(emptyList())
        val historyCalls = mutableListOf<HistoryCall>()
        var allRecentCalls: Int = 0

        fun seed(rows: List<DoseEvent>) {
            events.value = rows
        }

        override fun observeForPet(
            petId: String,
            sinceInclusive: Instant,
        ): Flow<List<DoseEvent>> = events.map { emptyList() }

        override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> = events.map { null }

        override fun observeAll(): Flow<List<DoseEvent>> = events

        override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> {
            allRecentCalls += 1
            return events.map { emptyList() }
        }

        override fun observeRecentForHistory(
            sinceInclusive: Instant,
            limit: Int,
        ): Flow<List<DoseEvent>> {
            historyCalls += HistoryCall(sinceInclusive, limit)
            return events.map { all ->
                all
                    .asSequence()
                    .filter { it.scheduledAt >= sinceInclusive }
                    .sortedByDescending { it.scheduledAt }
                    .take(limit)
                    .toList()
            }
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

    private data class HistoryCall(
        val sinceInclusive: Instant,
        val limit: Int,
    )

    private companion object {
        val T0: Instant = Instant.parse("2024-01-01T00:00:00Z")
    }
}
