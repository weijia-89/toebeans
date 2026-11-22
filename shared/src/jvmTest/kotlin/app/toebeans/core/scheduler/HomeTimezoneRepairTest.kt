package app.toebeans.core.scheduler

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.data.SqlDelightDoseEventRepository
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.AnchorMode
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.ScheduledReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test-as-spec for the pet-edit repair path after a quarantined home timezone. */
class HomeTimezoneRepairTest {
    private class RecordingActuator : NotificationActuator {
        val scheduled = mutableListOf<ScheduledReminder>()
        val cancelled = mutableListOf<String>()

        override fun schedule(reminder: ScheduledReminder) {
            scheduled.add(reminder)
        }

        override fun cancel(reminderId: String) {
            cancelled.add(reminderId)
        }

        override fun show(reminder: ScheduledReminder): app.toebeans.core.notifications.NotificationOutcome =
            app.toebeans.core.notifications.NotificationOutcome
                .Posted(1)
    }

    private class FixedPetRepo(
        private var pet: Pet,
    ) : PetRepository {
        override fun observeAll(): Flow<List<Pet>> = flowOf(listOf(pet))

        override suspend fun getById(id: String): Pet? = pet.takeIf { it.id == id }

        override fun observeById(id: String): Flow<Pet?> = flowOf(pet.takeIf { it.id == id })

        override suspend fun upsert(pet: Pet) {
            this.pet = pet
        }

        override suspend fun delete(id: String) = Unit
    }

    private lateinit var driver: SqlDriver
    private lateinit var db: ToebeansDatabase
    private lateinit var doseRepo: DoseEventRepository
    private lateinit var petRepo: FixedPetRepo
    private lateinit var actuator: RecordingActuator

    private fun seed(now: Instant) {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        db = ToebeansDatabase(driver)
        doseRepo = SqlDelightDoseEventRepository(database = db, dispatcher = Dispatchers.Unconfined)
        actuator = RecordingActuator()
        val pet =
            Pet(
                id = "p-repair",
                name = "Repair Pet",
                species = Species.DOG,
                birthdate = null,
                weightKg = null,
                notes = null,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                archivedAt = null,
                homeTimezone = "Mars/Olympus_Mons",
            )
        petRepo = FixedPetRepo(pet)
        db.petQueries.insertPet(
            id = pet.id,
            name = pet.name,
            species = "dog",
            birthdate_iso = null,
            weight_kg = null,
            notes = null,
            created_at = pet.createdAt.toEpochMilliseconds(),
            archived_at = null,
        )
        driver.execute(null, "UPDATE Pet SET home_timezone = 'Mars/Olympus_Mons' WHERE id = 'p-repair'", 0)
        db.medicationQueries.insertMedication(
            id = "m-repair",
            pet_id = "p-repair",
            name = "Repair Med",
            dose_amount = "1mg",
            dose_unit = "MG",
            notes = null,
            created_at = now.toEpochMilliseconds(),
            discontinued_at = null,
        )
        db.scheduleQueries.insertSchedule(
            id = "s-repair",
            medication_id = "m-repair",
            start_date_iso = "2026-07-05",
            end_date_iso = null,
            created_at = now.toEpochMilliseconds(),
            anchor_mode = AnchorMode.STAY_HOME_TZ.name,
        )
        db.scheduleQueries.upsertSchedulePhase(
            id = "ph-repair",
            schedule_id = "s-repair",
            phase_order = 0,
            duration_days = 3,
            doses_per_day = 1,
            dose_times_local = """["08:00"]""",
            dose_amount = null,
            dose_unit = null,
        )
    }

    private fun bundle() =
        listOf(
            ScheduleWithPhases(
                schedule =
                    Schedule(
                        id = "s-repair",
                        medicationId = "m-repair",
                        startDate = LocalDate(2026, 7, 5),
                        endDate = null,
                        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                        anchorMode = AnchorMode.STAY_HOME_TZ,
                    ),
                phases =
                    listOf(
                        SchedulePhase(
                            id = "ph-repair",
                            scheduleId = "s-repair",
                            phaseOrder = 0,
                            durationDays = 3,
                            dosesPerDay = 1,
                            doseTimesLocal = listOf(LocalTime(8, 0)),
                            doseAmount = null,
                            doseUnit = null,
                        ),
                    ),
            ),
        )

