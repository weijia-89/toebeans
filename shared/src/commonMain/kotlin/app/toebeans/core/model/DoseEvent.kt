package app.toebeans.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A single dose occurrence. Materialized lazily by the scheduler at a 72-hour horizon (per
 * AGENTS.md: pre-generating an entire phase is a vibe-impossible anti-pattern).
 *
 * Lifecycle: `pending` -> `given` | `skipped` | `missed`.
 *
 * **Why [medicationId] is denormalized onto the event row.** A dose event belongs to a
 * schedule, which belongs to a medication, which belongs to a pet. Logically [medicationId]
 * is reachable via the join `DoseEvent → Schedule → Medication`. We store it directly on
 * the event for two reasons:
 *
 *   1. **Retrospective surfaces stay correct after edits.** If a user edits a medication's
 *      name from "Methimazole" to "Methimazole (compounded)", the historical Logged-Today /
 *      30-day-history rows should keep showing the name the user knew at the time. The
 *      `medicationId` is stable; the display name resolves through the current Medication
 *      row, but the link is preserved even if the live schedule is deleted (orphan-survival).
 *
 *   2. **It eliminates a hand-coded string-join.** Prior versions of `HomeViewModel.joinToUiState`
 *      computed `event.scheduleId.replaceFirst("sched-", "med-")` to find the medication.
 *      That only worked for the seeded demo IDs (`sched-luna-methimazole` /
 *      `med-luna-methimazole`); every user-created medication's dose silently dropped from
 *      the retrospective view because the new schedule and medication had unrelated UUIDs.
 *      Carrying [medicationId] on the event row is the structural fix.
 *
 * @property scheduledAt the instant the dose was supposed to be administered.
 * @property firedAt the instant the notification actually surfaced (may differ from
 *           [scheduledAt] by OS scheduling jitter).
 * @property resolvedAt the instant the user tapped "Given" or "Skipped", or the system marked
 *           the event "missed".
 */
@Serializable
public data class DoseEvent(
    val id: String,
    val scheduleId: String,
    val medicationId: String,
    val scheduledAt: Instant,
    val firedAt: Instant?,
    val resolvedAt: Instant?,
    val status: DoseStatus,
    val note: String?,
)

@Serializable
public enum class DoseStatus(
    public val wireName: String,
) {
    PENDING("pending"),
    GIVEN("given"),
    SKIPPED("skipped"),
    MISSED("missed"),
    ;

    public companion object {
        public fun fromWireName(value: String): DoseStatus =
            entries.firstOrNull { it.wireName == value }
                ?: error("Unknown dose status '$value'.")
    }
}
