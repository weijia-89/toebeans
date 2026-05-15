package app.toebeans.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Tappable button that opens a Material 3 DatePickerDialog and reports the picked
 * [LocalDate]. Composes cleanly inside a form Column.
 *
 * Why a Button and not an OutlinedTextField: the read-only-clickable TextField pattern
 * fights Compose's focus model and has subtle TalkBack a11y issues. A labelled
 * OutlinedButton is simpler and reads naturally to screen readers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DatePickerField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    supportingText: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value?.toString() ?: "Pick a date")
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        val initialMillis = value?.toInstantAtStartOfDayUtc()?.toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Button(onClick = {
                    val picked =
                        state.selectedDateMillis?.let { ms ->
                            Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                        }
                    onValueChange(picked)
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                if (allowClear) {
                    TextButton(onClick = {
                        onValueChange(null)
                        showDialog = false
                    }) { Text("Clear") }
                }
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun LocalDate.toInstantAtStartOfDayUtc(): Instant = LocalDateTime(this, LocalTime(0, 0)).toInstant(TimeZone.UTC)
