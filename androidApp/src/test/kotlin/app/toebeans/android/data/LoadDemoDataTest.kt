package app.toebeans.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [loadDemoData] — the function the first-launch dialog calls when the user taps
 * "Load demo data". The function mutates module-level [MutableStateFlow]s, so each test
 * resets them in [setUp].
 *
 * Vibe-careful tier (UX seed, not the medication path).
 */
class LoadDemoDataTest {
    @Before
    fun setUp() {
        // The module-level stores are process-global; reset them so test order does not
        // affect outcome.
        pets.value = emptyMap()
        medications.value = emptyMap()
        schedules.value = emptyMap()
        phasesByScheduleId.value = emptyMap()
        doseEvents.value = emptyMap()
    }

    @Test
    fun `fresh state — stores start empty before loadDemoData`() {
        assertTrue("pets must start empty", pets.value.isEmpty())
        assertTrue("medications must start empty", medications.value.isEmpty())
        assertTrue("schedules must start empty", schedules.value.isEmpty())
        assertTrue("phases must start empty", phasesByScheduleId.value.isEmpty())
    }

    @Test
    fun `loadDemoData populates Rufus and Luna`() {
        loadDemoData()
        assertEquals(2, pets.value.size)
        assertNotNull("Rufus must be seeded", pets.value["pet-rufus"])
        assertNotNull("Luna must be seeded", pets.value["pet-luna"])
    }

    @Test
    fun `loadDemoData populates the Methimazole medication for Luna`() {
        loadDemoData()
        val med = medications.value["med-luna-methimazole"]
        assertNotNull("Methimazole medication must be seeded", med)
        assertEquals("pet-luna", med!!.petId)
        assertEquals("Methimazole", med.name)
    }

    @Test
    fun `loadDemoData populates the Methimazole schedule with one BID phase`() {
        loadDemoData()
        val schedule = schedules.value["sched-luna-methimazole"]
        assertNotNull("Methimazole schedule must be seeded", schedule)
        val phases = phasesByScheduleId.value["sched-luna-methimazole"]
        assertNotNull("phases for the Methimazole schedule must be seeded", phases)
        assertEquals("the demo phase must dose twice daily", 2, phases!![0].dosesPerDay)
    }

    @Test
    fun `loadDemoData is idempotent — running twice does not duplicate entries`() {
        loadDemoData()
        loadDemoData()
        assertEquals("running twice must not duplicate pets", 2, pets.value.size)
        assertEquals("running twice must not duplicate medications", 1, medications.value.size)
        assertEquals("running twice must not duplicate schedules", 1, schedules.value.size)
        assertEquals(
            "running twice must not duplicate phases",
            1,
            phasesByScheduleId.value["sched-luna-methimazole"]!!.size,
        )
    }

    @Test
    fun `loadDemoData preserves user-created entries`() {
        // Simulate a real user who added a pet BEFORE tapping "Load demo data" (unlikely
        // flow but defensive — we never want demo seeding to clobber real data).
        pets.value =
            pets.value +
            (
                "pet-user-cat" to
                    app.toebeans.core.model.Pet(
                        id = "pet-user-cat",
                        name = "Tabby",
                        species = app.toebeans.core.model.Species.CAT,
                        birthdate = kotlinx.datetime.LocalDate(2021, 5, 1),
                        weightKg = 3.5,
                        notes = null,
                        createdAt = seedCreatedAt,
                        archivedAt = null,
                    )
            )
        loadDemoData()
        assertEquals("user-created pet must still be there", 3, pets.value.size)
        assertNotNull(
            "user-created pet must not be clobbered",
            pets.value["pet-user-cat"],
        )
    }
}
