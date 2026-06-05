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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import app.toebeans.android.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

/**
 * Reminder List screen — the management surface for configured schedules.
 *
 * Behavior:
 *   - Loading: a centered indeterminate spinner.
 *   - Empty: [EmptyState] with a primary CTA that starts the pet → medication → schedule chain.
 *   - Populated: a LazyColumn of [ReminderRow] cards plus an FAB for the same add path.
 *
 * Tap handling is wired through [onScheduleClick] to Schedule Detail (B7).
 *
 * The LazyColumn is the load-bearing surface for the ADR-0008 list-scroll fps budget;
 * the future macrobench `ScrollBenchmark` will measure it directly.
 */
@Composable
public fun ReminderListScreen(
    onScheduleClick: (scheduleId: String) -> Unit,
    onNeedsScheduleClick: (petId: String, medicationId: String) -> Unit,
    onAddReminder: (ReminderAddAction) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ReminderListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReminderListContent(
        state = state,
        onScheduleClick = onScheduleClick,
        onNeedsScheduleClick = onNeedsScheduleClick,
        onAddReminder = onAddReminder,
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
    onNeedsScheduleClick: (petId: String, medicationId: String) -> Unit,
    onAddReminder: (ReminderAddAction) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        when {
            state.loading -> LoadingState()
            state.rows.isEmpty() -> RemindersEmptyState(addAction = state.addAction, onAddReminder = onAddReminder)
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(
                        items = state.rows,
                        key = { row -> row.rowKey },
                    ) { row ->
                        ReminderRow(
                            row = row,
                            onClick = {
                                if (row.needsSchedule) {
                                    onNeedsScheduleClick(row.petId, row.medicationId)
                                } else {
                                    onScheduleClick(row.scheduleId!!)
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
        }

        if (!state.loading && state.addAction != null) {
            ExtendedFloatingActionButton(
                onClick = { onAddReminder(state.addAction) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(state.addAction.buttonLabel()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RemindersEmptyState(
    addAction: ReminderAddAction?,
    onAddReminder: (ReminderAddAction) -> Unit,
) {
    val label = addAction?.buttonLabel() ?: "Add schedule"
    EmptyState(
        emoji = "⏰",
        title = "No reminders yet",
        body =
            "Set up a dose schedule for one of your pets. " +
                "You'll see it here and on Today when a dose is due.",
        primaryActionLabel = label,
        onPrimaryAction = addAction?.let { { onAddReminder(it) } },
    )
}

@Composable
private fun ReminderRow(
    row: ReminderRowUi,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.medium)
                .pointerInput(row.rowKey) {
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
