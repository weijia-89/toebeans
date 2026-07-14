package app.toebeans.core.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryMedicationNameIndexTest {
    private val testMedications =
        listOf(
            "Amoxicillin",
            "Clavamox",
            "Cephalexin",
            "Gabapentin",
            "Meloxicam",
            "Prednisone",
            "Metronidazole",
            "Carprofen",
            "Rimadyl",
            "Tramadol",
        )

    private fun createIndex(medications: List<String> = testMedications) = InMemoryMedicationNameIndex(medications)

    @Test
    fun search_exactMatch_returnsAsFirstResult() =
        runTest {
            val index = createIndex()
            val results = index.search("Gabapentin")
            assertEquals("Gabapentin", results.first())
        }

    @Test
    fun search_prefixMatch_returnsMatchingNames() =
        runTest {
            val index = createIndex()
            val results = index.search("amox")
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.contains("amox", ignoreCase = true) })
        }

    @Test
    fun search_substringMatch_returnsMatchingNames() =
        runTest {
            val index = createIndex()
            val results = index.search("cillin")
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.contains("cillin", ignoreCase = true) })
        }

    @Test
    fun search_caseInsensitive_matchesRegardlessOfCase() =
        runTest {
            val index = createIndex()
            val lowerResults = index.search("gabapentin")
            val upperResults = index.search("GABAPENTIN")
            val mixedResults = index.search("GaBaPeNtIn")
            assertEquals(lowerResults, upperResults)
            assertEquals(lowerResults, mixedResults)
        }

    @Test
    fun search_limitsResultsToSpecifiedCount() =
        runTest {
            val index = createIndex()
            val results = index.search("a", limit = 3)
            assertTrue(results.size <= 3)
        }

    @Test
    fun search_blankQuery_returnsAllLimited() =
        runTest {
            val index = createIndex()
            val results = index.search("   ")
            assertEquals(10, results.size) // default limit
        }

    @Test
    fun search_emptyQuery_returnsAllLimited() =
        runTest {
            val index = createIndex()
            val results = index.search("")
            assertEquals(10, results.size) // default limit
        }

    @Test
    fun search_noMatches_returnsEmptyList() =
        runTest {
            val index = createIndex()
            val results = index.search("xyznotreal")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_ordering_exactBeforePrefixBeforeSubstring() =
        runTest {
            val meds = listOf("Test", "Testing", "Contest", "ATEST")
            val index = InMemoryMedicationNameIndex(meds)
            val results = index.search("test")
            // Exact "Test" should come first, then prefix "Testing", then substrings "Contest", "ATEST"
            assertEquals("Test", results.first())
            assertTrue(results.contains("Testing"))
            assertTrue(results.contains("Contest"))
            assertTrue(results.contains("ATEST"))
        }

    @Test
    fun getAll_returnsAllSortedAlphabetically() =
        runTest {
            val meds = listOf("Zebra", "Apple", "Mango", "Banana")
            val index = InMemoryMedicationNameIndex(meds)
            val results = index.getAll()
            assertEquals(listOf("Apple", "Banana", "Mango", "Zebra"), results)
        }
}
