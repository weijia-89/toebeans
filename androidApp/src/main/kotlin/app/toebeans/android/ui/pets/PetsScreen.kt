package app.toebeans.android.ui.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.android.ui.components.PetAvatar
import app.toebeans.android.ui.components.PetAvatarSizeList
import app.toebeans.android.ui.theme.ToebeansTheme
import app.toebeans.core.model.Pet
import app.toebeans.core.model.PetAgeFormatter
import app.toebeans.core.model.Species
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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
                emoji = "🐾",
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
                    PetRow(
                        pet = pet,
                        medCount = state.medCountByPetId[pet.id] ?: 0,
                        onClick = { onPetClick(pet.id) },
                    )
                }
            }
            // FAB uses the primary terracotta + onPrimary cream pairing so it sits
            // confidently in the warm palette. Default FAB colors fall back to
            // primaryContainer (the dusty rose) which read as off-brand against the
            // cream surface — too washed-out for a primary CTA.
            ExtendedFloatingActionButton(
                onClick = onAddPet,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add pet") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/**
 * Compact pet row for the list. Layout mirrors the detail-screen header card but at
 * smaller sizes:
 *   [48dp avatar]   Name (titleMedium)
 *                   Species · Weight  ·  Age
 *
 * The facts line uses a single `·`-joined string instead of two lines because the row
 * is meant for scanning, not lingering — the user is here to find the right pet, not
 * to read every detail. The detail screen does the two-line treatment.
 *
 * `today` is read inside the composable rather than passed in. For the list this is
 * fine: a recomposition every time we re-enter the screen re-reads the clock, which
 * is the correct frequency for a "how old is my pet" UI.
 */
@Composable
private fun PetRow(
    pet: Pet,
    medCount: Int,
    onClick: () -> Unit,
) {
    val today: LocalDate =
        remember(pet.id) { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val species =
        pet.species.name
            .lowercase()
            .replaceFirstChar(Char::titlecase)
    val weight = pet.weightKg?.let { "%.1f kg".format(it) }
    val age = pet.birthdate?.let { PetAgeFormatter.format(it, today) }
    // Med-count fact appears only when > 0 — a pet with no meds yet shouldn't have
    // a hollow "0 meds" string crowding the facts row. Hand-formatted plural because
    // v0.1 is English-only and the Plurals.xml plumbing isn't worth it yet.
    val medCountLabel =
        when (medCount) {
            0 -> null
            1 -> "1 med"
            else -> "$medCount meds"
        }
    val facts = listOfNotNull(species, weight, age, medCountLabel).joinToString(" · ")

    // Merged semantics: TalkBack announces the row as one node ("Luna, cat, 4.1
    // kilograms, 3 years old") instead of three separate text nodes plus a decorative
    // avatar. The facts string already contains the readable concat; we replace " · "
    // with ", " for natural prosody and "kg" with "kilograms" because TalkBack's
    // built-in dictionary doesn't always expand the abbreviation.
    val accessibleLabel =
        buildString {
            append(pet.name)
            if (facts.isNotEmpty()) {
                append(", ")
                append(facts.replace(" · ", ", ").replace(" kg", " kilograms"))
            }
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleLabel
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PetAvatar(species = pet.species, size = PetAvatarSizeList)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = pet.name, style = MaterialTheme.typography.titleMedium)
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                    medCountByPetId = mapOf("pet-2" to 1),
                    loading = false,
                ),
            onPetClick = {},
            onAddPet = {},
        )
    }
}