    private fun insertDose(
        id: String,
        at: Instant,
        status: DoseStatus,
    ) {
        db.doseEventQueries.insertDoseEvent(
            id = id,
            schedule_id = "s-repair",
            medication_id = "m-repair",
            scheduled_at = at.toEpochMilliseconds(),
            fired_at = null,
            resolved_at = null,
            status = status.wireName,
            note = null,
        )
    }

    @Test
    fun `repair keeps the stored id until a valid zone is picked then cancels old pending and re-materializes`() =
        runTest {
            val now = Instant.parse("2026-07-05T23:00:00Z")
            seed(now)
            val oldInstant = Instant.parse("2026-07-06T15:00:00Z")
            insertDose("dose-old-pending", oldInstant, DoseStatus.PENDING)
            insertDose("dose-given", oldInstant, DoseStatus.GIVEN)
            insertDose("dose-skipped", oldInstant, DoseStatus.SKIPPED)
            insertDose("dose-missed", oldInstant, DoseStatus.MISSED)

            val repaired =
                HomeTimezoneRepair.repair(
                    pet = petRepo.getById("p-repair")!!,
                    zoneId = "America/New_York",
                    schedulesWithPhases = bundle(),
                    petRepository = petRepo,
                    doseEventRepository = doseRepo,
                    notificationActuator = actuator,
                    scheduleCalculator = DefaultScheduleCalculator(),
                    deviceTimeZone = TimeZone.of("Asia/Tokyo"),
                    now = now,
                )

            assertEquals("America/New_York", petRepo.getById("p-repair")?.homeTimezone)
            assertTrue("dose-old-pending" in actuator.cancelled)
            assertNull(db.doseEventQueries.selectDoseEventById("dose-old-pending").executeAsOneOrNull())
            assertNotNull(db.doseEventQueries.selectDoseEventById("dose-given").executeAsOneOrNull())
            assertNotNull(db.doseEventQueries.selectDoseEventById("dose-skipped").executeAsOneOrNull())
            assertNotNull(db.doseEventQueries.selectDoseEventById("dose-missed").executeAsOneOrNull())
            assertTrue(repaired > 0)
            val newPending =
                db.doseEventQueries
                    .selectAllDoseEvents()
                    .executeAsList()
                    .filter { it.status == DoseStatus.PENDING.wireName }
            assertTrue(newPending.isNotEmpty())
            assertTrue(newPending.none { it.id == "dose-old-pending" })
            assertTrue(
                newPending.all { it.scheduled_at % 86_400_000L == 12L * 3_600_000L },
                "new PENDING doses must be anchored to 08:00 America/New_York = 12:00 UTC",
            )
            assertEquals(newPending.map { it.id }.toSet(), actuator.scheduled.map { it.id }.toSet())
        }

    @Test
    fun `repair rejects an invalid zone id without touching rows or alarms`() =
        runTest {
            val now = Instant.parse("2026-07-05T23:00:00Z")
            seed(now)
            val before = db.doseEventQueries.selectAllDoseEvents().executeAsList()

            assertFailsWith<IllegalArgumentException> {
                HomeTimezoneRepair.repair(
                    pet = petRepo.getById("p-repair")!!,
                    zoneId = "Mars/Olympus_Mons",
                    schedulesWithPhases = bundle(),
                    petRepository = petRepo,
                    doseEventRepository = doseRepo,
                    notificationActuator = actuator,
                    scheduleCalculator = DefaultScheduleCalculator(),
                    deviceTimeZone = TimeZone.of("Asia/Tokyo"),
                    now = now,
                )
            }

            assertEquals(before, db.doseEventQueries.selectAllDoseEvents().executeAsList())
            assertEquals(emptyList<String>(), actuator.cancelled)
            assertEquals(emptyList<ScheduledReminder>(), actuator.scheduled)
            assertEquals("Mars/Olympus_Mons", petRepo.getById("p-repair")?.homeTimezone)
        }

    @Test
    fun `quarantined pet blocks re-materialization before repair`() =
        runTest {
            val now = Instant.parse("2026-07-05T23:00:00Z")
            seed(now)
            val resolution =
                TimeZonePolicy.resolve(
                    bundle().first().schedule,
                    petRepo.getById("p-repair")!!,
                    TimeZone.of("Asia/Tokyo"),
                )

            assertEquals(TimeZonePolicy.Resolution.Quarantined, resolution)
            assertEquals(
                0,
                db.doseEventQueries
                    .selectAllDoseEvents()
                    .executeAsList()
                    .size,
            )
        }
}
