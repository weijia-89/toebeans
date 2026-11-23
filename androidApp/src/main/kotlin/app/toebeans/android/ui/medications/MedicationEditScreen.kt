package app.toebeans.android.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.PillBackground
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.formatDose
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
    onEditSchedule: (petId: String, medicationId: String, scheduleId: String) -> Unit = { _, _, _ -> },
    viewModel: MedicationEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscontinueDialog by remember { mutableStateOf(false) }
    var endMenuExpanded by remember { mutableStateOf(false) }

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
            // Opaque surface so the previous destination (Reminders FAB / list) does not
            // bleed through during enter/pop transitions. PillBackground stays in the Box
            // behind this Scaffold.
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(if (state.isNew) "Add medication" else "Edit medication") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    // All medication actions (End + Save) live in the bottom bar. The
                    // top-right overflow was a thumb-stretch and split the actions across
                    // two areas; see the D1 decision brief.
                )
            },
            bottomBar = {
                // D1: imePadding keeps the primary Save action above the on-screen keyboard
                // instead of letting the keyboard draw over it. "End" is the single trailing
                // home for Discontinue / Reactivate / Delete, so every medication action sits
                // in thumb reach and the destructive actions stay one deliberate tap away
                // from Save.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.imePadding(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!state.isNew) {
                            EndActionsMenu(
                                expanded = endMenuExpanded,
                                onExpand = { endMenuExpanded = true },
                                onDismiss = { endMenuExpanded = false },
                                isDiscontinued = state.isDiscontinued,
                                onReactivate = {
                                    scope.launch { viewModel.reactivate() }
                                },
                                onDiscontinue = { showDiscontinueDialog = true },
                                onDelete = { showDeleteDialog = true },
                            )
                        }
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (state.isNew) "Save medication" else "Save changes",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            },
        ) { inner ->
            // Opaque surface so predictive-back and transparent Scaffold do not show the
            // previous destination (Today dose list / Pet detail FAB) through the form.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
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
                        doseUnit = state.doseUnit,
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

                    MedicationNameSearchField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        repository = viewModel.medicationNameIndexRepository,
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.nameError != null,
                        supportingText = state.nameError?.let { { Text(it) } },
                        label = "Medication name",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.doseAmount,
                            onValueChange = viewModel::onDoseAmountChange,
                            label = { Text("Dose amount") },
                            placeholder = { Text("e.g. 10, 1, 0.5") },
                            singleLine = true,
                            isError = state.doseAmountError != null,
                            supportingText = state.doseAmountError?.let { { Text(it) } },
                            modifier = Modifier.weight(1f),
                        )
                        DoseUnitDropdown(
                            selected = state.doseUnit,
                            onSelect = viewModel::onDoseUnitChange,
                            isError = state.doseUnitError != null,
                            modifier = Modifier.width(140.dp),
                        )
                    }

                    DoseTimeSection(
                        state = state,
                        onDoseTimeChange = viewModel::onDoseTimeChange,
                        onUpdateTime = { scope.launch { viewModel.updateDoseTime() } },
                        onEditSchedule = onEditSchedule,
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
}

/**
 * Trailing "End" menu for the edit-mode bottom bar. Extracted so [MedicationEditScreen]'s
 * branch count stays under the detekt cap.
 *
 * - **Discontinue** (active meds) opens a confirmation dialog before stamping
 *   `discontinuedAt`. **Reactivate** (discontinued meds) clears `discontinuedAt`
 *   immediately — there is nothing destructive to confirm.
 * - **Delete** is destructive and irreversible, so it keeps its own confirmation dialog
 *   and stays last in the menu.
 *
 * In add mode the caller hides this composable entirely; there is no medication to end.
 */
@Composable
private fun EndActionsMenu(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    isDiscontinued: Boolean,
    onReactivate: () -> Unit,
    onDiscontinue: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        TextButton(onClick = onExpand) {
            Text("End")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            if (isDiscontinued) {
                DropdownMenuItem(
                    text = { Text("Reactivate") },
                    onClick = {
                        onDismiss()
                        onReactivate()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Discontinue") },
                    onClick = {
                        onDismiss()
                        onDiscontinue()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

/**
 * D2 dose-time surface. Hidden in add mode (no schedule exists yet) and when a medication
 * has no schedule. Exactly one phase with one time gets an editable "HH:MM" field; any
 * taper or multi-dose shape stays read-only and links to the schedule editor, which owns
 * the ascending-order / dose-count validation rules.
 */
@Composable
private fun DoseTimeSection(
    state: MedicationEditUiState,
    onDoseTimeChange: (String) -> Unit,
    onUpdateTime: () -> Unit,
    onEditSchedule: (petId: String, medicationId: String, scheduleId: String) -> Unit,
) {
    if (state.isNew || state.doseTimes.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.isSinglePhaseTimeEditable) {
            OutlinedTextField(
                value = state.doseTimeText,
                onValueChange = onDoseTimeChange,
                label = { Text("Dose time (24-hour HH:MM)") },
                singleLine = true,
                isError = state.doseTimeError != null,
                supportingText = state.doseTimeError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onUpdateTime) {
                Text("Update time")
            }
        } else {
            Text(
                text = "Dose times (edit in schedule)",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            state.doseTimes.forEach { time ->
                Text(
                    text = formatLocalTimeHint(time),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val scheduleId = state.scheduleId
            val medicationId = state.medicationId
            if (scheduleId != null && medicationId != null) {
                TextButton(
                    onClick = { onEditSchedule(state.petId ?: "", medicationId, scheduleId) },
                ) {
                    Text("Edit schedule")
                }
            }
        }
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
    doseUnit: DoseUnit?,
    scheduleHint: String?,
    isNew: Boolean,
) {
    if (petName == null && medicationName == null && scheduleHint == null) return
    val medLine = buildMedLine(medicationName, doseAmount, doseUnit, isNew)
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

private fun buildMedLine(
    medicationName: String?,
    doseAmount: String?,
    doseUnit: DoseUnit?,
    isNew: Boolean,
): String? {
    val doseDisplay = doseAmount?.let { formatDose(it, doseUnit) }
    return when {
        medicationName != null && doseDisplay != null -> "$medicationName · $doseDisplay"
        medicationName != null -> medicationName
        isNew -> "New medication"
        else -> null
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
                        "$displayText. Reminders are off. Open End in the bottom bar and tap Reactivate to resume."
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

/**
 * Dropdown picker for [DoseUnit]. Uses ExposedDropdownMenuBox for Material 3 compliance.
 * Shows the unit label ("mg", "tablet", etc.) in the collapsed field and a scrollable
 * list of [allowedUnits] when expanded. The default keeps the full 17-value list for
 * every existing call site; the medication form passes a narrowed, ordered set when
 * an approved entry exists and offers a separate override back to the full list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DoseUnitDropdown(
    selected: DoseUnit?,
    onSelect: (DoseUnit?) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    allowedUnits: List<DoseUnit> = DoseUnit.entries.toList(),
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit") },
            isError = isError,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse unit picker" else "Expand unit picker",
                )
            },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            allowedUnits.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}
