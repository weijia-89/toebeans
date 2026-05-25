package app.toebeans.core.data

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Abstract test-as-spec for [DoseEventRepository]. SqlDelight subclass turns GREEN;
 * aligns with [MedicationRepositoryContract] / [ScheduleRepositoryContract] shape.
 */
abstract class DoseEventRepositoryContract : MedicalRepositoryContract() {
    protected abstract fun createRepository(): DoseEventRepository

    protected abstract val contractScheduleId: String
    protected abstract val contractMedicationId: String
    protected abstract val contractDoseId: String

    private lateinit var repo: DoseEventRepository

    @BeforeTest
    fun setupRepository() {
        repo = createRepository()
    }

    @Test
    fun `recordGivenNow round-trips via observeLastGivenForMedication`() =
        runTest {
            val at = Instant.parse("2026-05-24T12:00:00Z")
            repo.recordGivenNow(
                doseEventId = contractDoseId,
                scheduleId = contractScheduleId,
                medicationId = contractMedicationId,
                at = at,
            )

            val last = repo.observeLastGivenForMedication(contractMedicationId).first()
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
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = slot,
                    resolvedAt = firstResolved,
                )
            val second =
                repo.recordGivenForSlot(
                    doseEventId = "dose-slot-b",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
            val pendingAt = Instant.parse("2026-05-24T07:00:00Z")
            val givenAt = Instant.parse("2026-05-24T08:00:00Z")
            val resolvedAt = Instant.parse("2026-05-24T08:05:00Z")

            repo.upsert(
                DoseEvent(
                    id = "dose-pending-import",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                    id = contractDoseId,
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                    id = contractDoseId,
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
            val since = Instant.parse("2026-05-24T00:00:00Z")
            val givenBeforeSince = Instant.parse("2026-05-23T20:00:00Z")
            val pendingAfterSince = Instant.parse("2026-05-24T07:00:00Z")
            val givenMorning = Instant.parse("2026-05-24T08:00:00Z")
            val givenNoon = Instant.parse("2026-05-24T12:00:00Z")

            repo.upsert(
                DoseEvent(
                    id = "dose-given-before-since",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
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
                "DESC by scheduledAt",
            )

            repeat(51) { index ->
                val at = Instant.parse("2026-05-24T13:${index.toString().padStart(2, '0')}:00Z")
                repo.upsert(
                    DoseEvent(
                        id = "dose-limit-$index",
                        scheduleId = contractScheduleId,
                        medicationId = contractMedicationId,
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
    fun `delete clears last given for medication`() =
        runTest {
            val at = Instant.parse("2026-05-24T10:00:00Z")
            repo.recordGivenNow(
                doseEventId = contractDoseId,
                scheduleId = contractScheduleId,
                medicationId = contractMedicationId,
                at = at,
            )
            repo.delete(contractDoseId)

            assertNull(repo.observeLastGivenForMedication(contractMedicationId).first())
        }
}
