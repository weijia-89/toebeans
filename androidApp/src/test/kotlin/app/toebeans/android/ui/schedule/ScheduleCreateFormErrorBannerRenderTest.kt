package app.toebeans.android.ui.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.MalformedScheduleException
import app.toebeans.core.scheduler.ScheduleCalculator
import app.toebeans.core.scheduler.ScheduledDose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0013 rendered-UI contract for B8: the form-level error banner on the schedule
 * create screen.
 *
 * The VM-level contract (calculator pre-flight failure populates [ScheduleCreateUiState
 * .formError], field mutation clears it) is pinned by [ScheduleCreatePreflightTest].
 * This class pins the rendered half: for a ui state with a non-empty formError the
 * banner renders and TalkBack's observable contract is the
 * `contentDescription = "Error: <message>"` node with a polite live region (the
 * Surface hides its subtree from semantics with `clearAndSetSemantics`, so the visible
 * Text itself is not a semantics node). The errorContainer tint is not machine-
 * checkable through semantics; the snapshot frameworks ADR-0013 rejected are the only
 * way to pin it, so the rendered contract pinned here is the announced node.
 *
 * The error message is produced by a test-local calculator fake that throws
 * [MalformedScheduleException.DuplicatePhaseOrder], whose mapped copy contains no
 * punctuation ambiguity for exact matching. SDK 33 and the plain-Application override
 * follow the [app.toebeans.android.ui.home.NotificationDenialBannerRenderTest] pattern
 * (ADR-0013 implementer notes); the ViewModel is injected directly so no Koin boots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ScheduleCreateFormErrorBannerRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `formError banner renders with the Error content description and polite live region`() {
        val vm = erroredViewModel()
        rule.setContent {
            MaterialTheme {
                ScheduleCreateScreen(
                    petId = PET_ID_B8_RENDER,
                    medicationId = MED_ID_B8_RENDER,
                    onBack = {},
                    onSaved = {},
                    viewModel = vm,
                )
            }
        }

        val banner =
            rule
                .onNodeWithContentDescription(
                    "Error: Two phases share the same position (1). Each phase needs its own slot.",
                ).assertIsDisplayed()
        assertEquals(
            "banner must be a polite live region so TalkBack announces it on appearance",
            LiveRegionMode.Polite,
            banner.fetchSemanticsNode().config[SemanticsProperties.LiveRegion],
        )
    }

    @Test
    fun `formError banner is absent when the form state has no error`() {
        val vm = cleanViewModel()
        rule.setContent {
            MaterialTheme {
                ScheduleCreateScreen(
                    petId = PET_ID_B8_RENDER,
                    medicationId = MED_ID_B8_RENDER,
                    onBack = {},
                    onSaved = {},
                    viewModel = vm,
                )
            }
        }

        rule
            .onNodeWithContentDescription(
                "Error: ",
                substring = true,
            ).assertDoesNotExist()
    }

    /**
     * VM with formError set by driving save() against a calculator fake that throws.
     * The pre-flight rejects before any persistence, so no repository is touched.
     */
    private fun erroredViewModel(): ScheduleCreateViewModel {
        val duplicateOrder =
            MalformedScheduleException.DuplicatePhaseOrder(phaseOrder = 1, phaseIds = listOf("a", "b"))
        val vm = baseViewModel(FakeCalculatorB8Render(throws = duplicateOrder))
        vm.setMedicationId(MED_ID_B8_RENDER)
        runBlocking { vm.save() }
        return vm
    }

    private fun cleanViewModel(): ScheduleCreateViewModel = baseViewModel(FakeCalculatorB8Render(throws = null))

    private fun baseViewModel(calculator: ScheduleCalculator): ScheduleCreateViewModel =
        ScheduleCreateViewModel(
            medicationRepository = InMemMedRepoB8Render(),
            scheduleRepository = InMemSchedRepoB8Render(),
            doseEventRepository = InMemDoseRepoB8Render(),
            scheduleCalculator = calculator,
            notificationActuator = RecordingNotificationActuatorB8Render(),
            timeZone = TimeZone.UTC,
        )
}

private const val PET_ID_B8_RENDER = "pet-luna"
private const val MED_ID_B8_RENDER = "med-amox"

