package app.toebeans.android.util

import android.util.Log
import app.toebeans.android.BuildConfig

/**
 * Centralized handling for "this row references a parent that no longer exists" cases
 * that surface during UI rendering — most notably [app.toebeans.android.ui.home.HomeViewModel]'s
 * join from `DoseEvent` to its `Medication` and from `Schedule` to its `Medication` + `Pet`.
 *
 * ## Why this is needed
 *
 * Before this guard, `joinToUiState` silently dropped any `DoseEvent` whose `medicationId`
 * could not be resolved. That silent filter is exactly the failure class that hid the
 * `replaceFirst("sched-", "med-")` join bug for the entire scaffold milestone — every
 * user-created medication's dose was silently skipped from the Logged Today card and
 * nobody noticed because the screen was never empty (the seed data still rendered).
 *
 * The safety net here makes that failure class loud during development and merely noisy
 * in production. We *want* a crash in CI when a stale-row condition arises, because that
 * means a developer introduced a join bug. We *do not* want a crash in a tester's pocket
 * while they're trying to log their cat's medication.
 *
 * ## Debug vs release behavior
 *
 * - **Debug builds** (including all unit tests, which always run against the debug
 *   variant's BuildConfig): throws `IllegalStateException` with the diagnostic message.
 * - **Release builds**: emits a `Log.w` line at WARN level and returns. The caller's
 *   `?: return@mapNotNull null` continues to skip the row, preserving the rest of the
 *   render.
 *
 * The release-mode `Log.w` is intentional rather than a `Log.e`: the inter-Flow race
 * window — where the doses Flow has not yet caught up with a deletion in the meds Flow
 * — is a normal transient and does not indicate a bug. The signal we actually care about
 * for release is "this is happening repeatedly", which a tester would surface through the
 * crash-log export if anything else also goes wrong.
 *
 * ## Why not always crash
 *
 * The two observed Flows (`doseEventRepository.observeRecent(...)` and
 * `medicationRepository.observeAll()`) are independent. A deletion of a medication briefly
 * leaves the doses Flow holding events whose `medicationId` is already gone from the meds
 * Flow. This is unavoidable without merging the two queries into one SQLDelight statement
 * (M1 work). Crashing in this window would crash legitimate use.
 *
 * ## Why not always swallow
 *
 * The whole point of this guard is to NOT swallow during development. Silent filtering
 * is how the original `replaceFirst` bug hid for so long.
 *
 * ## What this guard is NOT
 *
 * - Not a generic logger. Use `Log.w` directly for non-stale-event warnings.
 * - Not a crash reporter. Crash logs are captured by
 *   [app.toebeans.android.crash.LocalCrashLog] from uncaught exceptions; this guard
 *   either throws (so LocalCrashLog catches it) or logs (so it does not).
 * - Not a fitness function. The CI gate that prevents `LocalCrashLog.kt` from
 *   referencing domain models is independent (see `scripts/test_no_pii_in_crash_log.sh`).
 */
public object StaleEventGuard {
    internal const val TAG: String = "toebeans-stale"

    /**
     * Report a stale-row condition at [site], where the row identified by [eventId]
     * references [missingFieldName] = [missingValue] which cannot be resolved.
     *
     * Returns `null` so the caller can compose it with `?:` directly into an existing
     * mapNotNull / let pattern without changing control flow shape:
     *
     * ```kotlin
     * val med = medById[event.medicationId]
     *     ?: return@mapNotNull StaleEventGuard.reportMissing(
     *         site = "Home.joinToUiState",
     *         eventId = event.id,
     *         missingFieldName = "medicationId",
     *         missingValue = event.medicationId,
     *     )
     * ```
     *
     * @return always `null`. The return type is `Nothing?` to make this safe inside
     *   `return@mapNotNull` blocks.
     */
    public fun reportMissing(
        site: String,
        eventId: String,
        missingFieldName: String,
        missingValue: String,
    ): Nothing? {
        val msg = buildMessage(site, eventId, missingFieldName, missingValue)
        if (BuildConfig.DEBUG) {
            throw IllegalStateException(msg)
        }
        Log.w(TAG, msg)
        return null
    }

    /**
     * Visible for testing. Pure message builder so tests can assert on the format
     * without coupling to the throw/log dispatch.
     */
    internal fun buildMessage(
        site: String,
        eventId: String,
        missingFieldName: String,
        missingValue: String,
    ): String =
        "stale row at $site: event=$eventId references $missingFieldName=$missingValue " +
            "which no longer exists. Likely an inter-Flow race during deletion; the " +
            "render will skip this row in release builds and crash in debug to surface " +
            "any real join bug."
}
