package app.toebeans.android.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.PillBackground
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MedicationEditScreen(
    petId: String,
    medicationId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MedicationEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscontinueDialog by remember { mutableStateOf(false) }

    LaunchedEffect(petId, medicationId) {
        viewModel.prepareRoute(petId, medicationId)
    }

    if (showDeleteDialog) {
        DeleteMedicationDialog(
            medicationName = state.name.ifBlank { "this medication" },
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    if (viewModel.delete()) onSaved()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showDiscontinueDialog) {
        DiscontinueMedicationDialog(
            medicationName = state.name.ifBlank { "this medication" },
            onConfirm = {
                showDiscontinueDialog = false
                scope.launch {
                    // Stay on the form after discontinue so the user can still edit notes
                    // or reactivate. The banner will appear on the next state emission.
                    viewModel.discontinue()
                }
            },
            onDismiss = { showDiscontinueDialog = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative pill silhouette behind the form. Low-alpha; does not interfere with
        // foreground contrast. See PillBackground.kt for the WCAG audit note.
        PillBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            // fillMaxSize: without this, the Scaffold uses wrap-content height which
            // collapses the bottomBar up against the form content instead of pinning it to
            // the bottom of the parent Box.
            modifier = Modifier.fillMaxSize(),
            // Surface is set transparent so the PillBackground shows through. The Scaffold's
            // own surface color would paint over our decoration.
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (state.isNew) "Add medication" else "Edit medication") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Extracted to keep MedicationEditScreen's cyclomatic complexity below
                        // the detekt threshold. The Discontinue/Reactivate/Delete branching
                        // adds ~3-4 conditional edges that, combined with the dialog visibility
                        // checks and the form's other branches, push the function over the
                        // 15-edge cap.
                        MedicationEditTopBarActions(
                            isNew = state.isNew,
                            isDiscontinued = state.isDiscontinued,
                            onReactivateClick = { scope.launch { viewModel.reactivate() } },
                            onDiscontinueClick = { showDiscontinueDialog = true },
                            onDeleteClick = { showDeleteDialog = true },
                        )
                    },
                    // Save moved out of the top bar. Top-right is a thumb-stretch on most
                    // phones. The new primary Save lives in bottomBar below.
                )
            },
            bottomBar = {
                // Full-width primary Save button at the bottom, reachable with one thumb.
                // Wrapped in a Surface so it has the proper Material 3 elevation tone and a
                // visible separation from the form content above.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Button(
                        enabled = state.name.isNotBlank() && state.doseAmount.isNotBlank(),
                        onClick = {
                            scope.launch {
                                if (viewModel.save()) onSaved()
                            }
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            if (state.isNew) "Save medication" else "Save changes",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            },
        ) { inner ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MedicationEditContextCard(
                    petName = state.petName,
                    medicationName = state.name.takeIf { it.isNotBlank() },
                    doseAmount = state.doseAmount.takeIf { it.isNotBlank() },
                    scheduleHint = state.scheduleHint,
                    isNew = state.isNew,
                )

                // When this medication is discontinued, surface a status banner above the
                // form fields so it is the first thing the user sees on opening the screen.
                // Wrapped in clearAndSetSemantics + LiveRegion equivalent via contentDescription
                // so TalkBack announces the discontinue state immediately, matching the
                // ScheduleCreate formError + night-dose banner accessibility pattern.
                state.discontinuedAt?.let { discontinuedAt ->
                    DiscontinuedBanner(discontinuedAt = discontinuedAt)
                }

                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Medication name") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.doseAmount,
                    onValueChange = viewModel::onDoseAmountChange,
                    // Short label so it does not clip when it floats up on focus. The
                    // example moved to placeholder + supportingText so the user still sees
                    // the format hint, but only when the field is empty / errored.
                    label = { Text("Dose amount") },
                    placeholder = { Text("e.g. 10 mg, 1 tablet, 0.5 mL") },
                    singleLine = true,
                    isError = state.doseAmountError != null,
                    supportingText =
                        state.doseAmountError?.let {
                            { Text(it) }
                        } ?: {
                            Text(
                                "Confirm with your vet. We will use this on every reminder " +
                                    "unless a phase overrides it.",
                            )
                        },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Storage, special instructions, side-effects to watch for…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    // Use onSurfaceVariant (the M3-correct secondary token) instead of
                    // onSurface.copy(alpha = 0.6f). Contrast is guaranteed by the audit.
                    text =
                        "Per-phase dose overrides land when you create the schedule below. " +
                            "This default is what we'll show on reminders unless a phase changes it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The Edit-mode topBar actions, extracted from [MedicationEditScreen] so the screen
 * function's cyclomatic complexity stays under the detekt cap. Visual hierarchy:
 *
 * - **Discontinue** (tertiary color, shown when the medication is active): soft, reversible
 *   action. Opens a confirmation dialog before stamping `discontinuedAt`.
 *
 * - **Reactivate** (primary color, shown when the medication is discontinued): positive
 *   action. Clears `discontinuedAt` immediately without a dialog, because there's nothing
 *   destructive to confirm and an extra confirmation step would feel like punishment for
 *   undoing.
 *
 * - **Delete** (error color, always shown in edit mode): destructive, irreversible. Opens
 *   the [DeleteMedicationDialog] for confirmation. Placed second so the user reaches the
 *   less-destructive action first.
 *
 * In new-medication mode this composable renders nothing, since there is no medication to
 * discontinue or delete; Back is the analogous cancel affordance.
 */
@Composable
private fun MedicationEditTopBarActions(
    isNew: Boolean,
    isDiscontinued: Boolean,
    onReactivateClick: () -> Unit,
    onDiscontinueClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    if (isNew) return
    if (isDiscontinued) {
        TextButton(
            onClick = onReactivateClick,
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Text("Reactivate")
        }
    } else {
        TextButton(
            onClick = onDiscontinueClick,
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary,
                ),
        ) {
            Text("Discontinue")
        }
    }
    TextButton(
        onClick = onDeleteClick,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        Text("Delete")
    }
}

/**
 * Read-only reference block so caregivers know which pet and medication they are editing
 * when arriving from Today (Edit) or any stacked route. Editable fields below may change
 * while typing; this card reflects loaded identity + schedule hints from the repositories.
 */
@Composable
private fun MedicationEditContextCard(
    petName: String?,
    medicationName: String?,
    doseAmount: String?,
    scheduleHint: String?,
    isNew: Boolean,
) {
    if (petName == null && medicationName == null && scheduleHint == null) return
    val medLine =
        when {
            medicationName != null && doseAmount != null -> "$medicationName · $doseAmount"
            medicationName != null -> medicationName
            isNew -> "New medication"
            else -> null
        }
    val a11y =
        listOfNotNull(
            petName?.let { "For $it" },
            medLine,
            scheduleHint,
        ).joinToString(". ")
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (a11y.isNotEmpty()) {
                        Modifier.semantics { contentDescription = a11y }
                    } else {
                        Modifier
                    },
                ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            petName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
            }
            medLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            scheduleHint?.let { hint ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Confirmation dialog for medication deletion. Sibling of `PetEditScreen.DeleteConfirmDialog`;
 * the copy is medication-specific so the user knows exactly what's about to disappear.
 *
 * v0.1 hard-deletes via the fake repo. M1 will soft-delete by setting `discontinuedAt` in
 * the SQLDelight repo, preserving dose-history rows; the UI does not change.
 */
@Composable
private fun DeleteMedicationDialog(
    medicationName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $medicationName?") },
        text = {
            Text(
                "This removes $medicationName along with its schedule and any dose history. " +
                    "This can't be undone.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Status banner shown at the top of the form when a medication is discontinued.
 *
 * Uses M3 `tertiaryContainer` background, semantically a "soft warning / informational"
 * tone, distinct from error (which is for blocking validation problems) and primary (which
 * is for positive actions). The container/onContainer color pair is contrast-audited by
 * Material 3 so we don't need to manually verify ratios.
 *
 * Accessibility: the inner `Column` wraps its children in `clearAndSetSemantics` with a
 * single TalkBack announcement, so screen readers don't read the date as a separate node
 * disconnected from the "discontinued" label. The discontinuedAt instant is formatted as
 * YYYY-MM-DD in the user's current TimeZone for sighted users; the announcement uses the
 * same wording so there's no skew between visual and spoken content.
 */
@Composable
private fun DiscontinuedBanner(discontinuedAt: Instant) {
    val date = discontinuedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val displayText = "Discontinued on $date"
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription =
                        "$displayText. Reminders are off. Tap Reactivate in the top bar to resume."
                },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Reminders are off. Tap Reactivate to resume.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Confirmation dialog for marking a medication as discontinued.
 *
 * Distinct from [DeleteMedicationDialog]: discontinue is a soft-delete (reversible, keeps
 * dose history). Delete is hard (irreversible). The copy makes the reversibility explicit
 * so users who want to "stop reminders without losing the dose log" don't accidentally
 * pick Delete.
 */
@Composable
private fun DiscontinueMedicationDialog(
    medicationName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discontinue $medicationName?") },
        text = {
            Text(
                "Reminders for $medicationName will stop and the medication will be hidden " +
                    "from Home and Pets. Its dose history is preserved. You can reactivate " +
                    "anytime from the medication's edit screen.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    ),
            ) {
                Text("Discontinue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
