package app.toebeans.android.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.model.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Add or edit a [Medication] scoped to a pet. The medication's [petId] is fixed at construction
 * via [setPetId] (passed from the route argument) so the user cannot reassign a medication to
 * another pet from this form. Cross-pet moves would happen as delete+create.
 */
@OptIn(ExperimentalUuidApi::class)
public class MedicationEditViewModel(
    private val medicationRepository: MedicationRepository,
    private val petRepository: PetRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MedicationEditUiState())
    public val state: StateFlow<MedicationEditUiState> = _state.asStateFlow()

    public fun setPetId(petId: String) {
        _state.update { it.copy(petId = petId) }
        refreshReferenceContext()
    }

    /**
     * Single entry point from [MedicationEditScreen]'s route args. Clears stale form state
     * before async load so a shared Koin-scoped VM does not flash the previous medication
     * (Today Edit) or keep an old [MedicationEditUiState.medicationId] in add mode
     * (Reminders FAB → Add medication).
     */
    public fun prepareRoute(
        petId: String,
        medicationId: String?,
    ) {
        _state.update { it.copy(petId = petId) }
        if (medicationId == null) {
            _state.update {
                MedicationEditUiState(
                    petId = petId,
                    medicationId = null,
                    name = "",
                    doseAmount = "",
                    notes = "",
                    nameError = null,
                    doseAmountError = null,
                    discontinuedAt = null,
                    petName = null,
                    scheduleHint = null,
                )
            }
            refreshReferenceContext()
            return
        }
        load(medicationId)
    }

    public fun load(medicationId: String) {
        viewModelScope.launch {
            // Clear stale form fields before the async fetch. The screen shares one
            // Koin-scoped VM across navigations; without this, a Today med tap briefly
            // shows the previous medication until load() returns.
            _state.update {
                it.copy(
                    medicationId = medicationId,
                    name = "",
                    doseAmount = "",
                    notes = "",
                    nameError = null,
                    doseAmountError = null,
                    discontinuedAt = null,
                    petName = null,
                    scheduleHint = null,
                )
            }
            val med = medicationRepository.getById(medicationId) ?: return@launch
            _state.update {
                it.copy(
                    medicationId = med.id,
                    petId = med.petId,
                    name = med.name,
                    doseAmount = med.doseAmount,
                    notes = med.notes.orEmpty(),
                    discontinuedAt = med.discontinuedAt,
                )
            }
            refreshReferenceContext()
        }
    }

    /**
     * Mark this medication as discontinued. Soft-delete: preserves the medication row and
     * any dose history that references it, but the medication stops appearing in active
     * surfaces (Home's "due today" filter at `HomeViewModel.kt:195` already excludes
     * discontinued meds via `Medication.isActive`; the active-pet medications list in
     * `PetsViewModel.kt:24` does the same). Reversible via [reactivate].
     *
     * **What this does NOT do:** the Schedule rows that reference this medication remain in
     * the repository, and their existing AlarmManager registrations are NOT cancelled. v0.1
     * relies on Home's discontinuedAt filter to keep reminders out of view; if a phase is
     * still actively materializing, its alarms would still fire and [DoseAlarmReceiver]
     * resolves the dose via [app.toebeans.core.notifications.SqlDelightReminderLookup]
     * (M1.3). That lookup does not yet filter on [Medication.isActive], so discontinued-med
     * firings can still render until a follow-on slice joins the medication row at fire time.
     *
     * @param now Instant to record as discontinuedAt. Injectable so tests can pin time.
     *     Default `Clock.System.now()` for production callers.
     * @return true if discontinue persisted; false in new-medication mode (nothing to
     *     discontinue) or if the medication has been deleted from the repository between
     *     load() and this call.
     */
    public suspend fun discontinue(now: Instant = Clock.System.now()): Boolean {
        val id = _state.value.medicationId ?: return false
        val existing = medicationRepository.getById(id) ?: return false
        medicationRepository.upsert(existing.copy(discontinuedAt = now))
        _state.update { it.copy(discontinuedAt = now) }
        return true
    }

    /**
     * Reverse a previous [discontinue]. Clears `discontinuedAt` back to `null` so the
     * medication reappears in active surfaces. Idempotent on an already-active medication
     * (the upsert is a no-op but still returns true because the call ran).
     *
     * @return true if reactivate persisted; false only in new-medication mode or if the
     *     medication has been deleted between load() and this call.
     */
    public suspend fun reactivate(): Boolean {
        val id = _state.value.medicationId ?: return false
        val existing = medicationRepository.getById(id) ?: return false
        medicationRepository.upsert(existing.copy(discontinuedAt = null))
        _state.update { it.copy(discontinuedAt = null) }
        return true
    }

    /**
     * Loads read-only pet + schedule context for the header card. Runs after [setPetId]
     * and [load] so every entry path (Today Edit, pet detail, deep link) shows who and
     * what the user is editing without duplicating nav args for petName.
     */
    private fun refreshReferenceContext() {
        viewModelScope.launch {
            val snapshot = _state.value
            val petId = snapshot.petId ?: return@launch
            val petName = petRepository.getById(petId)?.name
            val scheduleHint =
                snapshot.medicationId?.let { medicationId ->
                    val schedules = scheduleRepository.observeForMedication(medicationId).first()
                    val phases =
                        schedules
                            .minByOrNull { it.startDate }
                            ?.let { schedule ->
                                scheduleRepository.observePhases(schedule.id).first()
                            }.orEmpty()
                    buildMedicationEditScheduleHint(schedules, phases)
                }
            _state.update {
                it.copy(
                    petName = petName,
                    scheduleHint = scheduleHint,
                )
            }
        }
    }

    public fun onNameChange(value: String) {
        _state.update { it.copy(name = value, nameError = null) }
    }

    public fun onDoseAmountChange(value: String) {
        _state.update { it.copy(doseAmount = value, doseAmountError = null) }
    }

    public fun onNotesChange(value: String) {
        _state.update { it.copy(notes = value) }
    }

    /**
     * Delete the loaded medication. Returns true if a delete was issued, false in new-med
     * mode (nothing to delete).
     *
     * v0.1: fake repo hard-removes from its in-memory map. M1: SQLDelight will soft-delete
     * by setting [Medication.discontinuedAt] (schema column already present) so historical
     * dose-events keep a name to display. Until then, the in-memory fake leaves orphans;
     * Home's `computeDueToday` already filters out viable bundles where the med is missing,
     * so the worst-case visible effect is a row dropping from "Logged today", which is
     * acceptable v0.1 behavior given the absence of persistence.
     */
    public suspend fun delete(): Boolean {
        val id = _state.value.medicationId ?: return false
        medicationRepository.delete(id)
        return true
    }

    public suspend fun save(): Boolean {
        val s = _state.value
        check(s.petId != null) { "petId must be set before save()" }
        var valid = true
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Required") }
            valid = false
        }
        if (s.doseAmount.isBlank()) {
            _state.update { it.copy(doseAmountError = "Required (e.g. \"10 mg\")") }
            valid = false
        }
        if (!valid) return false
        // Preserve createdAt and (critically) discontinuedAt from the persisted record on
        // edit. Without this, editing a discontinued medication silently un-discontinues it,
        // which could resume dose reminders for a medication the caregiver had stopped.
        val existing = s.medicationId?.let { medicationRepository.getById(it) }
        val med =
            Medication(
                id = s.medicationId ?: "med-${Uuid.random()}",
                petId = s.petId,
                name = s.name.trim(),
                doseAmount = s.doseAmount.trim(),
                notes = s.notes.trim().ifEmpty { null },
                createdAt = existing?.createdAt ?: Clock.System.now(),
                discontinuedAt = existing?.discontinuedAt,
            )
        medicationRepository.upsert(med)
        return true
    }
}

public data class MedicationEditUiState(
    public val medicationId: String? = null,
    public val petId: String? = null,
    /** Resolved from [PetRepository] for the read-only context card. */
    public val petName: String? = null,
    /**
     * Schedule date range + dose-time hint for edit mode; `null` in new-medication mode
     * until the user saves and creates a schedule.
     */
    public val scheduleHint: String? = null,
    public val name: String = "",
    public val doseAmount: String = "",
    public val notes: String = "",
    public val nameError: String? = null,
    public val doseAmountError: String? = null,
    /**
     * `null` for active medications. Non-null timestamp for discontinued medications.
     * Set by [MedicationEditViewModel.discontinue] / [MedicationEditViewModel.reactivate]
     * and seeded from the persisted record on [MedicationEditViewModel.load]. The UI
     * surfaces this as a "Discontinued on ..." banner and flips the topBar action
     * between Discontinue and Reactivate.
     */
    public val discontinuedAt: Instant? = null,
) {
    public val isNew: Boolean get() = medicationId == null

    /** True if this medication has been soft-deleted via discontinue. */
    public val isDiscontinued: Boolean get() = discontinuedAt != null
}
