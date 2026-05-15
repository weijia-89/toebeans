package app.toebeans.android.ui.schedule

import androidx.lifecycle.ViewModel
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Schedule-create form state. Holds a mutable list of phase drafts. On save, the drafts are
 * validated and converted to immutable [SchedulePhase] rows.
 *
 * Why a separate draft type ([PhaseDraft]) rather than mutating [SchedulePhase] directly:
 * SchedulePhase has an `init` block that rejects invalid combinations. While the user is
 * editing, intermediate values WILL be invalid (e.g. empty dose-time list, durationDays = 0).
 * The draft type permits invalid intermediate states; conversion to SchedulePhase happens
 * only on submit, where validation messages are surfaced to the user.
 */
@OptIn(ExperimentalUuidApi::class)
public class ScheduleCreateViewModel(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            ScheduleCreateUiState(
                startDate =
                    Clock.System
                        .now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date,
                phases = listOf(blankPhaseDraft()),
            ),
        )
    public val state: StateFlow<ScheduleCreateUiState> = _state.asStateFlow()

    public fun setMedicationId(medicationId: String) {
        _state.update { it.copy(medicationId = medicationId) }
    }

    public suspend fun loadMedication() {
        val id = _state.value.medicationId ?: return
        _state.update { it.copy(medication = medicationRepository.getById(id)) }
    }

    public fun onStartDateChange(value: LocalDate?) {
        _state.update { it.copy(startDate = value, startDateError = null) }
    }

    public fun onEndDateChange(value: LocalDate?) {
        _state.update { it.copy(endDate = value) }
    }

    public fun addPhase() {
        _state.update { it.copy(phases = it.phases + blankPhaseDraft()) }
    }

    public fun removePhase(index: Int) {
        _state.update { state ->
            if (state.phases.size <= 1) state else state.copy(phases = state.phases.filterIndexed { i, _ -> i != index })
        }
    }

    public fun updatePhase(
        index: Int,
        transform: (PhaseDraft) -> PhaseDraft,
    ) {
        _state.update { state ->
            state.copy(
                phases =
                    state.phases.mapIndexed { i, phase ->
                        if (i == index) transform(phase) else phase
                    },
            )
        }
    }

    /**
     * Convert the form state to a [Schedule] + [SchedulePhase] list and persist.
     * Returns the created Schedule.id on success, or null if validation failed.
     */
    public suspend fun save(): String? {
        val s = _state.value
        val medId = s.medicationId ?: return null

        if (s.startDate == null) {
            _state.update { it.copy(startDateError = "Required") }
            return null
        }

        // Validate phases. The first error per phase is surfaced.
        val phasesWithErrors =
            s.phases.mapIndexed { idx, draft ->
                validatePhase(draft, idx)
            }
        if (phasesWithErrors.any { it.second != null }) {
            _state.update {
                it.copy(
                    phases =
                        phasesWithErrors.map { (draft, err) ->
                            if (err != null) draft.copy(error = err) else draft
                        },
                )
            }
            return null
        }

        val scheduleId = "sched-${Uuid.random()}"
        val phases =
            s.phases.mapIndexed { idx, draft ->
                SchedulePhase(
                    id = "phase-${Uuid.random()}",
                    scheduleId = scheduleId,
                    phaseOrder = idx,
                    durationDays = draft.durationDaysText.toInt(),
                    dosesPerDay = draft.doseTimes.size,
                    doseTimesLocal = draft.doseTimes.sorted(),
                    doseAmount = draft.doseAmount.trim().ifEmpty { null },
                    dayInterval = draft.dayIntervalText.toIntOrNull() ?: 1,
                )
            }
        val schedule =
            Schedule(
                id = scheduleId,
                medicationId = medId,
                startDate = s.startDate,
                endDate = s.endDate,
                createdAt = Clock.System.now(),
            )
        scheduleRepository.upsert(schedule, phases)
        return scheduleId
    }

    private fun validatePhase(
        draft: PhaseDraft,
        index: Int,
    ): Pair<PhaseDraft, String?> {
        val durationOk = draft.durationDaysText.toIntOrNull()?.let { it in 1..SchedulePhase.MAX_DURATION_DAYS } == true
        if (!durationOk) {
            return draft to "Phase ${index + 1}: duration must be 1..${SchedulePhase.MAX_DURATION_DAYS} days"
        }
        if (draft.doseTimes.isEmpty()) {
            return draft to "Phase ${index + 1}: add at least one dose time"
        }
        if (draft.doseTimes.size > SchedulePhase.MAX_DOSES_PER_DAY) {
            return draft to "Phase ${index + 1}: max ${SchedulePhase.MAX_DOSES_PER_DAY} doses per day"
        }
        val distinct = draft.doseTimes.toSet()
        if (distinct.size != draft.doseTimes.size) {
            return draft to "Phase ${index + 1}: dose times must be unique"
        }
        val intervalOk = draft.dayIntervalText.toIntOrNull()?.let { it in 1..SchedulePhase.MAX_DAY_INTERVAL } == true
        if (!intervalOk) {
            return draft to "Phase ${index + 1}: skip-day interval must be 1..${SchedulePhase.MAX_DAY_INTERVAL}"
        }
        return draft to null
    }

    private fun blankPhaseDraft(): PhaseDraft =
        PhaseDraft(
            durationDaysText = "7",
            doseTimes = listOf(LocalTime(8, 0)),
            dayIntervalText = "1",
            doseAmount = "",
            error = null,
        )
}

public data class ScheduleCreateUiState(
    public val medicationId: String? = null,
    public val medication: Medication? = null,
    public val startDate: LocalDate?,
    public val endDate: LocalDate? = null,
    public val phases: List<PhaseDraft>,
    public val startDateError: String? = null,
)

public data class PhaseDraft(
    public val durationDaysText: String,
    public val doseTimes: List<LocalTime>,
    public val dayIntervalText: String,
    public val doseAmount: String,
    public val error: String?,
)
