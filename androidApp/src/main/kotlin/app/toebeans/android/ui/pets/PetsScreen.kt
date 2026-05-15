package app.toebeans.android.ui.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.android.ui.theme.ToebeansTheme
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import org.koin.androidx.compose.koinViewModel

@Composable
public fun PetsScreen(
    onPetClick: (petId: String) -> Unit,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: PetsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PetsScreenContent(
        state = state,
        onPetClick = onPetClick,
        onAddPet = onAddPet,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun PetsScreenContent(
    state: PetsUiState,
    onPetClick: (petId: String) -> Unit,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        if (state.pets.isEmpty() && !state.loading) {
            EmptyState(
                title = "No pets yet",
                body = "Add Rufus, Luna, or whoever you share toe beans with to get started.",
                primaryActionLabel = "Add pet",
                onPrimaryAction = onAddPet,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.pets, key = { it.id }) { pet ->
                    PetRow(pet = pet, onClick = { onPetClick(pet.id) })
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAddPet,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add pet") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun PetRow(
    pet: Pet,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = pet.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${pet.species.name.lowercase().replaceFirstChar(Char::titlecase)} · ${"%.1f".format(pet.weightKg)} kg",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PetsScreenPreview() {
    ToebeansTheme(dynamic = false) {
        PetsScreenContent(
            state =
                PetsUiState(
                    pets =
                        listOf(
                            Pet(
                                id = "pet-1",
                                name = "Rufus",
                                species = Species.DOG,
                                birthdate = null,
                                weightKg = 12.4,
                                notes = null,
                                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                                archivedAt = null,
                            ),
                            Pet(
                                id = "pet-2",
                                name = "Luna",
                                species = Species.CAT,
                                birthdate = null,
                                weightKg = 4.1,
                                notes = null,
                                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                                archivedAt = null,
                            ),
                        ),
                    loading = false,
                ),
            onPetClick = {},
            onAddPet = {},
        )
    }
}
