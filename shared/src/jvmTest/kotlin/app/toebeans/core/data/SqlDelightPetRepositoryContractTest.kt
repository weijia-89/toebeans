package app.toebeans.core.data

import app.toebeans.core.data.db.DatabaseFactory
import kotlinx.coroutines.Dispatchers

/**
 * Phase 2 concrete subclass of [PetRepositoryContract]. The factory constructs a freshly
 * isolated [SqlDelightPetRepository] backed by an in-memory [JdbcSqliteDriver] (via the JVM
 * `actual` of [DatabaseFactory]). Every inherited contract test should turn GREEN; this is
 * the proof that the SQLDelight implementation satisfies the contract Wei reviewed and
 * approved in PR #29 (Phase 1).
 *
 * Why this lives in `:shared:jvmTest` and not `:shared:commonTest`:
 *   - [DatabaseFactory] is `expect`/`actual`; its `actual` for JVM uses [JdbcSqliteDriver]
 *     which is JVM-only. There is no actual for `commonTest` because there is no
 *     platform-agnostic in-memory SQLite driver at v0.1 (Android needs Robolectric;
 *     iOS is disabled at M1 per gradle.properties).
 *   - Same pattern as [app.toebeans.core.data.db.SchemaSmokeTest] which lives in jvmTest for
 *     the same reason. The brief for that file documented the choice; this file follows it.
 *   - The `:shared:jvmTest` source set has access to `:shared:commonTest` declarations
 *     (including [PetRepositoryContract]), so the abstract contract pattern works across
 *     source-set boundaries.
 *
 * Dispatcher choice: [Dispatchers.Unconfined] keeps the test deterministic under
 * `runTest`; the contract tests do not assert on threading, only on observable behavior.
 * Production Android wiring should inject `Dispatchers.IO` for the disk-bound SQLDelight
 * calls (see [SqlDelightPetRepository] KDoc § Threading and dispatcher choice).
 *
 * Database isolation: [DatabaseFactory.create] returns a fresh in-memory database on every
 * call; each `@BeforeTest` per test method yields a clean slate. There is no shared state
 * across tests in this class.
 *
 * FK enforcement: the JVM `DatabaseFactory` does NOT enable `PRAGMA foreign_keys=ON` (per
 * its KDoc). [PetRepositoryContract] tests do not depend on FK cascade behavior (which is
 * asserted in dependent-repo contracts, Phases 3/5/7), so this is acceptable here. Future
 * cross-repo tests that DO depend on cascade behavior must enable the pragma at setup.
 */
class SqlDelightPetRepositoryContractTest : PetRepositoryContract() {
    override fun createRepository(): PetRepository =
        SqlDelightPetRepository(
            database = DatabaseFactory().create(),
            dispatcher = Dispatchers.Unconfined,
        )
}
