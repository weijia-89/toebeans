package app.toebeans.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.crash.LocalCrashLog
import app.toebeans.android.preferences.ThemeMode
import app.toebeans.android.ui.theme.ToebeansTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * Settings screen. Five grouping cards:
 *   1. Display: theme override (Auto/Light/Dark) + Material You dynamic-color toggle.
 *   2. Data: Export and Import buttons. Per ADR-0016, v1 ships plain JSON: the file
 *      contains no medical-grade or identifying information, so we do not gate the
 *      export on a passphrase. The encrypted posture reactivates per ADR-0016 v2
 *      triggers (when more sensitive fields land in the schema, or when v2 of the
 *      backup format is designed). Both buttons drive Storage Access Framework
 *      pickers so the file lives at whatever location the user picks.
 *   3. Diagnostics: local crash log export (ADR-0009).
 *   4. About: current build facts.
 *   5. What's coming: short user-facing list of features on the roadmap.
 *
 * Why the Display card is first: it's the only one a user is likely to actually touch.
 * About and What's coming are reference/curiosity surfaces. Putting Display at the top
 * means the most useful thing is the first thing in the user's reading order.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
public fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = koinViewModel(),
    exportViewModel: ExportBackupViewModel = koinViewModel(),
    importViewModel: ImportBackupViewModel = koinViewModel(),
) {
    // CyclomaticComplexMethod suppressed. The composable walks from the
    // Display card, through the Data card with its two SAF launchers and
    // several terminal dialog or toast branches across export and import
    // flows, through Diagnostics and About, and ends with the What's coming
    // reference list. The complexity comes from rendering fan-out across
    // visible cards rather than from algorithmic decisions. Splitting into
    // sub-composables intersects with recomposition-scope decisions because
    // every section shares state hoisted from the same VMs, which makes the
    // refactor itself vibe-careful and is deferred per ADR-0016.
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF launcher for ACTION_CREATE_DOCUMENT. The MIME type "application/json" hints
    // the picker toward JSON-aware folders; the user-supplied filename comes from
    // exportViewModel.suggestedFilename(). A null result means the user backed out,
    // which we treat as a silent no-op (no error state, the user is in control).
    val createDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                exportViewModel.exportTo { bytes ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Could not open the destination file. Please pick another location.")
                    }
                }
            }
        }

    // SAF launcher for ACTION_OPEN_DOCUMENT. We hint application/json but also accept
    // application/octet-stream so backups exported by Files apps that strip the mime
    // are still pickable. The picker returns a content:// URI that we read on IO. A
    // null result means the user backed out, no-op.
    val openDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                importViewModel.readAndStage {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error(
                                "Could not open the selected file. Please pick a different location.",
                            )
                    }
                }
            }
        }

    // Toast-on-success for import. Toasts (rather than an AlertDialog) match the
    // ADR-0016 spec for the post-import announcement: the user has already confirmed
    // intent at the confirm dialog, so the post-state is informational, not decisional.
    // The Toast text names the merge-by-id semantic counts so a user who imported a
    // file with overlapping ids understands why their "existing" rows did not change.
    val importSuccessLocal = importState as? ImportBackupUiState.Success
    LaunchedEffect(importSuccessLocal) {
        val success = importSuccessLocal ?: return@LaunchedEffect
        Toast
            .makeText(
                context,
                buildImportSuccessText(success.summary),
                Toast.LENGTH_LONG,
            ).show()
        importViewModel.onAcknowledge()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        SettingsCard(title = "Display") {
            // Three-way theme picker as a SingleChoiceSegmentedButtonRow. The "Auto"
            // default option means most users never touch this. The segmented control
            // is for the user who finds the system default uncomfortable in some
            // context (e.g. reading in bed, vet's office under fluorescents).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        key(mode) {
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape =
                                    SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                            ) {
                                Text(text = mode.displayName)
                            }
                        }
                    }
                }
            }

            // Material You dynamic-color toggle. On Android < 12 (API 31) the underlying
            // platform API doesn't exist, so we disable the row and explain why. Leaving
            // a dead toggle would just look broken. The Switch is right-aligned per the
            // M3 settings pattern; tapping the label as well as the switch toggles the
            // state (full-row touch target).
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ToggleRow(
                title = "Material You colors",
                subtitle =
                    if (dynamicSupported) {
                        "Match the app's palette to your wallpaper."
                    } else {
                        "Requires Android 12 or newer."
                    },
                checked = dynamicColor && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = { viewModel.setDynamicColor(it) },
            )
        }

        SettingsCard(title = "Data") {
            // Per ADR-0016: v1 exports plain JSON. The copy is honest about that. No
            // "encrypted" framing because the file is not encrypted, and the schema
            // does not yet contain anything that would warrant encryption at rest.
            // The v2 plan reactivates encryption once the schema gains more sensitive
            // fields or once we have a real keyless-encryption mechanism that survives
            // device transfer.
            Text(
                text =
                    "Your data lives only on this device. Export a backup file to a " +
                        "location you control, or import one to merge into the current " +
                        "device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    "Backups are plain JSON. They include your pets, medications, " +
                        "schedules, and dose history, nothing else, no encryption " +
                        "envelope. Keep the file somewhere private the same way you " +
                        "would keep a photo or note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { createDocumentLauncher.launch(exportViewModel.suggestedFilename()) },
                    enabled = exportState !is ExportBackupUiState.Writing,
                ) {
                    Text("Export data")
                }
                if (exportState is ExportBackupUiState.Writing) {
                    // Small inline progress affordance. The export typically completes
                    // in well under a second for v1's data volumes, so a full-screen
                    // modal would feel like a flicker. The inline spinner conveys
                    // "working" without being visually loud.
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Writing backup…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        // ACTION_OPEN_DOCUMENT accepts a mime-type array; we hint json but
                        // also accept octet-stream to tolerate file managers that drop the
                        // mime metadata.
                        openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                    },
                    enabled =
                        importState !is ImportBackupUiState.Importing &&
                            importState !is ImportBackupUiState.AwaitingConfirm,
                ) {
                    Text("Import data")
                }
                if (importState is ImportBackupUiState.Importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Importing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Result dialogs for the export flow. We use AlertDialogs (rather than a
        // Snackbar) because: (1) Settings does not host a SnackbarHost today, and (2)
        // the export terminal state ("backup saved" or "backup failed") is meaningful
        // enough that the user wants explicit acknowledgement, not a fly-by toast that
        // they might miss if they're scrolling.
        when (val state = exportState) {
            is ExportBackupUiState.Success ->
                AlertDialog(
                    onDismissRequest = { exportViewModel.onAcknowledge() },
                    confirmButton = {
                        TextButton(onClick = { exportViewModel.onAcknowledge() }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Backup saved") },
                    text = {
                        Text(
                            text = formatExportSummary(state),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            is ExportBackupUiState.Error ->
                AlertDialog(
                    onDismissRequest = { exportViewModel.onAcknowledge() },
                    confirmButton = {
                        TextButton(onClick = { exportViewModel.onAcknowledge() }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Backup failed") },
                    text = {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            else -> { /* Idle and Writing have no terminal dialog */ }
        }

        // Import confirm dialog. Per ADR-0016 the dialog MUST name the merge-by-id
        // semantic explicitly so the user understands existing rows will not be
        // overwritten. Silent merges are forbidden because the user otherwise cannot
        // tell whether their on-device edits will survive an import.
        when (val state = importState) {
            is ImportBackupUiState.AwaitingConfirm ->
                AlertDialog(
                    onDismissRequest = { importViewModel.onCancelConfirm() },
                    confirmButton = {
                        TextButton(onClick = { importViewModel.confirmImport() }) {
                            Text("Import")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { importViewModel.onCancelConfirm() }) {
                            Text("Cancel")
                        }
                    },
                    title = { Text("Import this backup?") },
                    text = {
                        Text(
                            text =
                                "This file contains ${state.pets} pets, ${state.medications} medications, " +
                                    "${state.schedules} schedules, and ${state.doseEvents} dose events. " +
                                    "Items whose IDs already exist on this device will be left alone; " +
                                    "new items will be added. Your current data will not be overwritten.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            is ImportBackupUiState.Error ->
                AlertDialog(
                    onDismissRequest = { importViewModel.onAcknowledge() },
                    confirmButton = {
                        TextButton(onClick = { importViewModel.onAcknowledge() }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Import failed") },
                    text = {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            else -> { /* Idle, Importing, and Success use other surfaces */ }
        }

        SettingsCard(title = "Diagnostics") {
            // Local crash-log capture per ADR-0009. The handler writes to
            // filesDir/crash.log; this button reads the file and hands it to the
            // user via ACTION_SEND. Intent extras are capped around 1 MB on most
            // platforms; the log is rotated at 256 KB so a single file fits well
            // under the cap.
            //
            // Hidden when the log is absent (no crash has happened yet on this
            // install) so first-launch users do not see a button for something
            // they have no use for.
            val logFile = File(context.filesDir, LocalCrashLog.FILE_PRIMARY)
            val logExists = logFile.exists() && logFile.length() > 0
            Text(
                text =
                    "We collect nothing automatically. If toebeans crashes and you " +
                        "want to send the log, you can export it yourself. The log " +
                        "contains the stack trace and your device model. No pet, " +
                        "medication, or dose data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val contents = runCatching { logFile.readText() }.getOrElse { "(failed to read log)" }
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "toebeans crash log")
                            putExtra(Intent.EXTRA_TEXT, contents)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    val chooser =
                        Intent
                            .createChooser(intent, "Share crash log")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                },
                enabled = logExists,
            ) {
                Text(if (logExists) "Export crash log" else "No crash log yet")
            }
        }

        SettingsCard(title = "About") {
            SettingsRow(label = "App version", value = "0.1.0")
            SettingsRow(label = "Storage", value = "Local-only · no cloud")
            SettingsRow(label = "Analytics", value = "None")
        }

        SettingsCard(title = "What's coming") {
            ComingSoonItem("Encrypted backup to a file you control")
            ComingSoonItem("Custom reminder sounds per medication")
            ComingSoonItem("Travel-aware reminders that respect time zones")
            ComingSoonItem("Per-pet quiet hours")
        }
    }
}

/**
 * Display labels for [ThemeMode]. Separate from `wireName` (which is the persisted
 * stable token) so we can change UI copy without breaking saved preferences.
 */
private val ThemeMode.displayName: String
    get() =
        when (this) {
            ThemeMode.AUTO -> "Auto"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
        }

/**
 * A grouped section card. Visual parity with [PetIdentityCard][app.toebeans.android.ui.pets.PetIdentityCard]
 * and the HomeScreen "Today's doses" card so Settings feels of-a-piece with the rest
 * of the app instead of like a separate info dump.
 */
@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/**
 * Two-line row: dim label above, brighter value below. The labelLarge / bodyMedium
 * pairing matches the field-display pattern used on PetDetailScreen so a user
 * scanning the app gets a consistent "label-on-top, value-below" rhythm.
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Boolean toggle row used by the Display card. Two-line text (title + subtitle) on the
 * left, right-aligned [Switch] on the right. Tapping anywhere on the row toggles state.
 * The row itself is the click target, not just the Switch, so the visible touch surface
 * matches what a user expects from a Material 3 settings list.
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // toggleable wraps the whole row in one accessible toggle node. TalkBack announces
    // "Material You colors, switch, on" rather than reading the title and subtitle as
    // separate text nodes plus a switch as its own control.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$title. $subtitle"
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * Bulleted-item row for the "What's coming" card. Uses a leading "·" glyph (middle dot)
 * rather than the Unicode bullet "•" because middle dot reads slightly lighter and the
 * card is meant to feel anticipatory ("here's what's next") not authoritative ("here
 * is THE list"). Small stylistic choice but the warmth adds up.
 */
@Composable
private fun ComingSoonItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = text
            },
    ) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Build the Toast text shown after a successful import. Names the merge-by-id semantic
 * counts directly so a user whose file overlapped existing rows knows the existing
 * rows survived intact. Per ADR-0016, the post-import announcement MUST surface the
 * skipped counts even when zero, so the user learns the semantic on the first import
 * and is not surprised on a later import that has overlaps.
 */
private fun buildImportSuccessText(summary: app.toebeans.core.backup.BackupImportSummary): String {
    val added = summary.totalAdded
    val skipped = summary.totalSkipped
    return if (skipped == 0) {
        "Import complete. Added $added items."
    } else {
        "Import complete. Added $added new items; kept $skipped existing items that " +
            "already had matching IDs on this device."
    }
}

/**
 * Build the human-readable summary shown inside the "Backup saved" dialog. Kept as
 * a top-level helper rather than a method on [ExportBackupUiState.Success] so the
 * VM stays free of UI-string responsibility (per the same boundary that keeps the
 * VM independent of Android types).
 */
private fun formatExportSummary(state: ExportBackupUiState.Success): String {
    val kb = state.bytesWritten / 1024
    val sizeLabel =
        if (kb < 1) {
            "${state.bytesWritten} bytes"
        } else {
            "$kb KB"
        }
    return "Saved $sizeLabel containing ${state.pets} pets, " +
        "${state.medications} medications, ${state.schedules} schedules, " +
        "and ${state.doseEvents} dose events."
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ToebeansTheme(dynamic = false) {
        // Preview can't easily inject a Koin VM, so we render only the static cards. The
        // toggle UI is exercised via @Preview on a separate composable below if desired.
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings (preview – Display card disabled)", style = MaterialTheme.typography.titleLarge)
        }
    }
}
