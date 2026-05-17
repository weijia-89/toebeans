package app.toebeans.android.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.koin.androidx.compose.koinViewModel

/**
 * Schedule Detail screen (B7).
 *
 * Read-only view of a Schedule: dates, ordered phases, dose-times. Top-bar Delete
 * affordance with confirmation dialog matching the [app.toebeans.android.ui.pets.PetEditScreen]
 * delete pattern.
 *
 * Reached from the Reminder List (B6). Has its own top app bar with a back button;
 * the bottom NavigationBar is hidden because `SCHEDULE_DETAIL_ROUTE` is not in the
 * `TOP_LEVEL_ROUTES` set in `ToebeansAppShell`.
 *
 * ## Auto-back on delete or stale
 *
 * Two scenarios pop the back stack automatically:
 *   1. The user confirms deletion → VM.delete() succeeds → `onBack()` called.
 *   2. The schedule disappears from underneath us (concurrent delete from another
 *      surface, or initial load resolves to null because the scheduleId is invalid
 *      after a process restart). The `LaunchedEffect` keyed on
 *      `(scheduleId, schedule == null, loading)` detects this and pops.
 *
 * The second case is defensive: in v0.1 there's no other surface that deletes
 * schedules, but the Reminder List + Schedule Detail loop creates the possibility of
 * the user navigating BACK to a stale link after a refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ScheduleDetailScreen(
    scheduleId: String,
    onBack: () -> Unit,
    viewModel: ScheduleDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scheduleId) {
        viewModel.load(scheduleId)
    }

    // Auto-pop when the schedule is gone (deleted or never existed). Only fires once
    // `loading == false` so we don't pop during the transient pre-emission frame.
    LaunchedEffect(state.scheduleId, state.schedule, state.loading) {
        if (state.scheduleId == scheduleId && !state.loading && state.schedule == null) {
            onBack()
        }
    }

    if (showDeleteDialog) {
        DeleteScheduleDialog(
            medicationName = state.medicationName.ifBlank { "this schedule" },
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    if (viewModel.delete()) onBack()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show Delete only once the schedule has loaded — protects against
                    // tapping Delete during the pre-emission frame where state.schedule
                    // is still null and a delete() call would no-op.
                    if (state.schedule != null) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            Text("Delete")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> LoadingBody(innerPadding)
            state.schedule == null -> {
                // Empty body during the brief moment between schedule == null and the
                // auto-pop LaunchedEffect firing. A bare Box keeps Scaffold happy
                // (its content lambda must return a Composable).
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
            }
            else ->
                ScheduleDetailBody(
                    schedule = state.schedule!!,
                    phases = state.phases,
                    medicationName = state.medicationName,
                    petName = state.petName,
                    contentPadding = innerPadding,
                )
        }
    }
}

@Composable
private fun LoadingBody(contentPadding: PaddingValues) {
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
private fun ScheduleDetailBody(
    schedule: Schedule,
    phases: List<SchedulePhase>,
    medicationName: String,
    petName: String,
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderCard(petName = petName, medicationName = medicationName)
        DatesCard(schedule = schedule)
        PhasesCard(phases = phases)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HeaderCard(
    petName: String,
    medicationName: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$medicationName for $petName"
                },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = petName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = medicationName,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun DatesCard(schedule: Schedule) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Date range",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val endLabel = schedule.endDate?.toString() ?: "ongoing"
            Text(
                text = "${schedule.startDate} → $endLabel",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun PhasesCard(phases: List<SchedulePhase>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (phases.size == 1) "Phase" else "Phases (${phases.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            phases.forEachIndexed { index, phase ->
                if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                PhaseRow(phase = phase, indexLabel = if (phases.size > 1) "Phase ${index + 1}: " else "")
            }
        }
    }
}

@Composable
private fun PhaseRow(
    phase: SchedulePhase,
    indexLabel: String,
) {
    val cadence =
        when (phase.dosesPerDay) {
            1 -> "Once daily"
            2 -> "Twice daily"
            else -> "${phase.dosesPerDay}× daily"
        }
    val skipDay =
        if (phase.dayInterval > 1) " (every ${phase.dayInterval} days)" else ""
    Column {
        Text(
            text = "$indexLabel$cadence$skipDay for ${phase.durationDays} days",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Doses at " + phase.doseTimesLocal.joinToString(", ") { it.shortFormat() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        phase.doseAmount?.let { amount ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Dose amount: $amount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LocalTime.shortFormat(): String {
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')
    return "$hh:$mm"
}

/**
 * Confirmation dialog for schedule deletion. Mirrors the pet/medication delete dialogs.
 *
 * Why a confirmation dialog and not an undo snackbar: deleting a schedule deletes its
 * phases atomically (per ScheduleRepository.delete contract). The user's tap intent is
 * "yes, end this medication reminder forever," which is destructive enough to warrant
 * a hard confirmation. Undo-snackbar UX would suggest reversibility we cannot offer
 * without restructuring the repo's delete contract.
 */
@Composable
private fun DeleteScheduleDialog(
    medicationName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete reminder?") },
        text = {
            Text(
                "Deleting the reminder for $medicationName will also remove its schedule history. " +
                    "This can't be undone.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
