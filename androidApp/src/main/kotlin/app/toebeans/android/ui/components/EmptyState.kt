package app.toebeans.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.toebeans.android.ui.theme.ToebeansTheme

/**
 * Reusable empty-state placeholder used by Home, Pets, and Schedule list screens. Single
 * primary call-to-action only — empty states with two equally-prominent buttons cause
 * decision paralysis. The secondary path (if any) is a TextButton in the caller, not here.
 */
@Composable
public fun EmptyState(
    title: String,
    body: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPrimaryAction) {
                Text(primaryActionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    ToebeansTheme(dynamic = false) {
        EmptyState(
            title = "No pets yet",
            body = "Add Rufus, Luna, or whoever you share toe beans with to get started.",
            primaryActionLabel = "Add pet",
            onPrimaryAction = {},
        )
    }
}
