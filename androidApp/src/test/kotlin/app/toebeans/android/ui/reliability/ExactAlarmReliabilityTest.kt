package app.toebeans.android.ui.reliability

import app.toebeans.core.notifications.NotificationDegraded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmReliabilityTest {
    @Test
    fun `api 30 hides the banner even when exact alarms would be denied`() {
        assertFalse(exactAlarmDenied(sdkInt = 30, canScheduleExactAlarms = false))
    }

    @Test
    fun `api 31 shows the banner when exact alarms are denied`() {
        assertTrue(exactAlarmDenied(sdkInt = 31, canScheduleExactAlarms = false))
    }

    @Test
    fun `api 31 hides the banner when exact alarms are granted`() {
        assertFalse(exactAlarmDenied(sdkInt = 31, canScheduleExactAlarms = true))
    }

    @Test
    fun `api 33 hides the banner when exact alarms are granted`() {
        assertFalse(exactAlarmDenied(sdkInt = 33, canScheduleExactAlarms = true))
    }

    @Test
    fun `constructor reads the current permission`() {
        val denied = ExactAlarmReliability(sdkInt = 31, canScheduleExactAlarms = { false })
        val granted = ExactAlarmReliability(sdkInt = 31, canScheduleExactAlarms = { true })

        assertTrue(denied.denied.value)
        assertFalse(granted.denied.value)
    }

    @Test
    fun `refresh clears the banner after the owner grants exact alarms`() {
        var canSchedule = false
        val reliability = ExactAlarmReliability(sdkInt = 31, canScheduleExactAlarms = { canSchedule })
        assertTrue(reliability.denied.value)

        canSchedule = true
        reliability.refresh()

        assertFalse(reliability.denied.value)
    }

    @Test
    fun `refresh shows the banner when exact alarms are turned off`() {
        var canSchedule = true
        val reliability = ExactAlarmReliability(sdkInt = 33, canScheduleExactAlarms = { canSchedule })
        assertFalse(reliability.denied.value)

        canSchedule = false
        reliability.refresh()

        assertTrue(reliability.denied.value)
    }

    @Test
    fun `actuator denial signal shows the banner immediately`() {
        val reliability = ExactAlarmReliability(sdkInt = 31, canScheduleExactAlarms = { true })
        assertFalse(reliability.denied.value)

        reliability.onDegraded(NotificationDegraded.EXACT_ALARM_DENIED)

        assertTrue(reliability.denied.value)
    }

    @Test
    fun `resume refresh wins after a grant even if the signal already fired`() {
        var canSchedule = false
        val reliability = ExactAlarmReliability(sdkInt = 31, canScheduleExactAlarms = { canSchedule })
        reliability.onDegraded(NotificationDegraded.EXACT_ALARM_DENIED)
        assertTrue(reliability.denied.value)

        canSchedule = true
        reliability.refresh()

        assertFalse(reliability.denied.value)
    }
}
