package app.toebeans.android.ui.home

import android.content.Context
import app.toebeans.core.data.SqlDelightDoseEventRepository
import app.toebeans.core.data.SqlDelightMedicationRepository
import app.toebeans.core.data.SqlDelightPetRepository
import app.toebeans.core.data.SqlDelightScheduleRepository
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import app.toebeans.core.scheduler.ReminderRescheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours

/**
 * Integration regression: Today "Log dose" must upgrade the materialized PENDING row at
 * the schedule slot (stable id) so [HomeViewModel.computeDueToday] flips the worklist row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeMarkGivenSlotIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: ToebeansDatabase
    private val dbName = "home-mark-given-slot-test.db"
    private val zone = TimeZone.of("America/New_York")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
        database =
            DatabaseFactory(
                context = context,
                databaseName = dbName,
                callback =
                    app.toebeans.android.data
                        .SqliteForeignKeysCallback(),
            ).create()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Suppress("LongMethod")
    @Test
    fun `markGiven upgrades pending slot row and flips due worklist`() =
        runTest {
            val dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            val petRepo = SqlDelightPetRepository(database, dispatcher)
            val medRepo = SqlDelightMedicationRepository(database, dispatcher)
            val schedRepo = SqlDelightScheduleRepository(database, dispatcher)
            val doseRepo = SqlDelightDoseEventRepository(database, dispatcher)
            val calculator = DefaultScheduleCalculator()

            val today = Clock.System.todayIn(zone)
            val petId = "pet-test"
            val medId = "med-test"
            val schedId = "sched-test"
            petRepo.upsert(
                Pet(
                    id = petId,
                    name = "Luna",
                    species = Species.CAT,
                    birthdate = null,
                    weightKg = 4.0,
                    notes = null,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                    archivedAt = null,
                ),
            )
            medRepo.upsert(
                Medication(
                    id = medId,
                    petId = petId,
                    name = "Methimazole",
                    doseAmount = "2.5 mg",
                    notes = null,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                    discontinuedAt = null,
                ),
            )
            val schedule =
                Schedule(
                    id = schedId,
                    medicationId = medId,
                    startDate = today,
                    endDate = null,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                )
            val phase =
                SchedulePhase(
                    id = "phase-0",
                    scheduleId = schedId,
                    phaseOrder = 0,
                    durationDays = SchedulePhase.MAX_DURATION_DAYS,
                    dosesPerDay = 1,
                    doseTimesLocal = listOf(LocalTime(8, 0)),
                    doseAmount = null,
                    dayInterval = 1,
                )
            schedRepo.upsert(schedule, listOf(phase))

            val todayStart = today.atStartOfDayIn(zone)
            val todayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            val slot =
                calculator
                    .computeScheduledDoses(
                        schedule = schedule,
                        phases = listOf(phase),
                        timeZone = zone,
                        fromInclusive = todayStart,
                        toExclusive = todayEnd,
                    ).single()
                    .scheduledAt

            val now = todayStart + 1.hours
            ReminderRescheduler.materializeHorizonForSchedule(
                schedule = schedule,
                phases = listOf(phase),
                medicationId = medId,
                doseEventRepository = doseRepo,
                scheduleCalculator = calculator,
                timeZone = zone,
                now = now,
            )

            val stableId = ReminderRescheduler.doseEventIdForSlot(schedId, slot)
            doseRepo.recordGivenForSlot(
                doseEventId = stableId,
                scheduleId = schedId,
                medicationId = medId,
                scheduledAt = slot,
                resolvedAt = now + kotlin.time.Duration.parse("5m"),
            )

            val midnight = todayStart
            val recent = doseRepo.observeAllRecent(midnight).first()
            assertEquals(1, recent.size)
            assertEquals(DoseStatus.GIVEN, recent.single().status)
            assertEquals(stableId, recent.single().id)

            val swp = schedRepo.observeActiveWithPhases(today).first()
            val due =
                HomeViewModel.computeDueToday(
                    schedulesWithPhases = swp,
                    medications = medRepo.observeAll().first(),
                    pets = petRepo.observeAll().first(),
                    recentDoses = recent,
                    calculator = calculator,
                    timeZone = zone,
                    todayStart = todayStart,
                    todayEnd = todayEnd,
                )
            assertEquals(1, due.size)
            assertEquals(stableId, due.single().givenEventId)
            assertTrue(due.single().isGiven)
            val givenAtSlot =
                doseRepo.observeAll().first().filter {
                    it.scheduleId == schedId &&
                        it.scheduledAt == slot &&
                        it.status == DoseStatus.GIVEN
                }
            assertEquals("must not insert a second GIVEN row for the slot", 1, givenAtSlot.size)
        }
}
