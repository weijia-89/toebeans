package app.toebeans.core.data

import app.cash.turbine.test
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Abstract test-as-spec for the [ScheduleRepository] interface contract.
 *
 * Any [ScheduleRepository] implementation MUST satisfy every test in this class. Concrete
 * subclasses provide a freshly-isolated [ScheduleRepository] via [createRepository] (called
 * once per test via [BeforeTest]).
 *
 * Phase 5 (this PR, M1 Decision 4a) ships this contract plus [StubScheduleRepositoryContractTest],
 * which exercises a stub-throws factory. Every test fails RED on first run, per AGENTS.md
 * § Test-as-spec rules: the human reviewer sees a failing test runner output, not just a
 * source diff. RED is the deliverable for this PR.
 *
 * Phase 6 (the SqlDelight-backed [ScheduleRepository] implementation PR, also vibe-dangerous)
 * ships a sibling subclass (e.g. `SqlDelightScheduleRepositoryContractTest`) whose factory
 * returns a real implementation. Those tests turn green and from then on this contract is
 * the regression gate.
 *
 * ## Inheritance
 *
 * Extends [MedicalRepositoryContract] to inherit the [obtainDriver] / [configureDb] hook
 * pair. The Phase-6 SqlDelight subclass overrides [obtainDriver] to return the real driver
 * and inherits the default [configureDb] override pattern documented in
 * [MedicalRepositoryContract] (i.e. `driver.execute(null, "PRAGMA foreign_keys=ON", 0)` per
 * ADR-0010). The stub subclass in this PR returns null, so no FK setup runs.
 *
 * ## Why phaseOrder denseness is caller-enforced (case 6)
 *
 * Per [ScheduleRepository.upsert]'s KDoc, the phases passed to upsert MUST form a dense
 * 0..N-1 phaseOrder sequence. Individual [SchedulePhase] init blocks only enforce
 * `phaseOrder >= 0`; they cannot see sibling phases. Repository-level upsert is therefore
 * the gate that catches inconsistent sets (gaps, duplicates). Case 6 pins this gate by
 * passing a gapped sequence and asserting [IllegalArgumentException].
 *
 * ## Cascade case (case 11)
 *
 * Per ADR-0010 (sqlite-foreign-keys), [Schedule.medicationId] is `REFERENCES Medication(id)
 * ON DELETE CASCADE`, and [SchedulePhase.scheduleId] is `REFERENCES Schedule(id) ON DELETE
 * CASCADE`. Deleting a parent Medication therefore removes its Schedules AND their
 * SchedulePhases in a single transaction. The contract pins this end-to-end cascade.
 *
 * The [ScheduleRepository] interface has no `delete medication` method (that lives on
 * [MedicationRepository]). To exercise the cascade, this contract declares an abstract
 * [deleteParentMedication] hook. The Phase-6 SqlDelight subclass implements it by deleting
 * directly through the medication table; subclasses without a real FK driver (the stub
 * here, and future fake-based subclasses) throw [UnsupportedOperationException], which
 * still leaves the test RED until the SqlDelight subclass lands.
 *
 * ## Active-window semantics (cases 9 and 10)
 *
 * Per [ScheduleRepository.observeActiveWithPhases]'s KDoc, a schedule is active for
 * `onOrAfter` iff `endDate == null OR endDate >= onOrAfter`. The boundary therefore
 * INCLUDES schedules whose endDate equals `onOrAfter` (case 10) and EXCLUDES schedules
 * whose endDate is strictly before (case 9). A schedule that ended yesterday is not on
 * today's worklist; a schedule that ends today still is.
 */
abstract class ScheduleRepositoryContract : MedicalRepositoryContract() {
    /**
     * Concrete subclasses return a freshly-isolated [ScheduleRepository] (empty initial state,
     * no leakage between tests). Called once per test via [BeforeTest].
     */
    protected abstract fun createRepository(): ScheduleRepository

    /**
     * Concrete subclasses delete the medication row identified by [medicationId] directly,
     * outside the [ScheduleRepository] surface, so case 11 can assert the FK CASCADE down to
     * Schedule and SchedulePhase rows. The Phase-6 SqlDelight subclass implements this via
     * raw driver execute (or via a co-injected [MedicationRepository]). Subclasses without a
     * real FK-enforcing driver throw [UnsupportedOperationException], which keeps case 11
     * RED until a real driver is wired.
     */
    protected abstract suspend fun deleteParentMedication(medicationId: String)

