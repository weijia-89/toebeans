package app.toebeans.android.ui.medications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Sibling of `MedicationEditViewModelDeleteTest`. Covers the soft-delete (discontinue) path.
 *
 * Behavior contract:
 *
 * - `discontinue(now)` stamps `Medication.discontinuedAt` with the supplied instant, persists
 *   via `MedicationRepository.upsert`, AND reflects the change in `state.discontinuedAt`.
 *
 * - `reactivate()` clears `Medication.discontinuedAt` back to null, persists, AND clears
 *   `state.discontinuedAt` in lockstep.
 *
 * - `discontinue` and `reactivate` are no-ops in new-medication mode (no loaded id) and
 *   return `false` so the UI can branch.
 *
 * - `load()` seeds `state.discontinuedAt` from the persisted record, so the UI's banner +
 *   topBar action surfaces immediately on open without an extra round-trip.
 *
 * - `save()` (existing behavior) preserves `discontinuedAt` from the repo, so editing
 *   notes/dose on a discontinued medication does NOT silently re-activate it (regression
 *   guard against the v0.1 footgun called out in the VM's KDoc).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditViewModelDiscontinueTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `discontinue stamps discontinuedAt on the persisted medication`() =
        runTest {
            val repo = InMemoryMedRepo(seedMed("med-1", "pet-luna", "Methimazole"))
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            val pinned = Instant.fromEpochMilliseconds(1_715_000_000_000L)
            assertTrue(vm.discontinue(now = pinned))
            assertEquals(pinned, repo.getById("med-1")?.discontinuedAt)
        }

    @Test
    fun `discontinue updates state discontinuedAt in lockstep with the repo`() =
        runTest {
            val repo = InMemoryMedRepo(seedMed("med-1", "pet-luna", "Methimazole"))
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            val pinned = Instant.fromEpochMilliseconds(1_715_000_000_000L)
            vm.discontinue(now = pinned)
            assertEquals(pinned, vm.state.value.discontinuedAt)
            assertTrue(vm.state.value.isDiscontinued)
        }

    @Test
    fun `reactivate clears discontinuedAt on the persisted medication`() =
        runTest {
            val pinned = Instant.fromEpochMilliseconds(1_715_000_000_000L)
            val repo =
                InMemoryMedRepo(
                    seedMed("med-1", "pet-luna", "Methimazole").copy(discontinuedAt = pinned),
                )
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            assertTrue(vm.reactivate())
            assertNull(repo.getById("med-1")?.discontinuedAt)
        }

    @Test
    fun `reactivate updates state discontinuedAt to null in lockstep with the repo`() =
        runTest {
            val pinned = Instant.fromEpochMilliseconds(1_715_000_000_000L)
            val repo =
                InMemoryMedRepo(
                    seedMed("med-1", "pet-luna", "Methimazole").copy(discontinuedAt = pinned),
                )
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            assertEquals(pinned, vm.state.value.discontinuedAt)
            vm.reactivate()
            assertNull(vm.state.value.discontinuedAt)
            assertFalse(vm.state.value.isDiscontinued)
        }

    @Test
    fun `discontinue in new-medication mode is a no-op and returns false`() =
        runTest {
            val seed = seedMed("med-1", "pet-luna", "Methimazole")
            val repo = InMemoryMedRepo(seed)
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            // Never load(). The form is in new-med mode.
            assertFalse(vm.discontinue(now = Clock.System.now()))
            assertNull(repo.getById("med-1")?.discontinuedAt)
            assertNull(vm.state.value.discontinuedAt)
        }

    @Test
    fun `reactivate in new-medication mode is a no-op and returns false`() =
        runTest {
            val repo = InMemoryMedRepo(seedMed("med-1", "pet-luna", "Methimazole"))
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            // Never load(). The form is in new-med mode.
            assertFalse(vm.reactivate())
            assertNull(vm.state.value.discontinuedAt)
        }

    @Test
    fun `load seeds state discontinuedAt from the persisted medication`() =
        runTest {
            val pinned = Instant.fromEpochMilliseconds(1_715_000_000_000L)
            val repo =
                InMemoryMedRepo(
                    seedMed("med-1", "pet-luna", "Methimazole").copy(discontinuedAt = pinned),
                )
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            assertEquals(pinned, vm.state.value.discontinuedAt)
            assertTrue(vm.state.value.isDiscontinued)
        }

    @Test
    fun `save on a discontinued medication preserves discontinuedAt from the repo`() =
        runTest {
            // Regression guard for the footgun called out in MedicationEditViewModel.save's
            // KDoc: "editing a discontinued medication silently un-discontinues it". Without
            // the existing.discontinuedAt preserve-step in save(), an edit-and-save flow on
            // a discontinued med would resume dose reminders for a medication the caregiver
            // had stopped. This test pins that behavior.
            val pinned = Instant.fromEpochMilliseconds(1_715_000_000_000L)
            val repo =
                InMemoryMedRepo(
                    seedMed("med-1", "pet-luna", "Methimazole").copy(discontinuedAt = pinned),
                )
            val vm = MedicationEditViewModel(repo)
            vm.setPetId("pet-luna")
            vm.load("med-1")
            vm.onNotesChange("New notes added while discontinued")
            assertTrue(vm.save())
            val persisted = repo.getById("med-1")
            assertEquals(pinned, persisted?.discontinuedAt)
            assertEquals("New notes added while discontinued", persisted?.notes)
        }
}

// `seedMed` and `InMemoryMedRepo` live in MedicationEditTestFixtures.kt. They are shared
// across the MedicationEditViewModel* test family so two file-private copies don't
// collide at the Kotlin namespace level.
