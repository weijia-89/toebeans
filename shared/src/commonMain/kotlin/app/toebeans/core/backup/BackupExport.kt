package app.toebeans.core.backup

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Full owner-data backup payload.
 *
 * This is the serialized contents of an export file BEFORE encryption (see [BackupCipher]).
 * The on-disk format wraps a JSON-encoded [BackupExport] in an AES-256-GCM envelope, prefixed
 * with the salt and IV. Plain JSON (no encryption) is never written by toebeans.
 *
 * **Versioning.** [schemaVersion] is bumped any time a field is added/removed/renamed.
 * Import logic dispatches on it via [BackupSerializer.import]. We never break readers of older
 * versions; new fields are always optional with sensible defaults.
 *
 * @property schemaVersion integer version of the backup wire format. v1 == 1.
 * @property exportedAt the instant the user pressed "Export" (informational; not used for
 *           import semantics).
 * @property appVersion the application versionName at export time (e.g. "0.1.0"). Used for
 *           humane error messages when an old app encounters a backup from a newer app.
 * @property pets every pet (active and archived).
 * @property medications every medication (active and discontinued).
 * @property schedules every schedule.
 * @property schedulePhases phases for the above schedules.
 * @property doseEvents the dose-event history. Pending DoseEvents are NOT included in v1
 *           — they will be re-materialized by the scheduler after import. Including only
 *           resolved (given / skipped / missed) events keeps the file small and avoids
 *           replaying stale pending notifications on the destination device.
 */
@Serializable
public data class BackupExport(
    val schemaVersion: Int,
    val exportedAt: Instant,
    val appVersion: String,
    val pets: List<Pet>,
    val medications: List<Medication>,
    val schedules: List<Schedule>,
    val schedulePhases: List<SchedulePhase>,
    val doseEvents: List<DoseEvent>,
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be >= 1 (was $schemaVersion)" }
    }

    public companion object {
        /** Current wire format version. Bump when fields change. */
        public const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
