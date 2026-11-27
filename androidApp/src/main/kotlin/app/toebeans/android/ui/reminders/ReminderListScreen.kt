package app.toebeans.android.ui.reminders

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.toebeans.core.scheduler.DstWarning
import kotlinx.datetime.LocalTime
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
    listState: LazyListState? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fallbackListState = rememberLazyListState()
    ReminderListContent(
        state = state,
        onScheduleClick = onScheduleClick,
        onNeedsScheduleClick = onNeedsScheduleClick,
        onAddReminder = onAddReminder,
        contentPadding = contentPadding,
        listState = listState ?: fallbackListState,
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
    listState: LazyListState? = null,
) {
    // D4: expand/collapse state lives in the screen; the toggle itself is a pure helper
    // on the ViewModel companion so the expansion contract is unit-testable.
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    val fallbackListState = rememberLazyListState()
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
                    state = listState ?: fallbackListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(
                        items = state.groups,
                        key = { group -> group.groupKey },
                    ) { group ->
                        val expanded = group.key in expandedGroups
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupHeader(
                                group = group,
                                expanded = expanded,
                                onToggle = {
                                    expandedGroups = ReminderListViewModel.toggleGroup(expandedGroups, group.key)
                                },
                            )
                            if (expanded) {
                                group.rows.forEach { row ->
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
                            }
                        }
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

/**
 * D4 group header. The name sits on the start, the expand/collapse chevron is a trailing
 * IconButton in thumb reach; nothing interactive is centered. Tapping a chevron reveals
 * one section per pet, and each section keeps its own schedule tap target.
 */
@Composable
private fun GroupHeader(
    group: ReminderGroupUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.medium)
                .semantics {
                    contentDescription =
                        "${group.displayName}, ${group.rows.size} pet(s), " +
                        if (expanded) "expanded" else "collapsed"
                },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.rows.size} pet${if (group.rows.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector =
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription =
                        if (expanded) "Collapse ${group.displayName}" else "Expand ${group.displayName}",
                )
            }
        }
    }
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
                            row.dstWarning?.let {
                                append(", ")
                                append(it.warningText())
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
            if (row.doseTimesLocal.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.doseTimesLocal.joinToString(", ") { formatDoseTime(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.endsLabel?.let { label ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.dstWarning?.let { warning ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = warning.warningText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 12-hour clock label for the D4 expanded-section dose times. */
private fun formatDoseTime(time: LocalTime): String {
    val hour24 = time.hour
    val hour12 = ((hour24 + 11) % 12) + 1
    val minute = time.minute.toString().padStart(2, '0')
    return "$hour12:$minute ${if (hour24 < 12) "AM" else "PM"}"
}

private fun DstWarning.warningText(): String =
    when (this) {
        DstWarning.DST_SKIP -> "DST spring-forward: one dose shifted forward"
        DstWarning.DST_DUPLICATE_RESOLVED -> "DST fall-back: duplicate dose resolved"
    }
