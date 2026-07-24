package app.toebeans.android.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.DatePickerField
import app.toebeans.android.ui.components.PillBackground
import app.toebeans.android.ui.schedule.PhaseEditorCard
import app.toebeans.core.model.AnchorMode
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun UnifiedMedicationScreen(
    petId: String,
    onBack: () -> Unit,
    onSaved: (scheduleId: String) -> Unit,
    viewModel: UnifiedMedicationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(petId) {
        viewModel.setPetId(petId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PillBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text("Add medication") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Button(
                        enabled =
                            !state.isSaving &&
                                state.name.isNotBlank() &&
                                state.doseAmount.isNotBlank() &&
                                state.doseUnit != null,
                        onClick = {
                            scope.launch {
                                val id = viewModel.save()
                                if (id != null) onSaved(id)
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
                        Text("Save medication", style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
        ) { inner ->
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
                    // Form-level error banner
                    state.formError?.let { msg ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clearAndSetSemantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = "Error: $msg"
                                    },
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // Medication section
                    Text("Medication", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MedicationNameSearchField(
                                value = state.name,
                                onValueChange = viewModel::onNameChange,
                                repository = viewModel.medicationNameIndex,
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
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedTextField(
                                value = state.notes,
                                onValueChange = viewModel::onNotesChange,
                                label = { Text("Notes (optional)") },
                                placeholder = { Text("Storage, special instructions...") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Schedule window section
                    Text("Schedule", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DatePickerField(
                                label = "Start date",
                                value = state.startDate,
                                onValueChange = viewModel::onStartDateChange,
                                supportingText = state.startDateError,
                            )
                            DatePickerField(
                                label = "End date (optional)",
                                value = state.endDate,
                                onValueChange = viewModel::onEndDateChange,
                                allowClear = true,
                            )
                            Text(
                                text = "Scheduling mode",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Column(Modifier.selectableGroup()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = state.anchorMode == AnchorMode.FOLLOW_PHONE,
                                            onClick = { viewModel.onAnchorModeChange(AnchorMode.FOLLOW_PHONE) },
                                        ).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = state.anchorMode == AnchorMode.FOLLOW_PHONE,
                                        onClick = null,
                                    )
                                    Spacer(Modifier.padding(horizontal = 8.dp))
                                    Column {
                                        Text("Normal scheduling", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "Dose times follow your phone's clock (default)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = state.anchorMode == AnchorMode.ELAPSED_INTERVAL,
                                            onClick = { viewModel.onAnchorModeChange(AnchorMode.ELAPSED_INTERVAL) },
                                        ).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = state.anchorMode == AnchorMode.ELAPSED_INTERVAL,
                                        onClick = null,
                                    )
                                    Spacer(Modifier.padding(horizontal = 8.dp))
                                    Column {
                                        Text(
                                            "Time-sensitive: keep interval constant",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "For insulin, anti-seizure, and other narrow-therapeutic-window drugs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Phases section
                    Text("Phases", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Phase 1 starts on the schedule's start date. Subsequent phases run back-to-back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    state.phases.forEachIndexed { idx, draft ->
                        key(idx) {
                            PhaseEditorCard(
                                index = idx,
                                draft = draft,
                                isOnlyPhase = state.phases.size == 1,
                                onChange = { updated -> viewModel.updatePhase(idx) { updated } },
                                onRemove = { viewModel.removePhase(idx) },
                                onAffirmNightDose = { viewModel.affirmNightDose(idx) },
                                onDismissMidnightStraddle = { viewModel.dismissMidnightStraddle(idx) },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::addPhase,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add phase")
                    }
                }
            }
        }
    }
}
