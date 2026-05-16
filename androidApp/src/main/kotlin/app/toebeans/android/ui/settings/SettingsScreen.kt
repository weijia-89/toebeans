package app.toebeans.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.toebeans.android.ui.theme.ToebeansTheme

/**
 * Settings screen. Two grouping cards:
 *   1. About — current build facts (version, storage model, analytics posture).
 *   2. What's coming — short user-facing list of features on the roadmap.
 *
 * No interactive controls yet (no toggles, no pickers). Once the user can actually
 * tune notification channels and pick a backup destination, those land here as their
 * own rows — at which point this screen will graduate from "info wall" to "control
 * panel" and the grouping will likely evolve.
 *
 * The previous incarnation linked to `docs/ROADMAP.md` and name-dropped milestone
 * numbers; replaced with plain-language feature descriptions because the doc isn't
 * shipped in the APK and pet owners shouldn't have to read internal planning
 * artifacts to understand what's coming.
 */
@Composable
public fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // verticalScroll: the screen barely overflows today, but once a few more rows land
    // (notification toggles, backup picker, etc.) we'll need scroll. Wiring it now means
    // we don't get the "wait, why doesn't this scroll" regression later.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        SettingsCard(title = "About") {
            SettingsRow(label = "App version", value = "0.1.0")
            SettingsRow(label = "Storage", value = "Local-only · no cloud")
            SettingsRow(label = "Analytics", value = "None")
        }

        SettingsCard(title = "What's coming") {
            ComingSoonItem("Encrypted backup to a file you control")
            ComingSoonItem("Custom reminder sounds per medication")
            ComingSoonItem("Travel-aware reminders that respect time zones")
            ComingSoonItem("Per-pet quiet hours")
        }
    }
}

/**
 * A grouped section card. Visual parity with [PetIdentityCard][app.toebeans.android.ui.pets.PetIdentityCard]
 * and the HomeScreen "Today's doses" card so Settings feels of-a-piece with the rest
 * of the app instead of like a separate info dump.
 */
@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/**
 * Two-line row: dim label above, brighter value below. The labelLarge / bodyMedium
 * pairing matches the field-display pattern used on PetDetailScreen so a user
 * scanning the app gets a consistent "label-on-top, value-below" rhythm.
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Bulleted-item row for the "What's coming" card. Uses a leading "·" glyph (middle dot)
 * rather than the Unicode bullet "•" because middle dot reads slightly lighter and the
 * card is meant to feel anticipatory ("here's what's next") not authoritative ("here
 * is THE list"). Small stylistic choice but the warmth adds up.
 */
@Composable
private fun ComingSoonItem(text: String) {
    // Merge descendants so TalkBack reads only the item text — the "·" glyph is
    // decorative and would otherwise be announced as "interpunct" or "middle dot"
    // on most engines, prefixing every item with noise.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = text
            },
    ) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ToebeansTheme(dynamic = false) { SettingsScreen() }
}
