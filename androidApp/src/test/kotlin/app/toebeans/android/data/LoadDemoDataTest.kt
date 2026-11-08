package app.toebeans.android.data

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.ReminderRescheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/**
 * Tests for [loadDemoData] using in-memory fakes (same contracts as production DI).
 */
class LoadDemoDataTest {
    private val now: Instant = Instant.parse("2026-11-08T15:00:00Z")
    private val timeZone: TimeZone = TimeZone.UTC

    @Test
    fun `loadDemoData populates Rufus and Luna`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            load(petRepo, medRepo, schedRepo)
            assertEquals(2, petRepo.observeAll().first().size)
            assertNotNull(petRepo.getById("pet-rufus"))
            assertNotNull(petRepo.getById("pet-luna"))
        }

    @Test
    fun `loadDemoData populates the Methimazole medication for Luna`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            load(petRepo, medRepo, schedRepo)
            val med = medRepo.getById("med-luna-methimazole")
            assertNotNull(med)
            assertEquals("pet-luna", med!!.petId)
            assertEquals("Methimazole", med.name)
        }

    @Test
    fun `loadDemoData populates the Methimazole schedule with one BID phase`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            load(petRepo, medRepo, schedRepo)
            val schedule = schedRepo.observeById("sched-luna-methimazole").first()
            assertNotNull(schedule)
            val phases = schedRepo.observePhases("sched-luna-methimazole").first()
            assertEquals(2, phases.single().dosesPerDay)
        }

    @Test
    fun `loadDemoData seeds multiple meds per pet with staggered dose times`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            load(petRepo, medRepo, schedRepo)
            val meds = medRepo.observeAll().first()
            assertEquals(6, meds.size)
            assertEquals(3, meds.count { it.petId == "pet-rufus" })
            assertEquals(3, meds.count { it.petId == "pet-luna" })
            val rufusMorning =
                schedRepo
                    .observePhases("sched-rufus-carprofen")
                    .first()
                    .single()
                    .doseTimesLocal
            val lunaEvening =
                schedRepo
                    .observePhases("sched-luna-fortiflora")
                    .first()
                    .single()
                    .doseTimesLocal
            assertEquals(listOf(kotlinx.datetime.LocalTime(8, 0)), rufusMorning)
            assertEquals(listOf(kotlinx.datetime.LocalTime(17, 30)), lunaEvening)
        }

    @Test
    fun `loadDemoData is idempotent`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            load(petRepo, medRepo, schedRepo)
            load(petRepo, medRepo, schedRepo)
            assertEquals(2, petRepo.observeAll().first().size)
            assertEquals(6, medRepo.observeAll().first().size)
            assertEquals(6, schedRepo.observeAll().first().size)
        }

    @Test
    fun `loadDemoData preserves user-created entries`() =
        runTest {
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            petRepo.upsert(
                app.toebeans.core.model.Pet(
                    id = "pet-user-cat",
                    name = "Tabby",
                    species = app.toebeans.core.model.Species.CAT,
                    birthdate = kotlinx.datetime.LocalDate(2021, 5, 1),
                    weightKg = 3.5,
                    notes = null,
                    createdAt = seedCreatedAt,
                    archivedAt = null,
                ),
            )
            load(petRepo, medRepo, schedRepo)
            assertEquals(3, petRepo.observeAll().first().size)
            assertNotNull(petRepo.getById("pet-user-cat"))
        }

    @Test
    fun `loadDemoData writes pending doses in the 72-hour horizon and registers those dose ids`() =
        runTest {
            pets.value = emptyMap()
            medications.value = emptyMap()
            schedules.value = emptyMap()
            phasesByScheduleId.value = emptyMap()
            doseEvents.value = emptyMap()
            val petRepo = FakePetRepository()
            val medRepo = FakeMedicationRepository()
            val schedRepo = FakeScheduleRepository()
            val doseRepo = FakeDoseEventRepository(medRepo, schedRepo)
            val actuator = RecordingNotificationActuator()
            load(petRepo, medRepo, schedRepo, doseRepo, actuator)

            val horizonEnd = now + ReminderRescheduler.HORIZON_HOURS.hours
            val doses = doseRepo.observeAll().first()
            val schedules = schedRepo.observeAll().first()
            for (schedule in schedules) {
                val pendingInHorizon =
                    doses.filter { dose ->
                        dose.scheduleId == schedule.id &&
                            dose.status == DoseStatus.PENDING &&
                            dose.scheduledAt >= now &&
                            dose.scheduledAt < horizonEnd
                    }
                assertTrue(schedule.id, pendingInHorizon.isNotEmpty())
            }
            assertEquals(
                doses.map { it.id }.sorted(),
                actuator.scheduled.map { it.id }.sorted(),
            )

            val doseIds = doses.map { it.id }.toSet()
            load(petRepo, medRepo, schedRepo, doseRepo, actuator)
            val doseIdsAfterSecondLoad =
                doseRepo
                    .observeAll()
                    .first()
                    .map { it.id }
                    .toSet()
            assertEquals(doseIds, doseIdsAfterSecondLoad)
        }

    private suspend fun load(
        petRepo: PetRepository,
        medRepo: MedicationRepository,
        schedRepo: ScheduleRepository,
        doseRepo: DoseEventRepository = FakeDoseEventRepository(medRepo, schedRepo),
        actuator: NotificationActuator = RecordingNotificationActuator(),
    ) {
        loadDemoData(
            petRepository = petRepo,
            medicationRepository = medRepo,
            scheduleRepository = schedRepo,
            doseEventRepository = doseRepo,
            scheduleCalculator = DefaultScheduleCalculator(),
            notificationActuator = actuator,
            now = now,
            timeZone = timeZone,
        )
    }
}

private class RecordingNotificationActuator : NotificationActuator {
    val scheduled = mutableListOf<ScheduledReminder>()

    override fun schedule(reminder: ScheduledReminder) {
        scheduled.add(reminder)
    }

    override fun cancel(reminderId: String) = Unit

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}
