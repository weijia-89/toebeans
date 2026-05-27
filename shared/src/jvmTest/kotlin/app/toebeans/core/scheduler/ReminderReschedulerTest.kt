package app.toebeans.core.scheduler

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ReminderReschedulerTest {
    @Test
    fun `materializeHorizonForSchedule persists pending rows and returns reminders`() =
        runTest {
            val doseRepo = RecordingDoseRepo()
            val tz = TimeZone.UTC
            val start = LocalDate(2026, 6, 1)
            val now = start.atStartOfDayIn(tz) + 8.hours
            val schedule =
                Schedule(
                    id = "sched-test",
                    medicationId = MED_ID,
                    startDate = start,
                    endDate = LocalDate(2026, 6, 8),
                    createdAt = now,
                )
            val phases =
                listOf(
                    SchedulePhase(
                        id = "phase-0",
                        scheduleId = schedule.id,
                        phaseOrder = 0,
                        durationDays = 7,
                        dosesPerDay = 1,
                        doseTimesLocal = listOf(LocalTime(9, 0)),
                        doseAmount = null,
                        dayInterval = 1,
                    ),
                )
            val reminders =
                ReminderRescheduler.materializeHorizonForSchedule(
                    schedule = schedule,
                    phases = phases,
                    medicationId = MED_ID,
                    doseEventRepository = doseRepo,
                    scheduleCalculator = DefaultScheduleCalculator(),
                    timeZone = tz,
                    now = now,
                )
            assertTrue(reminders.isNotEmpty(), "BID-once-daily for 7 days inside 72h should materialize doses")
            assertEquals(reminders.size, doseRepo.upserted.size)
            assertTrue(doseRepo.upserted.all { it.status == DoseStatus.PENDING })
            assertEquals(
                ReminderRescheduler.doseEventIdForSlot(schedule.id, reminders.first().scheduledAt),
                reminders.first().id,
            )
        }

    private companion object {
        const val MED_ID = "med-1"
    }
}

private class RecordingDoseRepo : DoseEventRepository {
    val upserted = mutableListOf<DoseEvent>()

    override fun observeForPet(petId: String, sinceInclusive: Instant): Flow<List<DoseEvent>> =
        flowOf(emptyList())

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> = flowOf(null)

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<DoseEvent>> = flowOf(upserted)

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent = error("unused")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent = error("unused")

    override suspend fun delete(doseEventId: String) = Unit

    override suspend fun upsert(event: DoseEvent) {
        upserted.add(event)
    }
}
