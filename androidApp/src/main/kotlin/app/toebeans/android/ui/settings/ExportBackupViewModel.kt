package app.toebeans.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.backup.BackupAggregator
import app.toebeans.core.backup.BackupSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Settings → Export data flow per ADR-0016 (plain JSON; no encryption at
 * v1).
 *
 * The VM does NOT depend on [android.net.Uri] or any Android platform type. Tests stay
 * pure-JVM. The Composable layer is responsible for:
 *
 *  1. Computing the SAF [android.content.Intent.ACTION_CREATE_DOCUMENT] launch with
 *     [suggestedFilename] as the default name.
 *  2. Receiving the user-picked URI back from the launcher.
 *  3. Calling [exportTo] with a `writeBytes` lambda that uses ContentResolver to write
 *     the bytes to that URI.
 *
 * This split keeps the VM testable without Robolectric and keeps the file-IO concern
 * out of the VM (which would otherwise need a `ContentResolver` injected).
 *
 * State machine: `Idle` -> `Writing` -> `Success(summary)` | `Error(message)`. The user
 * dismisses Success or Error via [onAcknowledge], which returns the VM to Idle.
 */
public class ExportBackupViewModel(
    private val aggregator: BackupAggregator,
    private val serializer: BackupSerializer,
    private val appVersion: String,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow<ExportBackupUiState>(ExportBackupUiState.Idle)
    public val state: StateFlow<ExportBackupUiState> = _state.asStateFlow()

    /**
     * Default filename for the SAF picker, in `toebeans-backup-MMDDYYYY-HHMM.json` form.
     * Computed at call time (not cached) so a user who picks Export, cancels, then picks
     * Export again gets a fresh timestamp.
     */
    public fun suggestedFilename(): String {
        val now = clock.now().toLocalDateTime(timeZone)
        val mm = now.monthNumber.toString().padStart(2, '0')
        val dd = now.dayOfMonth.toString().padStart(2, '0')
        val yyyy = now.year.toString()
        val hh = now.hour.toString().padStart(2, '0')
        val min = now.minute.toString().padStart(2, '0')
        return "toebeans-backup-$mm$dd$yyyy-$hh$min.json"
    }

    /**
     * Run the export pipeline: aggregate the local-state snapshot, serialize to JSON,
     * pass the bytes to [writeBytes] for the caller-supplied destination (typically a
     * SAF URI). State transitions: any -> Writing -> Success | Error.
     *
     * `writeBytes` is a suspend lambda so the caller can move the actual content-resolver
     * write to an IO dispatcher inside the callback. Failures inside `writeBytes` are
     * caught and surfaced as [ExportBackupUiState.Error].
     */
    public fun exportTo(writeBytes: suspend (ByteArray) -> Unit) {
        viewModelScope.launch {
            _state.value = ExportBackupUiState.Writing
            try {
                val backup = aggregator.collect(appVersion = appVersion, exportedAt = clock.now())
                val json = serializer.encodeToString(backup)
                val bytes = json.encodeToByteArray()
                writeBytes(bytes)
                _state.value =
                    ExportBackupUiState.Success(
                        bytesWritten = bytes.size,
                        pets = backup.pets.size,
                        medications = backup.medications.size,
                        schedules = backup.schedules.size,
                        doseEvents = backup.doseEvents.size,
                    )
            } catch (t: Throwable) {
                // Catch broadly so any failure (aggregator IO, serializer, writeBytes)
                // surfaces in the UI rather than crashing. The user gets a recoverable
                // error message; the calibration log captures whether real failures show
                // up in the wild for the v2 design iteration.
                _state.value = ExportBackupUiState.Error(t.message ?: "Unknown error")
            }
        }
    }

    /** Dismiss the current Success/Error state. Returns the VM to Idle. */
    public fun onAcknowledge() {
        _state.value = ExportBackupUiState.Idle
    }
}

public sealed class ExportBackupUiState {
    public data object Idle : ExportBackupUiState()

    public data object Writing : ExportBackupUiState()

    public data class Success(
        public val bytesWritten: Int,
        public val pets: Int,
        public val medications: Int,
        public val schedules: Int,
        public val doseEvents: Int,
    ) : ExportBackupUiState()

    public data class Error(
        public val message: String,
    ) : ExportBackupUiState()
}
