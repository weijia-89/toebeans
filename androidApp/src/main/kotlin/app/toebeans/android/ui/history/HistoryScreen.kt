package app.toebeans.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

/**
 * History tab (ADR-0011 UI slice). Read-only, newest-first log of dose events across
 * every pet for the last 30 days. Answers "what actually happened with the meds?" —
 * the surface the ADR-0011 denial banner and the ADR-0012 boot summary both point at.
 *
 * **Strictly read-only.** No edit, delete, or re-log affordance exists anywhere on this
 * screen: rows are not clickable, carry no [androidx.compose.ui.semantics.Role.Button],
 * and the ViewModel exposes no write path. Per the spec, the retrospective job here is
 * answering questions, not acting.
 *
 * States:
 *   - Loading: centered spinner with a content description so TalkBack reaches it.
 *   - Empty: [EmptyState] pointing the user back at the Today surface.
 *   - Populated: one [HistoryRow] per dose event; stale rows (unresolvable pet or
 *     medication) were already skipped by the ViewModel per the [app.toebeans.android.util.StaleEventGuard]
 *     contract, so this screen never renders a partial row.
 *
 * Accessibility: each row merges its descendants into one TalkBack node via
 * `semantics(mergeDescendants = true) { contentDescription = ... }`. That is NOT
 * `clearAndSetSemantics` (which removes children from the tree and made the B9 affirm
 * button unreachable tonight); merged nodes keep every child reachable.
 */
@Composable
public fun HistoryScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreenContent(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun HistoryScreenContent(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        when {
            state.loading -> LoadingState()
            state.rows.isEmpty() ->
                EmptyState(
                    emoji = "🕰️",
                    title = "No dose history yet",
                    body = "Log a dose from Today and it will show up here.",
                    modifier = Modifier.fillMaxSize(),
                )
            else -> HistoryList(state = state)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier.semantics {
                    contentDescription = "Loading dose history"
                },
        )
    }
}

@Composable
private fun HistoryList(state: HistoryUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "history_header") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // heading() semantic lets TalkBack's heading-rotor jump to this screen's
                // top-level sections, matching the Today/Reminders header pattern.
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Last 30 days, newest first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = state.rows, key = { it.id }) { row ->
            HistoryRow(row = row)
        }
    }
}

/**
 * One dose-event row. Layout matches the Home dose-row vocabulary: title line
 * (pet · medication), muted detail line (scheduled slot), status chip on the trailing
 * edge, optional fired line in the sage tertiary family.
 */
@Composable
private fun HistoryRow(row: HistoryRowUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // Merged node: TalkBack announces the whole row as one entry
                    // ("Luna, Methimazole, Oct 24, 8:02 PM, given") instead of four
                    // separate reads. Children stay in the tree, unlike clearAndSetSemantics.
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            buildString {
                                append(row.petName)
                                append(", ")
                                append(row.medicationName)
                                append(", ")
                                append(row.scheduledLabel)
                                append(", ")
                                append(row.statusLabel)
                                if (row.firedLabel != null) {
                                    append(", ")
                                    append(row.firedLabel)
                                }
                            }
                    },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${row.petName} · ${row.medicationName}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = row.scheduledLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.firedLabel != null) {
                    Text(
                        text = row.firedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            StatusChip(statusLabel = row.statusLabel)
        }
    }
}

/**
 * Small trailing status chip. Colors reuse existing scheme roles only (no Color.kt
 * change): given is the sage tertiary family used for "Given ✓" everywhere else,
 * pending is neutral secondary, missed takes the error family, skipped stays muted
 * surface so it reads as a deliberate choice rather than a failure.
 */
@Composable
private fun StatusChip(statusLabel: String) {
    val (container, content) =
        when (statusLabel) {
            "Given" ->
                MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            "Pending" ->
                MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
            "Missed" ->
                MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
            else -> // Skipped: deliberate non-dose, muted neutral.
                MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
        modifier =
            Modifier
                .width(84.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
