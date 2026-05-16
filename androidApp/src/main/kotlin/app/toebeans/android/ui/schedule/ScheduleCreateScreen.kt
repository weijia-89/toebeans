package app.toebeans.android.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.DatePickerField
import app.toebeans.android.ui.components.PillBackground
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ScheduleCreateScreen(
    // petId is part of the nav-graph route. The screen receives it for routing
    // identity and consistency with sibling screens; the current schedule-
    // creation flow keys off medicationId alone. When pet-scoped validation
    // lands (verifying the medication actually belongs to this pet before
    // saving), petId will be read here. Until then, suppress the warning
    // rather than drop it from the signature.
    @Suppress("UnusedParameter") petId: String,
    medicationId: String,
    onBack: () -> Unit,
    onSaved: (scheduleId: String) -> Unit,
    viewModel: ScheduleCreateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(medicationId) {
        viewModel.setMedicationId(medicationId)
        viewModel.loadMedication()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative pill silhouette behind the form. Low-alpha; does not interfere with
        // foreground contrast. See PillBackground.kt for the WCAG audit note.
        PillBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            // fillMaxSize pins the Scaffold (and especially its bottomBar) to the parent
            // Box bounds. Without this the Scaffold wraps content and the bottomBar floats
            // up against the form content.
            modifier = Modifier.fillMaxSize(),
            // Surface set transparent so the PillBackground shows through.
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Create schedule")
                            state.medication?.let { med ->
                                Text(
                                    text = "${med.name} · ${med.doseAmount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    // onSurfaceVariant — M3-correct secondary token,
                                    // contrast-audited in Color.kt.
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    // Save moved out of the top bar — top-right is a thumb-stretch.
                )
            },
            bottomBar = {
                // Full-width primary Save button at the bottom, reachable with one thumb.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Button(
                        enabled = state.startDate != null && state.phases.isNotEmpty(),
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
                        Text(
                            "Save schedule",
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
                Text("Schedule window", style = MaterialTheme.typography.titleMedium)

                DatePickerField(
                    label = "Start date",
                    value = state.startDate,
                    onValueChange = viewModel::onStartDateChange,
                    supportingText = state.startDateError,
                )
                DatePickerField(
                    label = "End date (optional, leave blank for open-ended)",
                    value = state.endDate,
                    onValueChange = viewModel::onEndDateChange,
                    allowClear = true,
                )

                Text("Phases", style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        "Phase 1 starts on the schedule's start date. Subsequent phases run back-to-back " +
                            "(e.g. a 7-day full dose followed by a 5-day taper).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.phases.forEachIndexed { idx, draft ->
                    PhaseEditorCard(
                        index = idx,
                        draft = draft,
                        isOnlyPhase = state.phases.size == 1,
                        onChange = { updated -> viewModel.updatePhase(idx) { updated } },
                        onRemove = { viewModel.removePhase(idx) },
                    )
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
