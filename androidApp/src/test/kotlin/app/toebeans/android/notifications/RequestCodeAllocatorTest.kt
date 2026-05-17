package app.toebeans.android.notifications

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [RequestCodeAllocator]. Uses Robolectric for a real SharedPreferences
 * implementation backed by an in-memory store.
 *
 * Tier: vibe-dangerous (the allocator sits on the medication-firing path). Treat these
 * assertions as the spec; any future refactor must satisfy each one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RequestCodeAllocatorTest {
    private lateinit var context: Context
    private lateinit var allocator: RequestCodeAllocator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Reset state between cases.
        context
            .getSharedPreferences(RequestCodeAllocator.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        allocator = RequestCodeAllocator.fromContext(context)
    }

    @Test
    fun `allocate returns INITIAL_COUNTER for the first reminder`() {
        val code = allocator.allocate("evt-1")
        assertEquals(RequestCodeAllocator.INITIAL_COUNTER, code)
    }

    @Test
    fun `allocate is idempotent for the same reminder id`() {
        val first = allocator.allocate("evt-1")
        val second = allocator.allocate("evt-1")
        assertEquals(first, second)
    }

    @Test
    fun `allocate issues distinct codes for distinct reminder ids`() {
        val a = allocator.allocate("evt-1")
        val b = allocator.allocate("evt-2")
        assertNotEquals(a, b)
    }

    @Test
    fun `allocate advances the counter monotonically`() {
        allocator.allocate("evt-1")
        allocator.allocate("evt-2")
        allocator.allocate("evt-3")
        assertEquals(RequestCodeAllocator.INITIAL_COUNTER + 3, allocator.peekNextCounter())
    }

    @Test
    fun `release removes the mapping`() {
        allocator.allocate("evt-1")
        allocator.release("evt-1")
        assertNull("after release, the mapping must be gone", allocator.peek("evt-1"))
    }

    @Test
    fun `release does NOT recycle the freed code — the counter still advances`() {
        val before = allocator.allocate("evt-1")
        allocator.release("evt-1")
        val after = allocator.allocate("evt-2")
        assertNotEquals(
            "freed codes must not be re-issued — protects against ABA on stale PendingIntents",
            before,
            after,
        )
    }

    @Test
    fun `release on an unknown id is a no-op`() {
        // Pre-allocate one so we can confirm it survives.
        val a = allocator.allocate("evt-a")
        allocator.release("evt-never-seen")
        assertEquals("unrelated allocation must survive", a, allocator.peek("evt-a"))
    }

    @Test
    fun `re-allocate after release issues a fresh code`() {
        val first = allocator.allocate("evt-1")
        allocator.release("evt-1")
        val second = allocator.allocate("evt-1")
        assertNotEquals(
            "after release, re-allocating the same id must yield a NEW code",
            first,
            second,
        )
    }

    @Test
    fun `allocator state survives reconstruction from the same context`() {
        val first = allocator.allocate("evt-1")
        val rebuilt = RequestCodeAllocator.fromContext(context)
        assertEquals(
            "two allocators backed by the same prefs file must see the same mapping",
            first,
            rebuilt.peek("evt-1"),
        )
    }

    /**
     * The canonical "Aa" / "BB" Java-hashCode collision. Direct allocator-level proof that
     * the allocator does not use hashing.
     */
    @Test
    fun `regression — Aa and BB receive distinct codes despite hashCode collision`() {
        // Pre-condition sanity.
        assertEquals("Aa".hashCode(), "BB".hashCode())

        val codeAa = allocator.allocate("Aa")
        val codeBB = allocator.allocate("BB")
        assertNotEquals(codeAa, codeBB)
    }
}
