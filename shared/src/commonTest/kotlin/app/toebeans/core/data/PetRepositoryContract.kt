package app.toebeans.core.data

import app.cash.turbine.test
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Abstract test-as-spec for the [PetRepository] interface contract.
 *
 * Any [PetRepository] implementation MUST satisfy every test in this class. Concrete subclasses
 * provide a freshly-isolated [PetRepository] via [createRepository] (called once per test via
 * [BeforeTest]).
 *
 * Phase 1 (this PR, M1 Decision 4a) ships this contract plus [StubPetRepositoryContractTest],
 * which exercises a stub-throws factory. Every test fails RED on first run, per AGENTS.md
 * § Test-as-spec rules: the human reviewer sees a failing test runner output, not just a
 * source diff.
 *
 * Phase 2 (the SqlDelight-backed PetRepository implementation PR, also vibe-dangerous) ships
 * a sibling subclass (e.g. `SqlDelightPetRepositoryContractTest`) whose factory returns a real
 * implementation. Those tests turn green and from then on this contract is the regression gate.
 *
 * Why a contract + subclass shape rather than a per-impl test class:
 *   1. The contract is reusable. Same assertions cover the SqlDelight impl (Phase 2), any
 *      future impl (e.g. an iOS bridge in M5), and informally the existing
 *      [app.toebeans.android.data.FakePetRepository] (which can be bound later if Wei wants
 *      a sibling subclass for the fake; see "Known fake divergence" in the Phase 1 PR body).
 *   2. One human-reviewable artifact pins what [PetRepository] is supposed to do, regardless
 *      of impl drift.
 *   3. The stub-factory subclass keeps Phase 1 honestly RED so the test-as-spec discipline
 *      (review failing tests before implementation lands) is preserved.
 *
 * Per AGENTS.md § Test-as-spec rules:
 *   1. Wei must approve this contract's signatures and assertions before any impl PR lands.
 *   2. Phase 2's impl PR adds a sibling subclass and turns these tests green.
 *   3. The contract here does not promise an archive method. [Pet.archivedAt] exists in the
 *      model and the [Pet.sq] schema has an `archivePet` query, but neither
 *      [PetRepository.delete] nor any other interface method exposes soft-archive today.
 *      Adding such a method is a separate change with its own contract case.
 *
 * Delete semantics: [PetRepository.delete] is hard delete with FK CASCADE per ADR-0010
 * (sqlite-foreign-keys). The cascade behavior (Medication, Schedule, SchedulePhase, DoseEvent
 * rows are removed when the parent Pet is deleted) is asserted in the dependent repository
 * contracts (Phases 3 through 8), not here, because Phase 1 cannot construct dependents
 * without those repositories' fakes or impls in scope.
 *
 * Ordering: the [Pet.sq] `selectAllPets` query orders by `name COLLATE NOCASE`. The contract
 * pins this case-insensitive ordering. Impls that sort case-sensitively (e.g. the current
 * androidApp [app.toebeans.android.data.FakePetRepository]) will fail the contract; this is
 * a real divergence flagged for separate cleanup in the Phase 1 PR body.
 */
abstract class PetRepositoryContract {
    /**
     * Concrete subclasses return a freshly-isolated [PetRepository] (empty initial state, no
     * leakage between tests). Called once per test via [BeforeTest].
     */
    protected abstract fun createRepository(): PetRepository

    private lateinit var repo: PetRepository

    @BeforeTest
    fun setup() {
        repo = createRepository()
    }

    @Test
    fun `observeAll emits empty list on initial subscribe`() =
        runTest {
            val initial = repo.observeAll().first()
            assertEquals(emptyList(), initial, "fresh repo must emit empty list")
        }

    @Test
    fun `upsert then getById round-trips the entity`() =
        runTest {
            val pet = pet("p1", "Rufus")
            repo.upsert(pet)
            assertEquals(pet, repo.getById("p1"))
        }

    @Test
    fun `getById returns null for unknown id`() =
        runTest {
            assertNull(repo.getById("does-not-exist"))
        }

    @Test
    fun `observeAll emits inserts in case-insensitive name order`() =
        runTest {
            // Insertion order is deliberately not the expected emission order. Names mix case
            // to exercise the COLLATE NOCASE contract from Pet.sq's selectAllPets query.
            repo.upsert(pet("p3", "charlie"))
            repo.upsert(pet("p1", "Alice"))
            repo.upsert(pet("p2", "bob"))
            val names = repo.observeAll().first().map { it.name }
            assertEquals(
                listOf("Alice", "bob", "charlie"),
                names,
                "observeAll must order by name COLLATE NOCASE per Pet.sq selectAllPets",
            )
        }

    @Test
    fun `upsert is idempotent on id (second upsert replaces, does not duplicate)`() =
        runTest {
            repo.upsert(pet("p1", "Rufus"))
            repo.upsert(pet("p1", "Rufus the Second"))
            val all = repo.observeAll().first()
            assertEquals(1, all.size, "second upsert with same id must replace, not duplicate")
            assertEquals("Rufus the Second", all.single().name)
        }

    @Test
    fun `delete removes the pet (getById returns null, observeAll excludes it)`() =
        runTest {
            repo.upsert(pet("p1", "Rufus"))
            repo.upsert(pet("p2", "Luna"))
            repo.delete("p1")
            assertNull(repo.getById("p1"), "getById must return null after delete")
            val remaining = repo.observeAll().first()
            assertEquals(listOf("p2"), remaining.map { it.id }, "observeAll must exclude the deleted pet")
        }

    @Test
    fun `delete is idempotent on unknown id (no exception thrown)`() =
        runTest {
            // Defensive contract: receivers may call delete on a pet that's already gone in a
            // race with a sibling UI delete. Throwing here would crash the receiver process,
            // which violates the medication-critical-path safety posture in AGENTS.md
            // § Operator wisdom. The test passes if no exception is thrown.
            repo.delete("never-existed")
        }

    @Test
    fun `observeById emits null for unknown id, then current after upsert, then null after delete`() =
        runTest {
            repo.observeById("p1").test {
                assertNull(awaitItem(), "first emission is null for absent id")
                repo.upsert(pet("p1", "Rufus"))
                assertEquals("Rufus", awaitItem()?.name)
                repo.delete("p1")
                assertNull(awaitItem(), "post-delete emission is null")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeAll re-emits after upsert (Flow reacts to changes)`() =
        runTest {
            repo.observeAll().test {
                assertEquals(emptyList(), awaitItem(), "initial emission is empty list")
                repo.upsert(pet("p1", "Rufus"))
                val afterInsert = awaitItem()
                assertEquals(1, afterInsert.size, "Flow must re-emit with the inserted pet")
                assertEquals("Rufus", afterInsert.single().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    companion object {
        // Reference time anchored well before any DST boundary. The createdAt value is not
        // load-bearing for the contract; it just needs to be a valid Instant.
        private val refCreatedAt: Instant = Instant.parse("2026-05-19T00:00:00Z")

        private fun pet(
            id: String,
            name: String,
        ): Pet =
            Pet(
                id = id,
                name = name,
                species = Species.DOG,
                birthdate = null,
                weightKg = null,
                notes = null,
                createdAt = refCreatedAt,
                archivedAt = null,
            )
    }
}
