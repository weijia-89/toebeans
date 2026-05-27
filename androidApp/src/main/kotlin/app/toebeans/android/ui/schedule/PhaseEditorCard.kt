package app.toebeans.android.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.toebeans.android.ui.components.TimePickerField
import app.toebeans.core.model.SchedulePhase
import kotlinx.datetime.LocalTime

/**
 * Editor card for one [PhaseDraft]. Shows duration, dose times, optional skip-day interval,
 * and an optional per-phase dose amount override. The "remove phase" button is hidden when
 * this is the only phase (the form requires at least one phase).
 */
@Composable
public fun PhaseEditorCard(
    index: Int,
    draft: PhaseDraft,
    isOnlyPhase: Boolean,
    onChange: (PhaseDraft) -> Unit,
    onRemove: () -> Unit,
    onAffirmNightDose: () -> Unit = {},
    onDismissMidnightStraddle: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row with phase number and remove button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Phase ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!isOnlyPhase) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove phase ${index + 1}")
                    }
                }
            }

            // Duration in days.
            OutlinedTextField(
                value = draft.durationDaysText,
                onValueChange = { v ->
                    if (v.isEmpty() || v.all(Char::isDigit)) {
                        onChange(draft.copy(durationDaysText = v))
                    }
                },
                label = { Text("Duration (days)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Skip-day interval. dayInterval = 1 means daily (most common); higher values
            // are alternate-day / every-N-days dosing.
            OutlinedTextField(
                value = draft.dayIntervalText,
                onValueChange = { v ->
                    if (v.isEmpty() || v.all(Char::isDigit)) {
                        onChange(draft.copy(dayIntervalText = v))
                    }
                },
                label = { Text("Day interval (1 = daily, 2 = every other day, …)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Midnight-straddle informational banner (v0.1-followups #9). Surfaced when
            // the current dose-time set wraps around the calendar-day boundary in a way
            // that may confuse the user (per MidnightStraddleDetection.crossesMidnight).
            // Info-tinted via secondaryContainer to distinguish from the warmer night-dose
            // warning below (tertiaryContainer) — both banners can fire simultaneously
            // for a dose set like [23:00, 01:00], so visual differentiation matters.
            //
            // Non-blocking. "Got it" dismisses the banner for the current dose-time set;
            // dose-time edits re-surface it. Same accessibility shape as the formError and
            // nightDoseWarning banners: clearAndSetSemantics + liveRegion Polite.
            if (draft.crossesMidnight && !draft.midnightStraddleDismissed) {
                val straddleMsg =
                    "Heads up: your dose times cross midnight. Doses after midnight land on the next calendar day."
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = straddleMsg
                            },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = straddleMsg, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onDismissMidnightStraddle) {
                            Text("Got it")
                        }
                    }
                }
            }

            // Dose times: one TimePickerField per time, plus a "+ add time" button up to
            // MAX_DOSES_PER_DAY. The phase enforces strict ascending order on persistence.
            Text(
                text = "Dose times (${draft.doseTimes.size}/${SchedulePhase.MAX_DOSES_PER_DAY})",
                style = MaterialTheme.typography.labelLarge,
            )
            draft.doseTimes.forEachIndexed { tIdx, time ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TimePickerField(
                            label = "Dose ${tIdx + 1}",
                            value = time,
                            onValueChange = { newTime ->
                                onChange(
                                    draft.copy(
                                        doseTimes =
                                            draft.doseTimes.toMutableList().also { it[tIdx] = newTime },
                                    ),
                                )
                            },
                        )
                    }
                    if (draft.doseTimes.size > 1) {
                        IconButton(onClick = {
                            onChange(
                                draft.copy(
                                    doseTimes = draft.doseTimes.toMutableList().also { it.removeAt(tIdx) },
                                ),
                            )
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove dose ${tIdx + 1}")
                        }
                    }
                }
            }
            if (draft.doseTimes.size < SchedulePhase.MAX_DOSES_PER_DAY) {
                TextButton(onClick = {
                    val nextTime = nextDoseTime(draft.doseTimes)
                    onChange(draft.copy(doseTimes = draft.doseTimes + nextTime))
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add dose time")
                }
            }

            // Night-dose warning (B9, per ADR-0004 D2 + v0.1-followups #1). Surfaced when
            // any dose time falls in [00:00, 06:00). Non-blocking — the user can ignore the
            // banner and save the schedule. "Yes, that's intentional" dismisses the banner;
            // subsequent edits to dose times re-trigger it per the reset-on-edit policy
            // (see KDoc on PhaseDraft).
            if (draft.nightDoseWarning) {
                val msg = "This dose fires between midnight and 6am. Confirm you want to be woken up."
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // clearAndSetSemantics + liveRegion = Polite so TalkBack announces
                            // the warning exactly once on its appearance, without focus-stealing,
                            // and without double-announcing from descendant Text nodes. Same
                            // pattern as the formError banner in ScheduleCreateScreen (B8).
                            .clearAndSetSemantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "Warning: $msg"
                            },
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onAffirmNightDose) {
                            Text("Yes, that's intentional")
                        }
                    }
                }
            }

            // Optional per-phase dose-amount override.
            OutlinedTextField(
                value = draft.doseAmount,
                onValueChange = { onChange(draft.copy(doseAmount = it)) },
                label = { Text("Dose amount override (optional, e.g. \"5 mg\")") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.error != null) {
                Text(
                    text = draft.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Suggest a sensible next dose time when the user adds another slot. */
private fun nextDoseTime(existing: List<LocalTime>): LocalTime {
    if (existing.isEmpty()) return LocalTime(8, 0)
    val last = existing.maxByOrNull { it.toSecondOfDay() } ?: LocalTime(8, 0)
    val nextHour = (last.hour + 6).coerceAtMost(23)
    return LocalTime(nextHour, last.minute)
}

private fun LocalTime.toSecondOfDay(): Int = hour * 3600 + minute * 60 + second
