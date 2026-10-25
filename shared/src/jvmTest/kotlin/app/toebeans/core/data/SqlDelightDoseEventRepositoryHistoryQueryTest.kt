package app.toebeans.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test-as-spec for the scoped history query (commit 1: RED by design).
 *
 * Pins the contract the History tab needs: [DoseEventRepository.observeRecentForHistory]
 * must return EVERY status (pending/given/skipped/missed) whose scheduledAt is on or
 * after the window floor, newest first, truncated to the requested limit keeping the
 * newest rows.
 *
 * In commit 1 the SQLDelight implementation delegates to the GIVEN-only
 * `selectAllGivenSince` mapping (the documented limitation), so the
 * pending/skipped/missed assertions fail red. Commit 2 wires the real
 * `selectRecentForHistory` query and turns this green.
 *
 * VIBE-DANGEROUS surface per AGENTS.md: human review of this file gates commit 2.
 */
class SqlDelightDoseEventRepositoryHistoryQueryTest {
    private lateinit var database: ToebeansDatabase
    private lateinit var driver: SqlDriver
    private lateinit var repo: SqlDelightDoseEventRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        database = ToebeansDatabase(driver)
        seedParentChain()
        repo =
            SqlDelightDoseEventRepository(
                database = database,
                dispatcher = Dispatchers.Unconfined,
            )
    }

    @Test
    fun `all statuses inside window are returned newest first`() =
        runTest {
            upsertEvent("hist-given-0800", "2026-05-24T08:00:00Z", DoseStatus.GIVEN)
            upsertEvent("hist-pending-0900", "2026-05-24T09:00:00Z", DoseStatus.PENDING)
            upsertEvent("hist-skipped-1000", "2026-05-24T10:00:00Z", DoseStatus.SKIPPED)
            upsertEvent("hist-missed-1100", "2026-05-24T11:00:00Z", DoseStatus.MISSED)

            val rows =
                repo
                    .observeRecentForHistory(
                        sinceInclusive = Instant.parse("2026-05-24T00:00:00Z"),
                        limit = 10,
                    ).first()

            assertEquals(
                listOf("hist-missed-1100", "hist-skipped-1000", "hist-pending-0900", "hist-given-0800"),
                rows.map { it.id },
                "every status inside the window must be returned, DESC by scheduledAt",
            )
            assertEquals(
                listOf(DoseStatus.MISSED, DoseStatus.SKIPPED, DoseStatus.PENDING, DoseStatus.GIVEN),
                rows.map { it.status },
            )
        }

    @Test
    fun `row below the window floor is excluded`() =
        runTest {
            upsertEvent("hist-below-floor", "2026-05-24T09:00:00Z", DoseStatus.GIVEN)
            upsertEvent("hist-inside-window", "2026-05-24T11:00:00Z", DoseStatus.GIVEN)

            val rows =
                repo
                    .observeRecentForHistory(
                        sinceInclusive = Instant.parse("2026-05-24T10:00:00Z"),
                        limit = 10,
                    ).first()

            assertEquals(listOf("hist-inside-window"), rows.map { it.id })
            assertTrue(
                rows.none { it.id == "hist-below-floor" },
                "a row with scheduledAt before the window floor must not appear",
            )
        }

    @Test
    fun `limit truncates keeping the newest`() =
        runTest {
            upsertEvent("hist-given-0800", "2026-05-24T08:00:00Z", DoseStatus.GIVEN)
            upsertEvent("hist-pending-0900", "2026-05-24T09:00:00Z", DoseStatus.PENDING)
            upsertEvent("hist-missed-1000", "2026-05-24T10:00:00Z", DoseStatus.MISSED)
            upsertEvent("hist-skipped-1100", "2026-05-24T11:00:00Z", DoseStatus.SKIPPED)
            upsertEvent("hist-given-1200", "2026-05-24T12:00:00Z", DoseStatus.GIVEN)

            val rows =
                repo
                    .observeRecentForHistory(
                        sinceInclusive = Instant.parse("2026-05-24T00:00:00Z"),
                        limit = 3,
                    ).first()

            assertEquals(3, rows.size, "LIMIT must truncate the window")
            assertEquals(
                listOf("hist-given-1200", "hist-skipped-1100", "hist-missed-1000"),
                rows.map { it.id },
                "truncation must keep the NEWEST rows, regardless of status",
            )
        }

    @Test
    fun `empty window returns empty`() =
        runTest {
            upsertEvent("hist-below-any-floor", "2026-05-24T08:00:00Z", DoseStatus.GIVEN)

            val rows =
                repo
                    .observeRecentForHistory(
                        sinceInclusive = Instant.parse("2027-01-01T00:00:00Z"),
                        limit = 10,
                    ).first()

            assertTrue(rows.isEmpty(), "a window floor above every row must return an empty list")
        }

    private suspend fun upsertEvent(
        id: String,
        scheduledAt: String,
        status: DoseStatus,
    ) {
        val at = Instant.parse(scheduledAt)
        repo.upsert(
            DoseEvent(
                id = id,
                scheduleId = SCHEDULE_ID,
                medicationId = MED_ID,
                scheduledAt = at,
                firedAt = null,
                resolvedAt = null,
                status = status,
                note = null,
            ),
        )
    }

    private fun seedParentChain() {
        val createdAt = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
        database.petQueries.insertPet(
            id = PET_ID,
            name = "History Pet",
            species = "dog",
            birthdate_iso = null,
            weight_kg = 10.0,
            notes = null,
            created_at = createdAt,
            archived_at = null,
        )
        database.medicationQueries.insertMedication(
            id = MED_ID,
            pet_id = PET_ID,
            name = "History Med",
            dose_amount = "5mg",
            dose_unit = "MG",
            notes = null,
            created_at = createdAt,
            discontinued_at = null,
        )
        database.scheduleQueries.insertSchedule(
            id = SCHEDULE_ID,
            medication_id = MED_ID,
            start_date_iso = "2026-05-01",
            end_date_iso = null,
            created_at = createdAt,
            anchor_mode = "FOLLOW_PHONE",
        )
    }

    private companion object {
        const val PET_ID = "pet-history-query"
        const val MED_ID = "med-history-query"
        const val SCHEDULE_ID = "sched-history-query"
    }
}
