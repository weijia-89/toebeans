package app.toebeans.core.data

import app.cash.turbine.test
import app.toebeans.core.model.Medication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Abstract test-as-spec for [MedicationRepository]. Phase 4 SqlDelight subclass turns GREEN.
 */
abstract class MedicationRepositoryContract : MedicalRepositoryContract() {
    protected abstract fun createRepository(): MedicationRepository

    private lateinit var repo: MedicationRepository

    @BeforeTest
    fun setupRepository() {
        repo = createRepository()
    }

    @Test
    fun `observeForPet emits empty list when no medications exist`() =
        runTest {
            assertEquals(emptyList(), repo.observeForPet("p1").first())
        }

    @Test
    fun `upsert then getById round-trips the entity`() =
        runTest {
            val med = medication("m1", "p1", "Methimazole")
            repo.upsert(med)
            assertEquals(med, repo.getById("m1"))
        }

    @Test
    fun `observeAll emits inserts in case-insensitive name order`() =
        runTest {
            repo.upsert(medication("m3", "p1", "charlie"))
            repo.upsert(medication("m1", "p1", "Alice"))
            repo.upsert(medication("m2", "p2", "bob"))
            val names = repo.observeAll().first().map { it.name }
            assertEquals(listOf("Alice", "bob", "charlie"), names)
        }

    @Test
    fun `upsert is idempotent on id`() =
        runTest {
            repo.upsert(medication("m1", "p1", "First"))
            repo.upsert(medication("m1", "p1", "Second"))
            val all = repo.observeAll().first()
            assertEquals(1, all.size)
            assertEquals("Second", all.single().name)
        }

    @Test
    fun `delete removes the medication`() =
        runTest {
            repo.upsert(medication("m1", "p1", "A"))
            repo.upsert(medication("m2", "p1", "B"))
            repo.delete("m1")
            assertNull(repo.getById("m1"))
            assertEquals(listOf("m2"), repo.observeForPet("p1").first().map { it.id })
        }

    @Test
    fun `observeForPet re-emits after upsert`() =
        runTest {
            repo.observeForPet("p1").test {
                assertEquals(emptyList(), awaitItem())
                repo.upsert(medication("m1", "p1", "Methimazole"))
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    companion object {
        private val refCreatedAt: Instant = Instant.parse("2026-05-19T00:00:00Z")

        private fun medication(
            id: String,
            petId: String,
            name: String,
        ): Medication =
            Medication(
                id = id,
                petId = petId,
                name = name,
                doseAmount = "2.5 mg",
                notes = null,
                createdAt = refCreatedAt,
                discontinuedAt = null,
            )
    }
}
