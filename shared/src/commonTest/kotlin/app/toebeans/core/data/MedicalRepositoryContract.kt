package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.BeforeTest

/**
 * Abstract test-as-spec base for repositories whose tests need configurable per-test
 * database setup. Currently used as the parent of `MedicationRepositoryContract`,
 * `ScheduleRepositoryContract`, and `DoseEventRepositoryContract` (all upcoming in
 * Phases 3 / 5 / 7 of the Decision 4a sequence). [PetRepositoryContract] does NOT
 * extend this base because Pet is the top of the foreign-key chain; its tests do
 * not assert FK cascade behavior (per AGENTS.md § Test-as-spec rules and ADR-0010).
 *
 * Why a separate base from [PetRepositoryContract]:
 *
 *   1. Medication, Schedule, and DoseEvent are all FK children of Pet (per ADR-0010).
 *      Their cascade tests need `PRAGMA foreign_keys=ON` enabled per-test, which
 *      requires the [SqlDriver] to be reachable from the test fixture. Pet's contract
 *      has no such need; the Phase-1 KDoc on [PetRepositoryContract] explicitly defers
 *      cascade-test infrastructure to the dependent contracts.
 *
 *   2. Pet is the parent/owner entity. Medication, Schedule, and DoseEvent are
 *      health/medical data about a pet. The two categories have different lifecycle
 *      semantics (a pet outlives a medication course; a course outlives a single
 *      dose), and are likely to diverge further as the data model grows.
 *
 *   3. Future roadmap: if encryption-at-rest is added for medical data (per Wei's
 *      directive 2026-05-20), the per-test setup hook required to enable encryption
 *      will live on this base. Pet's contract does not gain that hook. Naming the
 *      base `MedicalRepositoryContract` rather than `EncryptedRepositoryContract`
 *      follows the naming iron law at
 *      `/Users/wjia/Projects/.windsurf/rules/naming-iron-law.md`: the name describes
 *      the artifact's current scope (medical data), not a speculative future
 *      property (encryption). The encryption setup slot will land when the
 *      encryption work begins, not preemptively.
 *
 * This PR (the precursor to Phase 3) ships only the abstract base plus a minimal
 * smoke test in the same source set. There are no real consumers yet; Phase 3 adds
 * `MedicationRepositoryContract` that extends this class. The smoke test is the
 * load-bearing falsifier: it proves the abstract compiles, can be subclassed, and
 * the `@BeforeTest` setup runs without throwing when no driver is available.
 *
 * Subclasses MUST override [obtainDriver] to return either a non-null [SqlDriver]
 * (for SQLDelight-backed concrete subclasses, e.g. the future
 * `SqlDelightMedicationRepositoryContractTest`) or null (for stub, fake, or iOS-bridge
 * subclasses that have no SQL driver). When [obtainDriver] returns non-null,
 * [configureDb] runs as part of the per-test setup.
 *
 * Subclasses MAY override [configureDb] to perform driver-level configuration. The
 * canonical Phase-3-onward use is `driver.execute(null, "PRAGMA foreign_keys=ON", 0)`
 * per ADR-0010. The default implementation is a no-op so subclasses that do not
 * need FK enforcement inherit safely.
 *
 * Inheritance shape:
 *
 * ```
 * MedicalRepositoryContract                       (this file; abstract; infrastructure)
 *   ↳ MedicationRepositoryContract                (Phase 3; abstract; defines @Test methods)
 *       ↳ SqlDelightMedicationRepositoryContractTest  (Phase 4; concrete; real impl)
 *       ↳ StubMedicationRepositoryContractTest        (Phase 3; concrete; RED)
 *   ↳ ScheduleRepositoryContract                  (Phase 5; same shape)
 *   ↳ DoseEventRepositoryContract                 (Phase 7; same shape)
 * ```
 */
abstract class MedicalRepositoryContract {
    /**
     * Concrete subclasses return either the [SqlDriver] backing the test's repository
     * (for SQLDelight-backed impls) or null (for stub / fake / non-SQL impls that have
     * no driver). Called once per test by [setupMedicalDb] via [BeforeTest].
     */
    protected abstract fun obtainDriver(): SqlDriver?

    /**
     * Per-test driver configuration hook. The default is a no-op. Subclasses that
     * need to enable `PRAGMA foreign_keys=ON` (i.e. all SQLDelight-backed subclasses
     * of dependent contracts) override this to call
     * `driver.execute(null, "PRAGMA foreign_keys=ON", 0)` per ADR-0010.
     *
     * Invoked from [setupMedicalDb] AFTER [obtainDriver] returns a non-null driver.
     * Never invoked when [obtainDriver] returns null.
     */
    protected open fun configureDb(driver: SqlDriver) {}

    @BeforeTest
    fun setupMedicalDb() {
        obtainDriver()?.let(::configureDb)
    }
}
