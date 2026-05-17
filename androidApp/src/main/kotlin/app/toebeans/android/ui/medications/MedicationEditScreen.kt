package app.toebeans.android.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import app.toebeans.android.ui.components.PillBackground
import kotlinx.coroutines.launch
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

    LaunchedEffect(petId, medicationId) {
        viewModel.setPetId(petId)
        if (medicationId != null) viewModel.load(medicationId)
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
                        // Delete only when editing an existing medication. A new-med form has
                        // nothing to delete; Back is the analogous cancel affordance.
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
