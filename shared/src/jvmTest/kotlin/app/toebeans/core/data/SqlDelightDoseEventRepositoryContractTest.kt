package app.toebeans.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.notifications.SqlDelightReminderLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Contract-style tests for [SqlDelightDoseEventRepository] on an isolated in-memory JDBC driver.
 *
 * Proves insert → [selectDoseEventById] visibility for the receiver fire path (v0.1-followups §3:
 * rows must land in `toebeans.db` before [NotificationActuator][app.toebeans.core.notifications.NotificationActuator]
 * schedules the alarm).
 */
class SqlDelightDoseEventRepositoryContractTest {
    private lateinit var database: ToebeansDatabase
    private lateinit var repo: SqlDelightDoseEventRepository

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        database = ToebeansDatabase(driver)
        seedParentChain(scheduleId = SCHEDULE_ID)
        repo =
            SqlDelightDoseEventRepository(
                database = database,
                dispatcher = Dispatchers.Unconfined,
            )
    }

    @Test
    fun `recordGivenNow persists row visible to selectDoseEventById for receiver lookup`() =
        runTest {
            val at = Instant.parse("2026-05-24T08:00:00Z")
            repo.recordGivenNow(
                doseEventId = DOSE_ID,
                scheduleId = SCHEDULE_ID,
                medicationId = MED_ID,
                at = at,
                note = "quick log",
            )

            val row =
                database.doseEventQueries
                    .selectDoseEventById(DOSE_ID)
                    .executeAsOneOrNull()
            assertNotNull(row, "insert must be visible to indexed receiver lookup query")
            assertEquals(DOSE_ID, row.id)
            assertEquals(SCHEDULE_ID, row.schedule_id)
            assertEquals(MED_ID, row.medication_id)
            assertEquals("given", row.status)

            val lookup = SqlDelightReminderLookup(database)
            val reminder = lookup.lookup(DOSE_ID)
            assertNotNull(reminder, "SqlDelightReminderLookup must resolve persisted dose id")
            assertEquals(SCHEDULE_ID, reminder.scheduleId)
            assertEquals(at, reminder.scheduledAt)
        }

    @Test
    fun `recordGivenNow round-trips via observeLastGivenForMedication`() =
        runTest {
            val at = Instant.parse("2026-05-24T12:00:00Z")
            repo.recordGivenNow(
                doseEventId = DOSE_ID,
                scheduleId = SCHEDULE_ID,
                medicationId = MED_ID,
                at = at,
            )

            val last = repo.observeLastGivenForMedication(MED_ID).first()
            assertNotNull(last)
            assertEquals(DoseStatus.GIVEN, last.status)
            assertEquals(at, last.scheduledAt)
        }

    @Test
    fun `recordGivenForSlot is idempotent on scheduleId and scheduledAt`() =
        runTest {
            val slot = Instant.parse("2026-05-24T09:00:00Z")
            val firstResolved = Instant.parse("2026-05-24T09:05:00Z")
            val secondResolved = Instant.parse("2026-05-24T09:07:00Z")

            val first =
                repo.recordGivenForSlot(
                    doseEventId = "dose-slot-a",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = slot,
                    resolvedAt = firstResolved,
                )
            val second =
                repo.recordGivenForSlot(
                    doseEventId = "dose-slot-b",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = slot,
                    resolvedAt = secondResolved,
                )

            assertEquals(first.id, second.id, "second call must replace the same slot row")
            assertEquals(secondResolved, second.resolvedAt)

            val all = repo.observeAll().first()
            assertEquals(1, all.size, "idempotent slot replace must not duplicate rows")
        }

    @Test
    fun `upsert round-trips pending and given rows via observeAll for backup import`() =
        runTest {
            // sdk-review F2: backup import calls upsert verbatim; must round-trip all statuses.
            val pendingAt = Instant.parse("2026-05-24T07:00:00Z")
            val givenAt = Instant.parse("2026-05-24T08:00:00Z")
            val resolvedAt = Instant.parse("2026-05-24T08:05:00Z")

            repo.upsert(
                DoseEvent(
                    id = "dose-pending-import",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = pendingAt,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )
            repo.upsert(
                DoseEvent(
                    id = "dose-given-import",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = givenAt,
                    firedAt = givenAt,
                    resolvedAt = resolvedAt,
                    status = DoseStatus.GIVEN,
                    note = "imported",
                ),
            )

            val all = repo.observeAll().first()
            assertEquals(2, all.size)
            val pending = all.single { it.id == "dose-pending-import" }
            assertEquals(DoseStatus.PENDING, pending.status)
            assertEquals(pendingAt, pending.scheduledAt)
            val given = all.single { it.id == "dose-given-import" }
            assertEquals(DoseStatus.GIVEN, given.status)
            assertEquals("imported", given.note)
            assertEquals(resolvedAt, given.resolvedAt)
        }

    @Test
    fun `upsert overwrites existing row by id`() =
        runTest {
            val slot = Instant.parse("2026-05-24T11:00:00Z")
            repo.upsert(
                DoseEvent(
                    id = DOSE_ID,
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = slot,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = "before",
                ),
            )
            val resolvedAt = Instant.parse("2026-05-24T11:30:00Z")
            repo.upsert(
                DoseEvent(
                    id = DOSE_ID,
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = slot,
                    firedAt = slot,
                    resolvedAt = resolvedAt,
                    status = DoseStatus.GIVEN,
                    note = "after",
                ),
            )

            val all = repo.observeAll().first()
            assertEquals(1, all.size)
            assertEquals(DoseStatus.GIVEN, all.single().status)
            assertEquals("after", all.single().note)
            assertEquals(resolvedAt, all.single().resolvedAt)
        }

    @Test
    fun `observeAllRecent filters GIVEN since midnight and caps at 50 for Home Logged today`() =
        runTest {
            // sdk-review F2: HomeViewModel drives "Logged today" via observeAllRecent(localMidnightToday()).
            val since = Instant.parse("2026-05-24T00:00:00Z")
            val givenBeforeSince = Instant.parse("2026-05-23T20:00:00Z")
            val pendingAfterSince = Instant.parse("2026-05-24T07:00:00Z")
            val givenMorning = Instant.parse("2026-05-24T08:00:00Z")
            val givenNoon = Instant.parse("2026-05-24T12:00:00Z")

            repo.upsert(
                DoseEvent(
                    id = "dose-given-before-since",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = givenBeforeSince,
                    firedAt = givenBeforeSince,
                    resolvedAt = givenBeforeSince,
                    status = DoseStatus.GIVEN,
                    note = null,
                ),
            )
            repo.upsert(
                DoseEvent(
                    id = "dose-pending-after-since",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = pendingAfterSince,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )
            repo.upsert(
                DoseEvent(
                    id = "dose-given-morning",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = givenMorning,
                    firedAt = givenMorning,
                    resolvedAt = givenMorning,
                    status = DoseStatus.GIVEN,
                    note = "morning",
                ),
            )
            repo.upsert(
                DoseEvent(
                    id = "dose-given-noon",
                    scheduleId = SCHEDULE_ID,
                    medicationId = MED_ID,
                    scheduledAt = givenNoon,
                    firedAt = givenNoon,
                    resolvedAt = givenNoon,
                    status = DoseStatus.GIVEN,
                    note = "noon",
                ),
            )

            val recent = repo.observeAllRecent(since).first()
            assertEquals(2, recent.size, "only GIVEN rows on/after sinceInclusive")
            assertEquals(
                listOf("dose-given-noon", "dose-given-morning"),
                recent.map { it.id },
                "DESC by scheduledAt matches selectAllGivenSince ORDER BY",
            )

            repeat(51) { index ->
                val at = Instant.parse("2026-05-24T13:${index.toString().padStart(2, '0')}:00Z")
                repo.upsert(
                    DoseEvent(
                        id = "dose-limit-$index",
                        scheduleId = SCHEDULE_ID,
                        medicationId = MED_ID,
                        scheduledAt = at,
                        firedAt = at,
                        resolvedAt = at,
                        status = DoseStatus.GIVEN,
                        note = null,
                    ),
                )
            }

            val capped = repo.observeAllRecent(since).first()
            assertEquals(50, capped.size, "OBSERVE_ALL_RECENT_LIMIT caps Home read path")
        }

    @Test
    fun `delete removes row from selectDoseEventById`() =
        runTest {
            val at = Instant.parse("2026-05-24T10:00:00Z")
            repo.recordGivenNow(
                doseEventId = DOSE_ID,
                scheduleId = SCHEDULE_ID,
                medicationId = MED_ID,
                at = at,
            )
            repo.delete(DOSE_ID)

            val row =
                database.doseEventQueries
                    .selectDoseEventById(DOSE_ID)
                    .executeAsOneOrNull()
            assertNull(row)
        }

    private fun seedParentChain(scheduleId: String) {
        val createdAt = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
        database.petQueries.upsertPet(
            id = PET_ID,
            name = "Contract Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 10.0,
            notes = null,
            created_at = createdAt,
            archived_at = null,
        )
        database.medicationQueries.upsertMedication(
            id = MED_ID,
            pet_id = PET_ID,
            name = "Contract Med",
            dose_amount = "5mg",
            notes = null,
            created_at = createdAt,
            discontinued_at = null,
        )
        database.scheduleQueries.upsertSchedule(
            id = scheduleId,
            medication_id = MED_ID,
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = createdAt,
        )
    }

    private companion object {
        const val PET_ID = "pet-dose-contract"
        const val MED_ID = "med-dose-contract"
        const val SCHEDULE_ID = "sched-dose-contract"
        const val DOSE_ID = "dose-contract-1"
    }
}
