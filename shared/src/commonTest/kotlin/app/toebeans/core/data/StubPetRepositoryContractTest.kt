package app.toebeans.core.data

import app.toebeans.core.model.Pet
import kotlinx.coroutines.flow.Flow

/**
 * Phase 1 concrete subclass of [PetRepositoryContract]. The stub factory returns a
 * [PetRepository] whose every method throws [NotImplementedError]. Every inherited test fails
 * RED on first run; this is by design per AGENTS.md § Test-as-spec rules (the human reviewer
 * sees a failing test runner output, not just a source diff).
 *
 * Phase 2 (the SqlDelight-backed PetRepository implementation PR) ships a sibling subclass
 * (e.g. `SqlDelightPetRepositoryContractTest`) whose factory returns a real implementation and
 * turns these tests green.
 *
 * This subclass intentionally does NOT bind the existing
 * [app.toebeans.android.data.FakePetRepository] because:
 *   1. The goal of Phase 1 is to ratify the contract before any impl asserts it satisfies
 *      said contract. Binding the fake here would conflate "the fake works" with "the
 *      contract is correct."
 *   2. The fake lives in :androidApp and is not on the :shared:commonTest classpath.
 *      Binding it would either pull androidApp into shared (forbidden) or fork the contract
 *      into a Robolectric-only test (defeats the cross-platform reusability).
 *   3. The fake currently sorts case-sensitively (`sortedBy(Pet::name)`), which diverges from
 *      the contract's case-insensitive ordering assertion (matching Pet.sq's COLLATE NOCASE).
 *      Binding the fake would fail the contract; that finding belongs in a separate small
 *      fake-fix PR rather than getting tangled with the contract review here.
 */
class StubPetRepositoryContractTest : PetRepositoryContract() {
    override fun createRepository(): PetRepository = StubPetRepository

    private object StubPetRepository : PetRepository {
        override fun observeAll(): Flow<List<Pet>> =
            throw NotImplementedError("Phase 2: SqlDelightPetRepository.observeAll() not yet implemented")

        override suspend fun getById(id: String): Pet? =
            throw NotImplementedError("Phase 2: SqlDelightPetRepository.getById() not yet implemented")

        override fun observeById(id: String): Flow<Pet?> =
            throw NotImplementedError("Phase 2: SqlDelightPetRepository.observeById() not yet implemented")

        override suspend fun upsert(pet: Pet): Unit =
            throw NotImplementedError("Phase 2: SqlDelightPetRepository.upsert() not yet implemented")

        override suspend fun delete(id: String): Unit =
            throw NotImplementedError("Phase 2: SqlDelightPetRepository.delete() not yet implemented")
    }
}