    private lateinit var repo: ScheduleRepository

    @BeforeTest
    fun setupRepository() {
        repo = createRepository()
    }

    @Test
    fun `observeForMedication emits empty list when no schedules exist for that medication`() =
        runTest {
            val initial = repo.observeForMedication("m1").first()
            assertEquals(
                emptyList(),
                initial,
                "fresh repo must emit empty list for any medicationId",
            )
        }

    @Test
    fun `upsert then observeForMedication round-trips schedule plus phases`() =
        runTest {
            val sched = schedule("s1", "m1")
            val phases = listOf(phase("s1", 0))
            repo.upsert(sched, phases)

            val emitted = repo.observeForMedication("m1").first()
            assertEquals(
                listOf(sched),
                emitted,
                "observeForMedication must emit the upserted schedule",
            )

            val emittedPhases = repo.observePhases("s1").first()
            assertEquals(
                phases,
                emittedPhases,
                "observePhases must emit the upserted phases for the schedule",
            )
        }

    @Test
    fun `observeById emits null for unknown id, then current after upsert, then null after delete`() =
        runTest {
            repo.observeById("s1").test {
                assertNull(awaitItem(), "first emission is null for absent id")
                val sched = schedule("s1", "m1")
                repo.upsert(sched, listOf(phase("s1", 0)))
                assertEquals(sched, awaitItem(), "emission after upsert is the new schedule")
                repo.delete("s1")
                assertNull(awaitItem(), "emission after delete is null")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observePhases emits in phaseOrder ascending regardless of upsert order`() =
        runTest {
            // Insertion order is deliberately not the expected emission order. The contract
            // pins observePhases as the ordering authority (sorted ascending by phaseOrder),
            // matching the KDoc on ScheduleRepository.observePhases.
            val phases = listOf(
                phase("s1", 2),
                phase("s1", 0),
                phase("s1", 1),
            )
            repo.upsert(schedule("s1", "m1"), phases)

            val emittedOrders = repo.observePhases("s1").first().map { it.phaseOrder }
            assertEquals(
                listOf(0, 1, 2),
                emittedOrders,
                "observePhases must order by phaseOrder ascending regardless of upsert order",
            )
        }

    @Test
    fun `upsert is idempotent on Schedule id (replace, not duplicate)`() =
        runTest {
            repo.upsert(schedule("s1", "m1"), listOf(phase("s1", 0)))
            val updated = schedule("s1", "m1", startDate = LocalDate(2026, 6, 1))
            repo.upsert(updated, listOf(phase("s1", 0)))

            val emitted = repo.observeForMedication("m1").first()
            assertEquals(1, emitted.size, "second upsert with same id must replace, not duplicate")
            assertEquals(
                LocalDate(2026, 6, 1),
                emitted.single().startDate,
                "the replaced row must carry the second upsert's startDate",
            )
        }

    @Test
    fun `upsert with inconsistent phaseOrder sequence throws IllegalArgumentException`() =
        runTest {
            // Individually valid (each phase has phaseOrder >= 0), collectively invalid
            // (the set [0, 2] is not dense 0..N-1; phase 1 is missing). Per ScheduleRepository.upsert
            // KDoc, the repository must reject this.
            val gapped = listOf(phase("s1", 0), phase("s1", 2))
            assertFailsWith<IllegalArgumentException>(
                "upsert must reject a non-dense phaseOrder sequence per the interface KDoc",
            ) {
                repo.upsert(schedule("s1", "m1"), gapped)
            }
        }

    @Test
    fun `delete removes schedule AND its phases (observePhases emits empty after delete)`() =
        runTest {
            repo.upsert(schedule("s1", "m1"), listOf(phase("s1", 0), phase("s1", 1)))
            repo.delete("s1")

            val schedulesAfter = repo.observeForMedication("m1").first()
            assertEquals(
                emptyList(),
                schedulesAfter,
                "observeForMedication must exclude the deleted schedule",
            )

            val phasesAfter = repo.observePhases("s1").first()
            assertEquals(
                emptyList(),
                phasesAfter,
                "observePhases must emit empty after the parent schedule is deleted",
            )
        }

    @Test
    fun `delete is idempotent on unknown id (no exception)`() =
        runTest {
            // Defensive contract: receivers may call delete on a schedule that has already
            // been removed in a race with a sibling UI delete. Throwing here would crash the
            // receiver process, which violates the medication-critical safety posture in
            // AGENTS.md § Operator wisdom. The test passes if no exception is thrown.
            repo.delete("never-existed")
        }

    @Test
    fun `observeActiveWithPhases excludes schedules whose endDate is strictly before onOrAfter`() =
        runTest {
            val expired = schedule(
                "s-expired",
                "m1",
                startDate = LocalDate(2026, 5, 1),
                endDate = LocalDate(2026, 5, 18),
            )
            repo.upsert(expired, listOf(phase("s-expired", 0)))

            val emitted = repo.observeActiveWithPhases(LocalDate(2026, 5, 19)).first()
            assertTrue(
                emitted.none { it.schedule.id == "s-expired" },
                "schedule with endDate strictly before onOrAfter must be excluded from active window",
            )
        }

    @Test
    fun `observeActiveWithPhases includes schedules with endDate equal to onOrAfter`() =
        runTest {
            val endsToday =
                schedule(
                    "s-today",
                    "m1",
                    startDate = LocalDate(2026, 5, 1),
                    endDate = LocalDate(2026, 5, 19),
                )
            repo.upsert(endsToday, listOf(phase("s-today", 0)))

            val emitted = repo.observeActiveWithPhases(LocalDate(2026, 5, 19)).first()
            assertTrue(
                emitted.any { it.schedule.id == "s-today" },
                "schedule with endDate equal to onOrAfter must be included (boundary case)",
            )
        }

    @Test
    fun `delete on parent Medication cascades to Schedule and SchedulePhase rows`() =
        runTest {
            repo.upsert(schedule("s1", "m1"), listOf(phase("s1", 0), phase("s1", 1)))
            repo.upsert(schedule("s2", "m1"), listOf(phase("s2", 0)))

            // The hook is implemented by Phase-6 SqlDelight subclasses via raw driver execute
            // (or a co-injected MedicationRepository) so the FK CASCADE actually fires. Stub
            // subclasses throw, which keeps the assertion below unreachable and the test RED
            // until Phase 6 wires a real FK-enforcing driver.
            deleteParentMedication("m1")

            val schedulesAfter = repo.observeForMedication("m1").first()
            assertEquals(
                emptyList(),
                schedulesAfter,
                "deleting parent Medication must CASCADE-delete its Schedules per ADR-0010",
            )

            val phasesAfterS1 = repo.observePhases("s1").first()
            assertEquals(
                emptyList(),
                phasesAfterS1,
                "deleting parent Medication must CASCADE-delete s1's SchedulePhases per ADR-0010",
            )

            val phasesAfterS2 = repo.observePhases("s2").first()
            assertEquals(
                emptyList(),
                phasesAfterS2,
                "deleting parent Medication must CASCADE-delete s2's SchedulePhases per ADR-0010",
            )
        }

    companion object {
        // Reference time anchored at 2026-05-19T00:00:00Z, matching PetRepositoryContract's pattern.
        // The createdAt value is not load-bearing for the contract; it just needs to be a valid
        // Instant that the test can construct deterministically.
        private val refCreatedAt: Instant = Instant.parse("2026-05-19T00:00:00Z")

        // Reference local date for schedule windows. Matches the Instant anchor in calendar
        // day so the two read as one logical "today" in the contract.
        private val refStartDate: LocalDate = LocalDate(2026, 5, 19)

        private fun schedule(
            id: String,
            medicationId: String,
            startDate: LocalDate = refStartDate,
            endDate: LocalDate? = null,
        ): Schedule =
            Schedule(
                id = id,
                medicationId = medicationId,
                startDate = startDate,
                endDate = endDate,
                createdAt = refCreatedAt,
            )

        private fun phase(
            scheduleId: String,
            order: Int,
            durationDays: Int = 7,
            dosesPerDay: Int = 1,
            doseTimesLocal: List<LocalTime> = listOf(LocalTime(8, 0)),
            doseAmount: String? = null,
            dayInterval: Int = 1,
        ): SchedulePhase =
            SchedulePhase(
                id = "$scheduleId-p$order",
                scheduleId = scheduleId,
                phaseOrder = order,
                durationDays = durationDays,
                dosesPerDay = dosesPerDay,
                doseTimesLocal = doseTimesLocal,
                doseAmount = doseAmount,
                dayInterval = dayInterval,
            )
    }
}
