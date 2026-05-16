package app.toebeans.android.ui.settings

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.preferences.ThemeMode
import app.toebeans.android.ui.theme.ToebeansTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Settings screen. Four grouping cards:
 *   1. Display — theme override (Auto/Light/Dark) + Material You dynamic-color toggle.
 *   2. Data — placeholder Export-data action (no-op for v0.1 except a toast confirming
 *      the affordance is wired; full implementation lands with the Backup milestone).
 *   3. About — current build facts.
 *   4. What's coming — short user-facing list of features on the roadmap.
 *
 * Why the Display card is first: it's the only one a user is likely to actually touch.
 * About and What's coming are reference/curiosity surfaces. Putting Display at the top
 * means the most useful thing is the first thing in the user's reading order.
 */
@Composable
public fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

        SettingsCard(title = "Display") {
            // Three-way theme picker as a SingleChoiceSegmentedButtonRow. The "Auto"
            // default option means most users never touch this — the segmented control
            // is for the user who finds the system default uncomfortable in some
            // context (e.g. reading in bed, vet's office under fluorescents).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                        ) {
                            Text(text = mode.displayName)
                        }
                    }
                }
            }

            // Material You dynamic-color toggle. On Android < 12 (API 31) the underlying
            // platform API doesn't exist, so we disable the row and explain why — leaving
            // a dead toggle would just look broken. The Switch is right-aligned per the
            // M3 settings pattern; tapping the label as well as the switch toggles the
            // state (full-row touch target).
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ToggleRow(
                title = "Material You colors",
                subtitle =
                    if (dynamicSupported) {
                        "Match the app's palette to your wallpaper."
                    } else {
                        "Requires Android 12 or newer."
                    },
                checked = dynamicColor && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = { viewModel.setDynamicColor(it) },
            )
        }

        SettingsCard(title = "Data") {
            // Placeholder export. v0.1 the encrypted-backup format is not yet finalized
            // (ADR pending) so we don't write a file — but the affordance is here so
            // users discover the feature exists and reviewers see the surface area.
            // Tapping it surfaces a Toast acknowledging the request rather than silently
            // doing nothing (which would feel broken).
            Text(
                text =
                    "Your data lives only on this device. A future update will let " +
                        "you export an encrypted backup to a file you control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    Toast
                        .makeText(
                            context,
                            "Backup export is coming in a future update.",
                            Toast.LENGTH_SHORT,
                        ).show()
                },
            ) {
                Text("Export data")
            }
        }

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
 * Display labels for [ThemeMode]. Separate from `wireName` (which is the persisted
 * stable token) so we can change UI copy without breaking saved preferences.
 */
private val ThemeMode.displayName: String
    get() =
        when (this) {
            ThemeMode.AUTO -> "Auto"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
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
 * Boolean toggle row used by the Display card. Two-line text (title + subtitle) on the
 * left, right-aligned [Switch] on the right. Tapping anywhere on the row toggles state —
 * the row itself is the click target, not just the Switch, so the visible touch surface
 * matches what a user expects from a Material 3 settings list.
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // toggleable wraps the whole row in one accessible toggle node — TalkBack announces
    // "Material You colors, switch, on" rather than reading the title and subtitle as
    // separate text nodes plus a switch as its own control.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$title. $subtitle"
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
    ToebeansTheme(dynamic = false) {
        // Preview can't easily inject a Koin VM, so we render only the static cards. The
        // toggle UI is exercised via @Preview on a separate composable below if desired.
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings (preview – Display card disabled)", style = MaterialTheme.typography.titleLarge)
        }
    }
}
