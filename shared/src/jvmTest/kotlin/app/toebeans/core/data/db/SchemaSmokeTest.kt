package app.toebeans.core.data.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Smoke test for the SQLDelight schema split (Pet.sq / Medication.sq / Schedule.sq /
 * DoseEvent.sq). Asserts that:
 *
 *  - The four per-entity .sq files compile and produce a usable [ToebeansDatabase].
 *  - Every per-entity "select all" query executes against the freshly-migrated
 *    empty schema and returns an empty result (no spurious seed rows).
 *  - Cross-file table references (Medication.pet_id -> Pet.id, etc.) resolve at
 *    SQLDelight code-gen time. If a future SQLDelight version regression breaks
 *    cross-file FK refs, this test fails at the database-construction step.
 *
 * Lives in `:shared:jvmTest` (not commonTest, per the brief's specified path)
 * because [JdbcSqliteDriver] is JVM-only. The brief's intent — "test runs as
 * JVM unit tests in :shared via ./gradlew :shared:jvmTest" — is satisfied by
 * placing the test directly in the JVM-only source set, avoiding the need for
 * expect/actual indirection across iOS/Android test targets that don't exist
 * for this test.
 */
class SchemaSmokeTest {
    @Test
    fun `every per-entity select-all query returns empty against a fresh in-memory database`() {
        val db = DatabaseFactory().create()

        assertEquals(emptyList(), db.petQueries.selectAllPets().executeAsList())
        assertEquals(emptyList(), db.petQueries.selectAllActivePets().executeAsList())
        assertEquals(emptyList(), db.medicationQueries.selectAllMedications().executeAsList())
        assertEquals(emptyList(), db.scheduleQueries.selectAllSchedules().executeAsList())
        assertEquals(emptyList(), db.scheduleQueries.selectAllSchedulePhases().executeAsList())
        assertEquals(emptyList(), db.doseEventQueries.selectAllDoseEvents().executeAsList())
    }

    @Test
    fun `selectById queries return null on a fresh in-memory database`() {
        val db = DatabaseFactory().create()

        assertNull(db.petQueries.selectPetById("nonexistent").executeAsOneOrNull())
        assertNull(db.medicationQueries.selectMedicationById("nonexistent").executeAsOneOrNull())
        assertNull(db.scheduleQueries.selectScheduleById("nonexistent").executeAsOneOrNull())
    }
}
