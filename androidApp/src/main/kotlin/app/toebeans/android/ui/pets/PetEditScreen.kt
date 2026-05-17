package app.toebeans.android.ui.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.DatePickerField
import app.toebeans.core.model.Species
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Add or edit a Pet. If [petId] is null, this is a new-pet form. Otherwise the VM loads the
 * pet and pre-fills the form. Save is enabled only when the name is non-blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PetEditScreen(
    petId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PetEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(petId) {
        if (petId != null) viewModel.load(petId)
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            // Use the loaded name; falls back to "this pet" if the load coroutine is
            // still resolving (defensive — the dialog can't open until the edit screen
            // has been on for a frame).
            petName = state.name.ifBlank { "this pet" },
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    if (viewModel.delete()) onSaved()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add pet" else "Edit pet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Delete only when editing an existing pet. A new-pet form has nothing
                    // to delete; the Back button is the analogous cancel affordance.
                    if (!state.isNew) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            Text("Delete")
                        }
                    }
                },
                // Save moved out of the top bar — top-right is a thumb-stretch on most
                // phones. New primary Save lives in bottomBar below.
            )
        },
        bottomBar = {
            // Full-width primary Save button at the bottom, reachable with one thumb.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Button(
                    enabled = state.name.isNotBlank(),
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
                        if (state.isNew) "Save pet" else "Save changes",
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxSize().padding(0.dp),
            )

            SpeciesDropdown(
                value = state.species,
                onValueChange = viewModel::onSpeciesChange,
            )

            DatePickerField(
                label = "Birthdate (optional)",
                value = state.birthdate,
                onValueChange = viewModel::onBirthdateChange,
                allowClear = true,
            )

            OutlinedTextField(
                value = state.weightKgText,
                onValueChange = viewModel::onWeightChange,
                label = { Text("Weight (kg, optional)") },
                singleLine = true,
                isError = state.weightError != null,
                supportingText = state.weightError?.let { { Text(it) } },
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                minLines = 3,
            )
        }
    }
}

/**
 * Confirmation dialog for pet deletion. M3 [AlertDialog] with destructive-styled confirm
 * button (error-color text) and a neutral cancel.
 *
 * Why a confirmation dialog and not an undo snackbar: a pet carries medications, schedules,
 * and dose-event history; an accidental delete is destructive enough to warrant explicit
 * confirmation. Undo-snackbar UX is the right pattern for low-stakes (e.g. archive a logged
 * dose); high-stakes deletes get a modal.
 */
@Composable
private fun DeleteConfirmDialog(
    petName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $petName?") },
        text = {
            Text(
                "This removes $petName and any medications, schedules, and dose history " +
                    "attached to them. This can't be undone.",
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
 * Species selector using a horizontal row of RadioButtons. Material 3 dropdowns
 * (ExposedDropdownMenuBox) have a churning API across 1.4–1.7 — radio buttons are simpler
 * and at v1 we only have two species. Switches to a dropdown if the enum grows past ~4
 * entries.
 */
@Composable
private fun SpeciesDropdown(
    value: Species,
    onValueChange: (Species) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Species",
            style = MaterialTheme.typography.labelLarge,
            // onSurfaceVariant: M3-correct secondary token, contrast-audited in Color.kt.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Species.entries.forEach { species ->
                Row(
                    modifier =
                        Modifier
                            .selectable(
                                selected = species == value,
                                onClick = { onValueChange(species) },
                            ).padding(end = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = species == value,
                        onClick = { onValueChange(species) },
                    )
                    Text(
                        text = species.name.lowercase().replaceFirstChar(Char::titlecase),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
