package app.toebeans.android.ui.medications

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.toebeans.core.data.MedicationNameIndexRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A medication name search field with typeahead suggestions.
 *
 * Displays an ExposedDropdownMenu that filters medication names as the user types.
 * Supports case-insensitive prefix and substring matching. Free-form entry is allowed.
 *
 * @param value Current medication name value
 * @param onValueChange Callback when value changes
 * @param repository Repository providing medication name suggestions
 * @param modifier Compose modifier
 * @param enabled Whether the field is enabled
 * @param isError Whether the field is in error state
 * @param supportingText Optional supporting text to display below the field
 * @param label Optional label for the field
 * @param debounceMs Debounce delay in milliseconds (default: 250ms)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MedicationNameSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    repository: MedicationNameIndexRepository,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    label: String = "Medication name",
    debounceMs: Long = 250L,
) {
    var expanded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(value) {
        // Cancel any pending search
        searchJob?.cancel()
        // Debounce the search
        searchJob =
            launch {
                delay(debounceMs)
                if (value.isBlank()) {
                    // When empty, show first 10 medications as hints
                    suggestions = repository.getAll().take(10)
                } else {
                    suggestions = repository.search(value, limit = 10)
                }
            }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            supportingText = supportingText,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            enabled = enabled,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = "Medication suggestions: ${suggestions.size} results"
                },
        ) {
            suggestions.forEach { medication ->
                DropdownMenuItem(
                    text = { Text(medication) },
                    onClick = {
                        onValueChange(medication)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
