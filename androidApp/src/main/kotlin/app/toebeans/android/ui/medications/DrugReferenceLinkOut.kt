package app.toebeans.android.ui.medications

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * D5 option A: a link-out catalog. Each row stores only a medication name, a source label,
 * and a source URL. There is no advice text, no paraphrase, no summary, and no dose number
 * anywhere in this surface — the publisher page is the single source of truth and the app
 * holds only a pointer to it. Tapping a label opens the system browser.
 */
internal data class LinkOutEntry(
    val medication: String,
    val sourceLabel: String,
    val url: String,
)

/** Mirrors data/drug-reference/link-out-catalog.json. Keep the two lists in sync. */
internal object DrugReferenceCatalog {
    val entries: List<LinkOutEntry> =
        listOf(
            LinkOutEntry(
                medication = "Methimazole",
                sourceLabel = "Merck Veterinary Manual",
                url = "https://www.merckvetmanual.com/",
            ),
            LinkOutEntry(
                medication = "Amoxicillin",
                sourceLabel = "VCA Animal Hospitals",
                url = "https://vcahospitals.com/",
            ),
        )
}

/** Platform Intent only: no network library and no Gradle dependency. */
internal fun viewUrlIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

internal fun openSourceLink(
    context: Context,
    url: String,
) {
    context.startActivity(viewUrlIntent(url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
internal fun DrugReferenceLinkOutCard(
    catalog: List<LinkOutEntry> = DrugReferenceCatalog.entries,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Drug references",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Links to publisher pages. No advice text is stored in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        catalog.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.medication,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.sourceLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .clickable { onOpenUrl(entry.url) }
                            .semantics {
                                contentDescription =
                                    "Open ${entry.sourceLabel} reference for ${entry.medication}"
                            },
                )
            }
        }
    }
}
