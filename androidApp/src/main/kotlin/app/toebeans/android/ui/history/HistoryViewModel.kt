package app.toebeans.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.android.util.StaleEventGuard
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * History tab (ADR-0011 UI slice). Read-only, newest-first log of dose events across
 * every pet, bounded to the last 30 days.
 *
 * **Repository read.** The tab calls [DoseEventRepository.observeRecentForHistory]
 * with [windowStart] (`now` minus [WINDOW_DAYS]) and [HISTORY_ROW_LIMIT]. That query
 * returns every [DoseStatus] inside the window, newest first. The projection drops a
 * row whose scheduled time is still before the window start, and [applyPetFilter]
 * narrows the list to one pet.
 *
 * Like Home, the window is anchored once per flow subscription (rebuilt via
 * [flatMapLatest] off the pets flow), not rolled over mid-session.
 *
 * The projection lives on the companion as a pure function (same shape as
 * [app.toebeans.android.ui.home.HomeViewModel.joinToUiState]) so it is directly
 * unit-testable without a viewModelScope. Stale rows — a dose event whose medication or
 * pet no longer resolves — go through the [StaleEventGuard] contract: debug throws with
 * its diagnostic message, release logs and skips the row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class HistoryViewModel(
    petRepository: PetRepository,
    medicationRepository: MedicationRepository,
    doseEventRepository: DoseEventRepository,
) : ViewModel() {
    private val filterPetId = MutableStateFlow<String?>(null)

    public val uiState: StateFlow<HistoryUiState> =
        buildUiState(petRepository, medicationRepository, doseEventRepository)

    /** Keep rows for [petId]. A pet id that is not in the list clears the filter. */
    public fun selectPetFilter(petId: String) {
        filterPetId.value = petId
    }

    /** Show every pet's rows again. */
    public fun clearPetFilter() {
        filterPetId.value = null
    }

    private fun buildUiState(
        petRepository: PetRepository,
        medicationRepository: MedicationRepository,
        doseEventRepository: DoseEventRepository,
    ): StateFlow<HistoryUiState> {
        val pets = petRepository.observeAll()
        val medications = medicationRepository.observeAll()
        val recentDoses =
            pets.flatMapLatest { _ ->
                doseEventRepository.observeRecentForHistory(
                    sinceInclusive = historyWindowStart(),
                    limit = HISTORY_ROW_LIMIT,
                )
            }
        return combine(
            pets,
            medications,
            recentDoses,
            filterPetId,
        ) { petList, medList, events, activePetFilter ->
            applyPetFilter(
                projectHistory(
                    pets = petList,
                    medications = medList,
                    events = events,
                    since = historyWindowStart(),
                    zone = TimeZone.currentSystemDefault(),
                ),
                activePetFilter,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5_000L,
                    replayExpirationMillis = 0L,
                ),
            initialValue = HistoryUiState(loading = true),
        )
    }

    /** The instant that starts the 30-day window, computed at call time. */
    private fun historyWindowStart(): Instant = windowStart(Clock.System.now())

    public companion object {
        /** History window length in days (spec: "last 30 days", no load-more in v1). */
        public const val WINDOW_DAYS: Int = 30

        /** Newest rows kept from [DoseEventRepository.observeRecentForHistory]. */
        public const val HISTORY_ROW_LIMIT: Int = 500

        /**
         * Window start for "the last [WINDOW_DAYS] days" ending at [now]. Pure so tests
         * can pin the boundary exactly.
         */
        internal fun windowStart(now: Instant): Instant = now - WINDOW_DAYS.days

        /**
         * Pure join of pets, medications, and dose events into the History UI state.
         * Lives on the companion (not inside the VM) so it is directly unit-testable
         * without a viewModelScope, mirroring the Home screen's projection pattern.
         *
         * The window filter is applied here as well as at the repository call site:
         * the projection stays correct no matter which read feeds it, and tests can
         * exercise out-of-window rows without a repository.
         */
        internal fun projectHistory(
            pets: List<Pet>,
            medications: List<Medication>,
            events: List<DoseEvent>,
            since: Instant,
            zone: TimeZone,
        ): HistoryUiState {
            val petById = pets.associateBy { it.id }
            val medById = medications.associateBy { it.id }
            val rows =
                events
                    .filter { it.scheduledAt >= since }
                    .mapNotNull { event ->
                        val med =
                            medById[event.medicationId]
                                ?: return@mapNotNull StaleEventGuard.reportMissing(
                                    site = "HistoryViewModel.projectHistory",
                                    eventId = event.id,
                                    missingFieldName = "medicationId",
                                    missingValue = event.medicationId,
                                )
                        val pet =
                            petById[med.petId]
                                ?: return@mapNotNull StaleEventGuard.reportMissing(
                                    site = "HistoryViewModel.projectHistory",
                                    eventId = event.id,
                                    missingFieldName = "petId",
                                    missingValue = med.petId,
                                )
                        HistoryRowUi(
                            id = event.id,
                            petId = pet.id,
                            petName = pet.name,
                            medicationName = med.name,
                            scheduledAt = event.scheduledAt,
                            scheduledLabel = formatLocalDateTime(event.scheduledAt, zone),
                            statusLabel = statusLabel(event.status),
                            firedLabel = event.firedAt?.let { "alarm fired ${formatLocalTime(it, zone)}" },
                        )
                    }.sortedByDescending { it.scheduledAt }
            // Sort by the actual instant, not the display label, and re-sort here rather
            // than trusting the repository's DESC order: the projection stays correct
            // even if a future read path arrives unsorted.
            return HistoryUiState(rows = rows, pets = pets, loading = false)
        }

        /**
         * When [petId] is in [HistoryUiState.pets], keep only that pet's rows.
         * A null id, or an id the pet list does not contain, shows every row.
         */
        internal fun applyPetFilter(
            state: HistoryUiState,
            petId: String?,
        ): HistoryUiState {
            if (petId == null || state.pets.none { it.id == petId }) {
                return state.copy(filterPetId = null)
            }
            return state.copy(
                filterPetId = petId,
                rows = state.rows.filter { it.petId == petId },
            )
        }

        /** Human label for a [DoseStatus] chip. Never claims delivery, only state. */
        internal fun statusLabel(status: DoseStatus): String =
            when (status) {
                DoseStatus.PENDING -> "Pending"
                DoseStatus.GIVEN -> "Given"
                DoseStatus.SKIPPED -> "Skipped"
                DoseStatus.MISSED -> "Missed"
            }

        /**
         * Render an [Instant] as a local "Oct 24, 8:02 PM" label. Hand-rolled because
         * v1 is English-only (same rationale as Home's `formatTodayHeader`); no locale
         * plumbing, no extra dependency.
         */
        internal fun formatLocalDateTime(
            instant: Instant,
            timeZone: TimeZone,
        ): String {
            val date = instant.toLocalDateTime(timeZone).date
            val month =
                date.month.name
                    .lowercase()
                    .replaceFirstChar(Char::titlecase)
                    .take(3)
            return "$month ${date.dayOfMonth}, ${formatLocalTime(instant, timeZone)}"
        }

        /** Render an [Instant] as a local "8:02 PM" time label (Home pattern). */
        internal fun formatLocalTime(
            instant: Instant,
            timeZone: TimeZone,
        ): String {
            val ldt: LocalDateTime = instant.toLocalDateTime(timeZone)
            val hour24 = ldt.hour
            val hour12 = ((hour24 + 11) % 12) + 1
            val minute = ldt.minute.toString().padStart(2, '0')
            val suffix = if (hour24 < 12) "AM" else "PM"
            return "$hour12:$minute $suffix"
        }
    }
}

/**
 * One renderable History row. All display strings are precomputed by the projection so
 * the screen stays a dumb renderer and the copy is unit-testable. Read-only by shape:
 * there is no edit, delete, or re-log field here, and no callback type at all.
 */
public data class HistoryRowUi(
    public val id: String,
    public val petId: String,
    public val petName: String,
    public val medicationName: String,
    public val scheduledAt: Instant,
    public val scheduledLabel: String,
    public val statusLabel: String,
    /** "alarm fired 8:02 PM" when the alarm ran; null otherwise. Never claims delivery. */
    public val firedLabel: String?,
)

/** UI state for the History tab. Immutable snapshot. */
public data class HistoryUiState(
    public val rows: List<HistoryRowUi> = emptyList(),
    public val pets: List<Pet> = emptyList(),
    public val filterPetId: String? = null,
    public val loading: Boolean = false,
)
