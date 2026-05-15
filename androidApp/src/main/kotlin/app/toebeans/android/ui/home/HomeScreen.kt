package app.toebeans.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import app.toebeans.android.ui.components.PetAvatar
import app.toebeans.android.ui.components.PetAvatarSizeCompact
import app.toebeans.android.ui.theme.ToebeansTheme
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import org.koin.androidx.compose.koinViewModel

/**
 * Home / Today screen. Shows the user's tracked pets as tappable chips and a placeholder
 * for "today's doses" until SQLDelight + DoseEventRepository are wired in the next
 * milestone.
 *
 * Three states:
 *   - Loading: nothing (the parent suppresses recompositions while loading).
 *   - No pets:  one-CTA empty state inviting the user to add their first pet.
 *   - Has pets: Today header → "Your pets" tappable row → "Today's doses" placeholder.
 *
 * The pet chips are the only tappable surface on this screen so far. They route to the
 * pet's detail (which is also reachable from the Pets tab) — Today is a fast-path home
 * for users who think "oh I need to check on Rufus" rather than "let me browse the
 * pets list." Same destination, two paths.
 */
@Composable
public fun HomeScreen(
    onAddPet: () -> Unit,
    onPetClick: (petId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        onAddPet = onAddPet,
        onPetClick = onPetClick,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onAddPet: () -> Unit,
    onPetClick: (petId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (state.pets.isEmpty() && !state.loading) {
        EmptyState(
            title = "No pets yet",
            // The original "No doses scheduled" framing was confusing when the user
            // had no pets at all — it implied dose-scheduling was the missing step
            // when really the missing step was pet entry. Now the empty-state CTA
            // matches the actual blocking action.
            body = "Add Rufus, Luna, or whoever you share toe beans with to get started.",
            primaryActionLabel = "Add pet",
            onPrimaryAction = onAddPet,
            modifier = modifier.padding(contentPadding),
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Today", style = MaterialTheme.typography.titleLarge)

        // Pet quick-tap row. Each chip is a Card with avatar + name; tapping routes to
        // pet detail. LazyRow gives us horizontal scrolling for users with many pets
        // (the 4-pet household exists) without breaking the vertical scroll model of
        // the parent Column.
        Text(text = "Your pets", style = MaterialTheme.typography.titleMedium)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // 4dp vertical contentPadding leaves room for the Card's elevation shadow
            // to render without clipping at the top/bottom of the LazyRow.
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(items = state.pets, key = { it.id }) { pet ->
                PetChip(pet = pet, onClick = { onPetClick(pet.id) })
            }
        }

        Text(text = "Today's doses", style = MaterialTheme.typography.titleMedium)
        // Placeholder until the dose-event store is wired. Friendlier than the previous
        // copy which name-dropped "milestone 1", "SQLDelight", and "DoseEventRepository"
        // — internal implementation jargon that meant nothing to a stressed pet owner.
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "No doses scheduled yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        "Once you set up a schedule for a medication, upcoming doses will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Compact, tappable pet card for the Today screen's pet row. Horizontal layout —
 * 40dp avatar + name — gives roughly two chips visible at once on a typical phone width
 * with a third peeking, signaling horizontal scrollability.
 */
@Composable
private fun PetChip(
    pet: Pet,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PetAvatar(species = pet.species, size = PetAvatarSizeCompact)
            Text(
                text = pet.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    ToebeansTheme(dynamic = false) {
        HomeScreenContent(
            state = HomeUiState(pets = emptyList()),
            onAddPet = {},
            onPetClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPopulatedPreview() {
    ToebeansTheme(dynamic = false) {
        HomeScreenContent(
            state =
                HomeUiState(
                    pets =
                        listOf(
                            Pet(
                                id = "pet-1",
                                name = "Rufus",
                                species = Species.DOG,
                                birthdate = null,
                                weightKg = 12.0,
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
                ),
            onAddPet = {},
            onPetClick = {},
        )
    }
}
