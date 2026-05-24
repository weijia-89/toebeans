package app.toebeans.android.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for [loadDemoData] using in-memory fakes (same contracts as production DI).
 */
class LoadDemoDataTest {
    @Test
    fun `loadDemoData populates Rufus and Luna`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            loadDemoData(petRepo, medRepo, schedRepo)
            assertEquals(2, petRepo.observeAll().first().size)
            assertNotNull(petRepo.getById("pet-rufus"))
            assertNotNull(petRepo.getById("pet-luna"))
        }

    @Test
    fun `loadDemoData populates the Methimazole medication for Luna`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            loadDemoData(petRepo, medRepo, schedRepo)
            val med = medRepo.getById("med-luna-methimazole")
            assertNotNull(med)
            assertEquals("pet-luna", med!!.petId)
            assertEquals("Methimazole", med.name)
        }

    @Test
    fun `loadDemoData populates the Methimazole schedule with one BID phase`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            loadDemoData(petRepo, medRepo, schedRepo)
            val schedule = schedRepo.observeById("sched-luna-methimazole").first()
            assertNotNull(schedule)
            val phases = schedRepo.observePhases("sched-luna-methimazole").first()
            assertEquals(2, phases.single().dosesPerDay)
        }

    @Test
    fun `loadDemoData is idempotent`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            loadDemoData(petRepo, medRepo, schedRepo)
            loadDemoData(petRepo, medRepo, schedRepo)
            assertEquals(2, petRepo.observeAll().first().size)
            assertEquals(1, medRepo.observeAll().first().size)
            assertEquals(1, schedRepo.observeAll().first().size)
        }

    @Test
    fun `loadDemoData preserves user-created entries`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            petRepo.upsert(
                app.toebeans.core.model.Pet(
                    id = "pet-user-cat",
                    name = "Tabby",
                    species = app.toebeans.core.model.Species.CAT,
                    birthdate = kotlinx.datetime.LocalDate(2021, 5, 1),
                    weightKg = 3.5,
                    notes = null,
                    createdAt = seedCreatedAt,
                    archivedAt = null,
                ),
            )
            loadDemoData(petRepo, medRepo, schedRepo)
            assertEquals(3, petRepo.observeAll().first().size)
            assertNotNull(petRepo.getById("pet-user-cat"))
        }
}
