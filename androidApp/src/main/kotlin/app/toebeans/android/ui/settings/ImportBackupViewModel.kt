package app.toebeans.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.toebeans.core.backup.BackupFormatException
import app.toebeans.core.backup.BackupImportSummary
import app.toebeans.core.backup.BackupImporter
import app.toebeans.core.backup.BackupSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings → Import data flow per ADR-0016.
 *
 * Mirrors the export VM's split: this VM does not depend on Android types. The Composable
 * layer is responsible for:
 *
 *  1. Launching the SAF [android.content.Intent.ACTION_OPEN_DOCUMENT] picker with the
 *     `application/json` mime type.
 *  2. Receiving the user-picked URI back from the launcher.
 *  3. Reading the bytes from the URI on an IO dispatcher.
 *  4. Calling [confirmImport] with the byte payload once the user has acknowledged the
 *     merge-by-id confirmation dialog.
 *
 * **State machine:**
 *  - `Idle`: no file picked.
 *  - `AwaitingConfirm(bytes)`: a file has been picked and parsed successfully; the
 *     Composable shows a confirmation dialog that names the merge-by-id semantic so the
 *     user understands existing rows will not be touched. (Per ADR-0016 the dialog must
 *     name the semantic explicitly; silent merges are forbidden because they hide a
 *     potentially data-shaping decision.)
 *  - `Importing`: write-in-progress while [BackupImporter.import] runs.
 *  - `Success(summary)`: import completed; the post-import toast/dialog reports counts.
 *  - `Error(message)`: parse or import failure.
 *
 * The user dismisses Success/Error via [onAcknowledge], which returns the VM to Idle.
 * The user cancels the confirm dialog via [onCancelConfirm].
 */
public class ImportBackupViewModel(
    private val serializer: BackupSerializer,
    private val importer: BackupImporter,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportBackupUiState>(ImportBackupUiState.Idle)
    public val state: StateFlow<ImportBackupUiState> = _state.asStateFlow()

    /**
     * Parse a freshly-picked backup file. If the parse succeeds we move to
     * AwaitingConfirm; the actual import is gated on the user tapping "Import" in the
     * confirm dialog (so a user who accidentally picks the wrong file can still back
     * out without writing anything).
     *
     * @param fileBytes the raw file contents. The Composable layer reads this from the
     *   SAF-picked URI on an IO dispatcher and hands the bytes here.
     */
    public fun stageFile(fileBytes: ByteArray) {
        viewModelScope.launch {
            stageFileInternal(fileBytes)
        }
    }

    /**
     * Same as [stageFile], but the read is performed by the supplied suspend lambda
     * inside the VM's coroutine scope. The Composable calls this with a lambda that
     * uses ContentResolver to read the SAF-picked URI on Dispatchers.IO. This mirrors
     * the export VM's [ExportBackupViewModel.exportTo] pattern so the Composable does
     * not need to manage its own coroutine scope for the read.
     */
    @Suppress("TooGenericExceptionCaught")
    public fun readAndStage(readBytes: suspend () -> ByteArray) {
        viewModelScope.launch {
            val bytes =
                try {
                    readBytes()
                } catch (e: Exception) {
                    // TooGenericExceptionCaught suppressed. readBytes is a
                    // caller-supplied suspend lambda backed by ContentResolver
                    // on a SAF URI, so any IO failure here surfaces as one
                    // recoverable Error state for the user. Catching Exception
                    // rather than Throwable lets OutOfMemoryError and
                    // StackOverflowError continue to bubble per detekt guidance.
                    _state.value =
                        ImportBackupUiState.Error(
                            message = "Could not read the backup file: ${e.message ?: e::class.simpleName}",
                        )
                    return@launch
                }
            stageFileInternal(bytes)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun stageFileInternal(fileBytes: ByteArray) {
        try {
            if (fileBytes.isEmpty()) {
                _state.value =
                    ImportBackupUiState.Error(
                        message = "The backup file appears to be empty. Please pick a different file.",
                    )
                return
            }
            val text = fileBytes.decodeToString()
            val parsed = serializer.decodeFromString(text)
            _state.value =
                ImportBackupUiState.AwaitingConfirm(
                    pets = parsed.pets.size,
                    medications = parsed.medications.size,
                    schedules = parsed.schedules.size,
                    doseEvents = parsed.doseEvents.size,
                    parsed = parsed,
                )
        } catch (e: BackupFormatException) {
            _state.value =
                ImportBackupUiState.Error(
                    message = e.message ?: "The backup file format is not supported.",
                )
        } catch (e: Exception) {
            // TooGenericExceptionCaught suppressed. Decode and post-parse
            // checks can raise unexpected deserialization failures beyond the
            // typed BackupFormatException already caught above. Surfacing them
            // as one Error message is the documented contract for the
            // merge-by-id flow; catching Exception rather than Throwable lets
            // fatal VM errors continue to bubble.
            _state.value =
                ImportBackupUiState.Error(
                    message = "Could not read the backup file: ${e.message ?: e::class.simpleName}",
                )
        }
    }

    /**
     * Apply the merge-by-id import to the staged payload. Called from the confirm
     * dialog's primary button. No-op if the current state is not AwaitingConfirm
     * (defensive against double-tap or stale callbacks).
     */
    @Suppress("TooGenericExceptionCaught")
    public fun confirmImport() {
        val current = _state.value as? ImportBackupUiState.AwaitingConfirm ?: return
        _state.value = ImportBackupUiState.Importing
        viewModelScope.launch {
            try {
                val summary = importer.import(current.parsed)
                _state.value = ImportBackupUiState.Success(summary)
            } catch (e: Exception) {
                // TooGenericExceptionCaught suppressed. The importer fans in
                // writes across the pet, medication, schedule, and dose-event
                // repositories, and any database or serialization failure
                // surfaces as one recoverable Error state for the user.
                // Catching Exception rather than Throwable lets fatal VM
                // errors continue to bubble per detekt guidance.
                _state.value =
                    ImportBackupUiState.Error(
                        message = "Import failed: ${e.message ?: e::class.simpleName}",
                    )
            }
        }
    }

    /** User cancelled the confirm dialog without importing. Returns to Idle. */
    public fun onCancelConfirm() {
        _state.value = ImportBackupUiState.Idle
    }

    /** Dismiss a terminal Success/Error state. Returns to Idle. */
    public fun onAcknowledge() {
        _state.value = ImportBackupUiState.Idle
    }
}

public sealed class ImportBackupUiState {
    public data object Idle : ImportBackupUiState()

    public data class AwaitingConfirm(
        public val pets: Int,
        public val medications: Int,
        public val schedules: Int,
        public val doseEvents: Int,
        // Internal: the already-parsed payload; the Composable does not read this.
        internal val parsed: app.toebeans.core.backup.BackupExport,
    ) : ImportBackupUiState()

    public data object Importing : ImportBackupUiState()

    public data class Success(
        public val summary: BackupImportSummary,
    ) : ImportBackupUiState()

    public data class Error(
        public val message: String,
    ) : ImportBackupUiState()
}
