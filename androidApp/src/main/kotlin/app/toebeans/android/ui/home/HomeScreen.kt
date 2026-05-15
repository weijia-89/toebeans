package app.toebeans.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.android.ui.theme.ToebeansTheme
import org.koin.androidx.compose.koinViewModel

@Composable
public fun HomeScreen(
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        onAddPet = onAddPet,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (state.pets.isEmpty() && !state.loading) {
        EmptyState(
            title = "No doses scheduled",
            body = "Add a pet and a medication to see today's doses here.",
            primaryActionLabel = "Add pet",
            onPrimaryAction = onAddPet,
            modifier = modifier.padding(contentPadding),
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "${state.pets.size} pet${if (state.pets.size == 1) "" else "s"} on file.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Dose-list view lands in milestone 1 once SQLDelight + DoseEventRepository are wired.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    ToebeansTheme(dynamic = false) {
        HomeScreenContent(state = HomeUiState(pets = emptyList()), onAddPet = {})
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
                            app.toebeans.core.model.Pet(
                                id = "pet-1",
                                name = "Rufus",
                                species = app.toebeans.core.model.Species.DOG,
                                birthdate = null,
                                weightKg = 12.0,
                                notes = null,
                                createdAt = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z"),
                                archivedAt = null,
                            ),
                        ),
                ),
            onAddPet = {},
        )
    }
}
