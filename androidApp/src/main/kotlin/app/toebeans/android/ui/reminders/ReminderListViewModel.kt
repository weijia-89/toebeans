package app.toebeans.android.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.android.util.StaleEventGuard
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * ViewModel for the Reminders bottom-nav tab.
 *
 * Joins three repositories — pets, medications, and active schedules — into one flat
 * list of [ReminderRowUi] grouped by pet. "Active" here means the schedule's effective
 * date range covers today (per `ScheduleRepository.observeActiveWithPhases`).
 *
 * ## Why a separate screen
 *
 * The Today screen shows today's *scheduled doses* (concrete firings produced by the
 * calculator over a 24-hour window). The Reminders screen shows the *configured
 * schedules themselves* — the user's mental model of "what reminders has my cat got
 * set up?" rather than "what's due in the next 24h?".
 *
 * The two are deliberately complementary. Today is action-oriented (tap to log).
 * Reminders is management-oriented (see what's configured, eventually tap to edit/delete
 * via Schedule Detail — B7).
 *
 * ## Scope for v0.1
 *
 * Only ACTIVE schedules. Ended/upcoming filtering is a follow-up and would need a
 * different repo query (the existing `observeActiveWithPhases` is keyed on "active on
 * this date").
 *
 * ## Tap behavior
 *
 * `onScheduleClick` is a callback that today is a no-op (no Schedule Detail screen
 * yet). B7 will wire it through `navController.navigate(Destinations.scheduleDetail(...))`.
 */
public class ReminderListViewModel(
    petRepository: PetRepository,
    medicationRepository: MedicationRepository,
    scheduleRepository: ScheduleRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    public val state: StateFlow<ReminderListUiState> =
        combine(
            petRepository.observeAll(),
            medicationRepository.observeAll(),
            scheduleRepository.observeActiveWithPhases(clock.todayIn(timeZone)),
        ) { pets, meds, schedules ->
            joinToUiState(pets, meds, schedules, clock.todayIn(timeZone))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ReminderListUiState(rows = emptyList(), loading = true),
        )

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * Pure projection. Visible-for-test (`internal`) so the join logic can be
         * exercised without a [viewModelScope] rig.
         *
         * Stale-row hazards (a schedule referencing a missing medication or a medication
         * referencing a missing pet) are funneled through [StaleEventGuard], matching the
         * Home screen contract from Tier A #4. Debug builds throw; release builds log
         * and skip.
         */
        internal fun joinToUiState(
            pets: List<Pet>,
            meds: List<Medication>,
            schedules: List<ScheduleWithPhases>,
            today: LocalDate,
        ): ReminderListUiState {
            val petById = pets.associateBy { it.id }
            val medById = meds.associateBy { it.id }
            val rows =
                schedules.mapNotNull { swp ->
                    val med =
                        medById[swp.schedule.medicationId]
                            ?: return@mapNotNull StaleEventGuard.reportMissing(
                                site = "ReminderListViewModel.joinToUiState",
                                eventId = swp.schedule.id,
                                missingFieldName = "medicationId",
                                missingValue = swp.schedule.medicationId,
                            )
                    val pet =
                        petById[med.petId]
                            ?: return@mapNotNull StaleEventGuard.reportMissing(
                                site = "ReminderListViewModel.joinToUiState",
                                eventId = swp.schedule.id,
                                missingFieldName = "petId",
                                missingValue = med.petId,
                            )
                    ReminderRowUi(
                        scheduleId = swp.schedule.id,
                        petId = pet.id,
                        petName = pet.name,
                        medicationId = med.id,
                        medicationName = med.name,
                        phaseSummary = summarizePhases(swp.phases),
                        endsLabel = endsLabel(swp.schedule.endDate, today),
                    )
                }
            // Group by pet name, then medication name. Stable sort preserves
            // schedule-id order within (pet, med) tuples — useful when one medication
            // has multiple active schedules (rare; e.g. multiple tapering phases that
            // for some reason live in separate Schedule rows).
            val sorted =
                rows.sortedWith(
                    compareBy(
                        { it.petName.lowercase() },
                        { it.medicationName.lowercase() },
                        { it.scheduleId },
                    ),
                )
            return ReminderListUiState(rows = sorted, loading = false)
        }

        /**
         * Compact one-line description of a schedule's phases. Examples:
         *   - "Once daily" (single phase, 1 dose/day)
         *   - "Twice daily" (single phase, 2 doses/day)
         *   - "3× daily for 14 days, then 2× daily" (multi-phase taper)
         *
         * The 3-phase+ case folds remaining phases into "(+N more)" to keep rows
         * single-line.
         */
        internal fun summarizePhases(phases: List<SchedulePhase>): String {
            if (phases.isEmpty()) return "(no phases)"
            val first = phaseShort(phases[0])
            if (phases.size == 1) return first
            val second = phaseShort(phases[1])
            if (phases.size == 2) return "$first, then $second"
            return "$first, then $second (+${phases.size - 2} more)"
        }

        private fun phaseShort(p: SchedulePhase): String {
            val cadence =
                when (p.dosesPerDay) {
                    1 -> "Once daily"
                    2 -> "Twice daily"
                    else -> "${p.dosesPerDay}× daily"
                }
            // SchedulePhase always carries a non-null durationDays (required field, 1..3650).
            // We omit it from the label only when the schedule has no end date AND a
            // single phase — the simplest "ongoing" case, where appending "for 3650
            // days" is misleading. Multi-phase tapers and bounded schedules show the
            // duration so the user can quickly verify the tapering plan.
            return "$cadence for ${p.durationDays} days"
        }

        /**
         * End-date label. Returns null when the schedule has no end date (the row UI
         * will hide the label entirely rather than render "Ends: —").
         */
        internal fun endsLabel(
            endDate: LocalDate?,
            today: LocalDate,
        ): String? {
            if (endDate == null) return null
            val daysLeft = endDate.toEpochDays() - today.toEpochDays()
            return when {
                daysLeft < 0 -> "Ended"
                daysLeft == 0 -> "Ends today"
                daysLeft == 1 -> "Ends tomorrow"
                daysLeft < 7 -> "Ends in $daysLeft days"
                else -> "Ends $endDate"
            }
        }
    }
}

/**
 * One row in the Reminder List screen. Carries enough data for the row UI plus the
 * navigation keys (`petId`, `medicationId`, `scheduleId`) the tap handler will use
 * when Schedule Detail (B7) ships.
 */
public data class ReminderRowUi(
    public val scheduleId: String,
    public val petId: String,
    public val petName: String,
    public val medicationId: String,
    public val medicationName: String,
    public val phaseSummary: String,
    public val endsLabel: String?,
)

/** UI state for the Reminder List screen. Immutable snapshot. */
public data class ReminderListUiState(
    public val rows: List<ReminderRowUi>,
    public val loading: Boolean,
)
