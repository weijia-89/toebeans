package app.toebeans.core.data

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseUnit
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
    fun `recordGivenForSlot upgrades pending row at slot without duplicating`() =
        runTest {
            val slot = Instant.parse("2026-05-24T09:00:00Z")
            val resolved = Instant.parse("2026-05-24T09:05:00Z")
            repo.upsert(
                DoseEvent(
                    id = "dose-pending-slot",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = slot,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )

            val given =
                repo.recordGivenForSlot(
                    doseEventId = "ignored-random-id",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = slot,
                    resolvedAt = resolved,
                )

            assertEquals("dose-pending-slot", given.id, "must reuse materialized pending id")
            assertEquals(DoseStatus.GIVEN, given.status)
            assertEquals(resolved, given.resolvedAt)
            assertEquals(1, repo.observeAll().first().size, "slot upgrade must not fork a second row")
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

    @Test
    fun `markStalePendingAsMissed one pending dose inside timeout stays pending`() =
        runTest {
            // Scheduled 2 hours ago, cutoff is 4 hours ago → still within timeout, stays pending
            val scheduled = Instant.parse("2026-05-24T06:00:00Z") // 6 hours ago from reference
            val cutoff = Instant.parse("2026-05-24T02:00:00Z") // 2 hours after scheduled = 4h since ref

            repo.upsert(
                DoseEvent(
                    id = "stale-within-timeout",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = scheduled,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )

            val changed = repo.markStalePendingAsMissed(cutoff)
            assertEquals(0, changed, "pending inside timeout should not be marked missed")

            val all = repo.observeAll().first()
            val event = all.single { it.id == "stale-within-timeout" }
            assertEquals(DoseStatus.PENDING, event.status, "status unchanged")
        }

    @Test
    fun `markStalePendingAsMissed one pending dose past timeout becomes missed`() =
        runTest {
            // Scheduled 6 hours ago, cutoff is 2 hours ago → past timeout
            val scheduled = Instant.parse("2026-05-24T02:00:00Z") // 6 hours ago from reference
            val cutoff = Instant.parse("2026-05-24T06:00:00Z") // 6 hours since reference = 4h after scheduled

            repo.upsert(
                DoseEvent(
                    id = "stale-past-timeout",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = scheduled,
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )

            val changed = repo.markStalePendingAsMissed(cutoff)
            assertEquals(1, changed, "one row should be marked missed")

            val all = repo.observeAll().first()
            val event = all.single { it.id == "stale-past-timeout" }
            assertEquals(DoseStatus.MISSED, event.status, "should be missed")
            assertNotNull(event.resolvedAt, "resolvedAt should be set to cutoff")
        }

    @Test
    fun `markStalePendingAsMissed one already-given dose past timeout stays given`() =
        runTest {
            val scheduled = Instant.parse("2026-05-24T02:00:00Z")
            val resolved = Instant.parse("2026-05-24T02:30:00Z")
            val cutoff = Instant.parse("2026-05-24T06:00:00Z")

            repo.upsert(
                DoseEvent(
                    id = "given-past-timeout",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = scheduled,
                    firedAt = scheduled,
                    resolvedAt = resolved,
                    status = DoseStatus.GIVEN,
                    note = "already logged",
                ),
            )

            val changed = repo.markStalePendingAsMissed(cutoff)
            assertEquals(0, changed, "given dose should not be affected")

            val all = repo.observeAll().first()
            val event = all.single { it.id == "given-past-timeout" }
            assertEquals(DoseStatus.GIVEN, event.status, "status unchanged")
        }

    @Test
    fun `markStalePendingAsMissed mixed batch only stale pending transition`() =
        runTest {
            val reference = Instant.parse("2026-05-24T08:00:00Z") // current time reference
            val cutoff = Instant.parse("2026-05-24T04:00:00Z") // 4 hours before reference

            // Pending within timeout (scheduled at reference - 2h = 6h ago)
            repo.upsert(
                DoseEvent(
                    id = "pending-within",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = Instant.parse("2026-05-24T06:00:00Z"),
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )

            // Pending past timeout (scheduled at reference - 6h = 2h ago)
            repo.upsert(
                DoseEvent(
                    id = "pending-stale",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = Instant.parse("2026-05-24T02:00:00Z"),
                    firedAt = null,
                    resolvedAt = null,
                    status = DoseStatus.PENDING,
                    note = null,
                ),
            )

            // Skipped past timeout
            repo.upsert(
                DoseEvent(
                    id = "skipped-stale",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = Instant.parse("2026-05-24T02:00:00Z"),
                    firedAt = Instant.parse("2026-05-24T02:00:00Z"),
                    resolvedAt = Instant.parse("2026-05-24T02:05:00Z"),
                    status = DoseStatus.SKIPPED,
                    note = null,
                ),
            )

            // Given past timeout
            repo.upsert(
                DoseEvent(
                    id = "given-stale",
                    scheduleId = contractScheduleId,
                    medicationId = contractMedicationId,
                    scheduledAt = Instant.parse("2026-05-24T02:00:00Z"),
                    firedAt = Instant.parse("2026-05-24T02:00:00Z"),
                    resolvedAt = Instant.parse("2026-05-24T02:30:00Z"),
                    status = DoseStatus.GIVEN,
                    note = null,
                ),
            )

            val changed = repo.markStalePendingAsMissed(cutoff)
            assertEquals(1, changed, "only one stale pending should transition")

            val all = repo.observeAll().first()
            assertEquals(4, all.size)

            assertEquals(DoseStatus.PENDING, all.single { it.id == "pending-within" }.status)
            assertEquals(DoseStatus.MISSED, all.single { it.id == "pending-stale" }.status)
            assertEquals(DoseStatus.SKIPPED, all.single { it.id == "skipped-stale" }.status)
            assertEquals(DoseStatus.GIVEN, all.single { it.id == "given-stale" }.status)
        }
}
