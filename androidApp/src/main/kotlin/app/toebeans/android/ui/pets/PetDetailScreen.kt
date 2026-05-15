package app.toebeans.android.ui.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import org.koin.androidx.compose.koinViewModel

/**
 * Pet detail. Shows the pet's identity card and a LazyColumn of medications. Tapping a
 * medication navigates to its edit screen; the Add FAB opens a new-medication form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PetDetailScreen(
    petId: String,
    onBack: () -> Unit,
    onEditPet: () -> Unit,
    onAddMedication: () -> Unit,
    onMedicationClick: (medicationId: String) -> Unit,
    viewModel: PetDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(petId) { viewModel.load(petId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.pet?.name ?: "Pet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditPet, enabled = state.pet != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit pet")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.pet != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddMedication,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add medication") },
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            val pet = state.pet
            if (pet == null) {
                if (!state.loading) {
                    EmptyState(title = "Pet not found", body = "It may have been deleted.")
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PetIdentityCard(pet, Modifier.padding(16.dp))
                    Text(
                        text = "Medications",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (state.medications.isEmpty()) {
                        EmptyState(
                            title = "No medications yet",
                            body = "Add a medication to start scheduling doses.",
                            primaryActionLabel = "Add medication",
                            onPrimaryAction = onAddMedication,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.medications, key = { it.id }) { med ->
                                MedicationRow(med, onClick = { onMedicationClick(med.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetIdentityCard(
    pet: Pet,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = pet.name, style = MaterialTheme.typography.titleLarge)
            val species =
                pet.species.name
                    .lowercase()
                    .replaceFirstChar(Char::titlecase)
            val weight = pet.weightKg?.let { "%.1f kg".format(it) }
            val dob = pet.birthdate?.let { "Born $it" }
            Text(
                text = listOfNotNull(species, weight, dob).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            if (!pet.notes.isNullOrBlank()) {
                Text(text = pet.notes!!, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MedicationRow(
    medication: Medication,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = medication.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = medication.doseAmount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
