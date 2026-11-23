package app.toebeans.android.util

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StaleEventGuard]. Vibe-careful tier (defensive helper, not on the medication
 * path).
 *
 * The unit-test variant always sees `BuildConfig.DEBUG = true`, so the throw path is
 * directly testable. The release-mode log path is exercised through the message-builder
 * test rather than by stubbing `Log.w`, which would require a Robolectric harness for a
 * single-line side effect.
 */
class StaleEventGuardTest {
    @Test
    fun `reportMissing throws IllegalStateException in debug builds`() {
        assertThrows(IllegalStateException::class.java) {
            StaleEventGuard.reportMissing(
                site = "TestSite",
                eventId = "evt-1",
                missingFieldName = "medicationId",
                missingValue = "med-missing",
            )
        }
    }

    @Test
    fun `exception message names the site, event id, and missing field`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                StaleEventGuard.reportMissing(
                    site = "HomeViewModel.joinToUiState",
                    eventId = "evt-42",
                    missingFieldName = "medicationId",
                    missingValue = "med-deleted",
                )
            }
        val msg = ex.message ?: ""
        assertTrue("message must name the site for log-trace navigation", msg.contains("HomeViewModel.joinToUiState"))
        assertTrue("message must name the offending event id", msg.contains("evt-42"))
        assertTrue("message must name the field that couldn't be resolved", msg.contains("medicationId"))
        assertTrue("message must include the value that couldn't be resolved", msg.contains("med-deleted"))
    }

    @Test
    fun `buildMessage keeps the site, event id, and missing field traceable for a missed-dose join`() {
        // Behavior contract for the release-mode log path. The surrounding prose may be
        // reworded; a developer tracing a missed-dose join must still be able to find the
        // join site, the offending row, and the unresolved reference in the log line.
        val first =
            StaleEventGuard.buildMessage(
                site = "HomeViewModel.joinToUiState",
                eventId = "evt-42",
                missingFieldName = "medicationId",
                missingValue = "med-deleted",
            )
        assertTrue("diagnostic must name the join site", first.contains("HomeViewModel.joinToUiState"))
        assertTrue("diagnostic must name the offending event id", first.contains("evt-42"))
        assertTrue("diagnostic must name the unresolved field", first.contains("medicationId"))
        assertTrue("diagnostic must include the unresolved value", first.contains("med-deleted"))

        // A second call with different inputs proves the line is built from the failing
        // row, not a fixed sentence that happens to contain the first call's values.
        val second =
            StaleEventGuard.buildMessage(
                site = "Reminder.render",
                eventId = "evt-99",
                missingFieldName = "scheduleId",
                missingValue = "sched-gone",
            )
        assertTrue("diagnostic must follow the new site", second.contains("Reminder.render"))
        assertTrue("diagnostic must follow the new event id", second.contains("evt-99"))
        assertTrue("diagnostic must follow the new field", second.contains("scheduleId"))
        assertTrue("diagnostic must follow the new value", second.contains("sched-gone"))
        assertTrue("different rows must produce different diagnostics", first != second)
    }
}
