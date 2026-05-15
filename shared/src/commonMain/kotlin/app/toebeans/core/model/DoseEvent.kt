package app.toebeans.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A single dose occurrence. Materialized lazily by the scheduler at a 72-hour horizon (per
 * AGENTS.md: pre-generating an entire phase is a vibe-impossible anti-pattern).
 *
 * Lifecycle: `pending` -> `given` | `skipped` | `missed`.
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
