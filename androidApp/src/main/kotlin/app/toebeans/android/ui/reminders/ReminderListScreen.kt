package app.toebeans.android.ui.reminders

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Reminder List screen — the management surface for configured schedules.
 *
 * Behavior:
 *   - Loading: a centered indeterminate spinner.
 *   - Empty: a friendly message explaining how to add reminders (the user must add a
 *     medication to a pet, then a schedule for that medication).
 *   - Populated: a LazyColumn of [ReminderRow] cards, grouped via the ViewModel's sort
 *     (pet name, then medication name).
 *
 * Tap handling is wired through [onScheduleClick]; until B7 Schedule Detail ships the
 * caller passes a no-op lambda. That keeps the row tappable (so the future wiring is a
 * one-line caller change) without exposing a broken navigation today.
 *
 * The LazyColumn is the load-bearing surface for the ADR-0008 list-scroll fps budget;
 * the future macrobench `ScrollBenchmark` will measure it directly.
 */
@Composable
public fun ReminderListScreen(
    onScheduleClick: (scheduleId: String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ReminderListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReminderListContent(
        state = state,
        onScheduleClick = onScheduleClick,
        contentPadding = contentPadding,
    )
}

/**
 * Stateless content. Separated from [ReminderListScreen] so previews and (future)
 * Compose UI tests can drive the screen without spinning up Koin.
 */
@Composable
internal fun ReminderListContent(
    state: ReminderListUiState,
    onScheduleClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    when {
        state.loading -> LoadingState(contentPadding)
        state.rows.isEmpty() -> EmptyState(contentPadding)
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(
                    items = state.rows,
                    // Stable keys keep LazyColumn from invalidating rows on every
                    // emission — load-bearing for the ADR-0008 scroll fps budget once
                    // the macrobench scroll test ships.
                    key = { row -> row.scheduleId },
                ) { row ->
                    ReminderRow(
                        row = row,
                        onClick = { onScheduleClick(row.scheduleId) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
    }
}

@Composable
private fun LoadingState(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(contentPadding: PaddingValues) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No reminders yet",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                "Add a medication to one of your pets, then set up a dose schedule. " +
                    "The schedule will appear here as a reminder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderRow(
    row: ReminderRowUi,
    onClick: () -> Unit,
) {
    // Compose detectTapGestures inside pointerInput gives us a tappable Card without
    // pulling in material3's experimental Card-with-onClick API. The Card itself stays
    // on the stable surface.
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.medium)
                .pointerInput(row.scheduleId) {
                    detectTapGestures(onTap = { onClick() })
                }.semantics {
                    contentDescription =
                        buildString {
                            append(row.petName)
                            append(", ")
                            append(row.medicationName)
                            append(", ")
                            append(row.phaseSummary)
                            row.endsLabel?.let {
                                append(", ")
                                append(it)
                            }
                        }
                },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = row.petName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = row.medicationName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.phaseSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.endsLabel?.let { label ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
