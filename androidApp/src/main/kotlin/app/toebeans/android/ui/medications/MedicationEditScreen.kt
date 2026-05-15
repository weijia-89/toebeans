package app.toebeans.android.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    LaunchedEffect(petId, medicationId) {
        viewModel.setPetId(petId)
        if (medicationId != null) viewModel.load(medicationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add medication" else "Edit medication") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = state.name.isNotBlank() && state.doseAmount.isNotBlank(),
                        onClick = {
                            scope.launch {
                                if (viewModel.save()) onSaved()
                            }
                        },
                    ) { Text("Save") }
                },
            )
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
                label = { Text("Medication name") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
            )

            OutlinedTextField(
                value = state.doseAmount,
                onValueChange = viewModel::onDoseAmountChange,
                label = { Text("Default dose amount (e.g. 10 mg, 1 tablet)") },
                singleLine = true,
                isError = state.doseAmountError != null,
                supportingText = state.doseAmountError?.let { { Text(it) } },
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                minLines = 3,
            )

            Text(
                text =
                    "Per-phase dose overrides land when you create the schedule. The default " +
                        "amount here is what we show alongside reminders unless a phase overrides it.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color =
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        .copy(alpha = 0.6f),
            )
        }
    }
}
