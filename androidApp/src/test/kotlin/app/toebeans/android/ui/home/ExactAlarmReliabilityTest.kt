package app.toebeans.android.ui.home

import app.toebeans.core.notifications.NotificationDegraded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0002: exact-alarm denial is a signal the Today banner can show.
 * The actuator raises [NotificationDegraded.EXACT_ALARM_DENIED]; this holder
 * is what the screen reads. Below API 31 the permission does not exist, so
 * the banner stays off even if a caller passes a false schedule flag.
 */
class ExactAlarmReliabilityTest {
    @Test
    fun `exact alarm denied signal turns the banner on`() {
        val reliability = ExactAlarmReliability()
        assertFalse(reliability.exactAlarmDenied.value)

        reliability.note(NotificationDegraded.EXACT_ALARM_DENIED)

        assertTrue(reliability.exactAlarmDenied.value)
    }

    @Test
    fun `publishing granted clears a previous denial`() {
        val reliability = ExactAlarmReliability()
        reliability.note(NotificationDegraded.EXACT_ALARM_DENIED)

        reliability.setDenied(false)

        assertFalse(reliability.exactAlarmDenied.value)
    }

    @Test
    fun `denial requires the exact-alarm permission gate`() {
        assertFalse(ExactAlarmReliability.denied(requiresExactAlarmPermission = false, canScheduleExactAlarms = false))
        assertFalse(ExactAlarmReliability.denied(requiresExactAlarmPermission = true, canScheduleExactAlarms = true))
        assertTrue(ExactAlarmReliability.denied(requiresExactAlarmPermission = true, canScheduleExactAlarms = false))
    }

    @Test
    fun `banner copy names the 10 minute window`() {
        assertEquals(
            "Exact alarms are off, so a dose can fire up to 10 minutes late. " +
                "Turn on Alarms and reminders for toebeans.",
            EXACT_ALARM_DENIED_MESSAGE,
        )
    }
}