private class FakeCalculatorB8Render(
    private val throws: MalformedScheduleException?,
) : ScheduleCalculator {
    override fun computeScheduledDoses(
        schedule: Schedule,
        phases: List<SchedulePhase>,
        timeZone: TimeZone,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<ScheduledDose> {
        throws?.let { throw it }
        return emptyList()
    }
}

private class InMemMedRepoB8Render : MedicationRepository {
    private val store = MutableStateFlow<Map<String, Medication>>(emptyMap())

    override fun observeAll(): Flow<List<Medication>> = store.asStateFlow().map { it.values.toList() }

    override fun observeForPet(petId: String): Flow<List<Medication>> =
        store.asStateFlow().map { snap -> snap.values.filter { it.petId == petId } }

    override suspend fun getById(id: String): Medication? = store.value[id]

    override suspend fun upsert(medication: Medication) {
        store.update { it + (medication.id to medication) }
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }
}

private class InMemSchedRepoB8Render : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, Schedule>>(emptyMap())
    private val phases = MutableStateFlow<Map<String, List<SchedulePhase>>>(emptyMap())

    override fun observeForMedication(medicationId: String): Flow<List<Schedule>> =
        schedules.asStateFlow().map { snap -> snap.values.filter { it.medicationId == medicationId } }

    override fun observeById(id: String): Flow<Schedule?> = schedules.asStateFlow().map { it[id] }

    override fun observePhases(scheduleId: String): Flow<List<SchedulePhase>> =
        phases.asStateFlow().map {
            it[scheduleId]
                ?: emptyList()
        }

    override fun observeActiveWithPhases(onOrAfter: LocalDate): Flow<List<ScheduleWithPhases>> =
        combine(schedules.asStateFlow(), phases.asStateFlow()) { scheds, phaseMap ->
            scheds.values
                .filter { (it.endDate == null || it.endDate!! >= onOrAfter) }
                .map { ScheduleWithPhases(it, phaseMap[it.id] ?: emptyList()) }
        }

    override fun observeAll(): Flow<List<Schedule>> = schedules.asStateFlow().map { it.values.toList() }

    override fun observeAllPhases(): Flow<List<SchedulePhase>> = phases.asStateFlow().map { it.values.flatten() }

    override suspend fun upsert(
        schedule: Schedule,
        phases: List<SchedulePhase>,
    ) {
        schedules.update { it + (schedule.id to schedule) }
        this.phases.update { it + (schedule.id to phases) }
    }

    override suspend fun delete(id: String) {
        schedules.update { it - id }
        phases.update { it - id }
    }
}

private class InMemDoseRepoB8Render : DoseEventRepository {
    private val store = mutableListOf<DoseEvent>()

    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> = MutableStateFlow(store.filter { it.scheduledAt >= sinceInclusive })

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
        MutableStateFlow(
            store
                .filter { it.medicationId == medicationId && it.status == DoseStatus.GIVEN }
                .maxByOrNull { it.scheduledAt },
        )

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
        MutableStateFlow(store.filter { it.status == DoseStatus.GIVEN && it.scheduledAt >= sinceInclusive })

    override fun observeRecentForHistory(
        sinceInclusive: Instant,
        limit: Int,
    ): Flow<List<DoseEvent>> =
        MutableStateFlow(
            store
                .asSequence()
                .filter { it.scheduledAt >= sinceInclusive }
                .sortedByDescending { it.scheduledAt }
                .take(limit)
                .toList(),
        )

    override fun observeAll(): Flow<List<DoseEvent>> = MutableStateFlow(store.toList())

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent = error("unused in ScheduleCreateFormErrorBannerRenderTest")

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent = error("unused in ScheduleCreateFormErrorBannerRenderTest")

    override suspend fun delete(doseEventId: String) {
        store.removeAll { it.id == doseEventId }
    }

    override suspend fun upsert(event: DoseEvent) {
        store.removeAll { it.id == event.id }
        store.add(event)
    }

    override suspend fun markStalePendingAsMissed(cutoff: Instant): Int = 0
}

private class RecordingNotificationActuatorB8Render : NotificationActuator {
    override fun schedule(reminder: ScheduledReminder) = Unit

    override fun cancel(reminderId: String) = Unit

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}
