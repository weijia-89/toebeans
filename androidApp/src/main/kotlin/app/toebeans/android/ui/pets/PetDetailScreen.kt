package app.toebeans.android.ui.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.android.ui.components.PetAvatar
import app.toebeans.android.ui.components.PetAvatarSizeHero
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.PetAgeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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
        // FAB only when the list is non-empty. Empty state has its own primary CTA, so
        // we avoid double-CTA confusion (and an awkward FAB hovering over an EmptyState
        // illustration). When the FAB IS shown, Scaffold handles navigation-bar inset
        // padding automatically via its default contentWindowInsets.
        floatingActionButton = {
            if (state.pet != null && state.medications.isNotEmpty()) {
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
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp)
                                .semantics { heading() },
                    )
                    if (state.medications.isEmpty()) {
                        // Single primary CTA. FAB is suppressed (see above).
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

/**
 * Header card showing the pet's identity at a glance. Layout: large species emoji in a
 * circular tinted badge on the left, name + facts stack on the right.
 *
 * Two-line fact layout (intentional):
 *   1. Species · Weight   — slow-changing factual data
 *   2. Age string         — relational data ("4 years old") computed from birthdate
 *
 * The split lets the eye land on the age first when scanning. Weight typically lives in
 * line 1 because vet calls often start with "what's the weight" — keeping it close to
 * the species reduces hunt-time.
 *
 * If birthdate is null we omit the age line entirely rather than rendering an empty row.
 */
@Composable
private fun PetIdentityCard(
    pet: Pet,
    modifier: Modifier = Modifier,
) {
    // Compute "today" once per recomposition driven by the pet object. Using
    // remember(pet.id) means the age string only re-derives when we swap pets, not on
    // every theme/config recomp. The day-rollover at midnight isn't an issue here —
    // the user will re-open the screen long before a 1-day age boundary matters.
    val today = remember(pet.id) { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val ageString = pet.birthdate?.let { PetAgeFormatter.format(it, today) }
    val speciesLabel =
        pet.species.name
            .lowercase()
            .replaceFirstChar(Char::titlecase)
    val weightLabel = pet.weightKg?.let { "%.1f kg".format(it) }
    val factsLine1 = listOfNotNull(speciesLabel, weightLabel).joinToString(" · ")

    // Build the merged accessibility label outside the Composable structure. TalkBack
    // reads this as one prosodic unit instead of the 4 separate Text nodes inside the
    // card (name + facts + age + notes). "kg" expanded to "kilograms" because
    // TalkBack's abbreviation dictionary doesn't always do it.
    val accessibleLabel =
        buildString {
            append(pet.name)
            append(", ")
            append(speciesLabel)
            weightLabel?.let {
                append(", ")
                append(it.replace(" kg", " kilograms"))
            }
            ageString?.let {
                append(", ")
                append(it)
            }
            if (!pet.notes.isNullOrBlank()) {
                append(". Notes: ")
                append(pet.notes)
            }
        }
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleLabel
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PetAvatar(species = pet.species, size = PetAvatarSizeHero)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = pet.name, style = MaterialTheme.typography.headlineSmall)
                if (factsLine1.isNotEmpty()) {
                    Text(
                        text = factsLine1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (ageString != null) {
                    Text(
                        text = ageString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (!pet.notes.isNullOrBlank()) {
                    Text(
                        text = pet.notes!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
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
