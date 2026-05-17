package app.toebeans.android.util

import org.junit.Assert.assertEquals
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
    fun `buildMessage produces a stable diagnostic format`() {
        // Pure builder; useful for the release-mode log path which we can't easily
        // intercept in a unit test. The message format is part of the contract because
        // future log-aggregation work may grep for it.
        val msg =
            StaleEventGuard.buildMessage(
                site = "S",
                eventId = "E",
                missingFieldName = "F",
                missingValue = "V",
            )
        assertEquals(
            "stale row at S: event=E references F=V which no longer exists. Likely an " +
                "inter-Flow race during deletion; the render will skip this row in release " +
                "builds and crash in debug to surface any real join bug.",
            msg,
        )
    }
}
