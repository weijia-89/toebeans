package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.Test

/**
 * Smoke test for [MedicalRepositoryContract]. The abstract base has no real consumers
 * yet; those land in Phases 3 / 5 / 7 (see the inheritance shape in the parent class's
 * KDoc). This subclass exists strictly to prove the abstract compiles, can be
 * subclassed, and the per-test `@BeforeTest` setup runs without throwing when
 * [obtainDriver] returns null.
 *
 * Once Phase 3 lands `MedicationRepositoryContract`, this smoke test remains as the
 * regression gate for the null-driver path (the path taken by stub, fake, and future
 * iOS-bridge subclasses).
 *
 * The test body is empty on purpose. The `@BeforeTest` `setupMedicalDb()` runs before
 * this method via the kotlin-test runner; if [obtainDriver] returns null, `configureDb`
 * is never invoked, so the setup is a no-op. Reaching the test method body without
 * an exception is the assertion.
 */
class MedicalRepositoryContractSmokeTest : MedicalRepositoryContract() {
    override fun obtainDriver(): SqlDriver? = null

    @Test
    fun `setupMedicalDb is a no-op when obtainDriver returns null`() {
        // Intentional empty body. See class KDoc.
    }
}
