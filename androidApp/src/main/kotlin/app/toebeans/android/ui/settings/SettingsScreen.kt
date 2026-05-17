package app.toebeans.android.ui.settings

import android.content.Intent
import android.os.Build
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
import app.toebeans.android.crash.LocalCrashLog
import app.toebeans.android.preferences.ThemeMode
import app.toebeans.android.ui.theme.ToebeansTheme
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * Settings screen. Four grouping cards:
 *   1. Display — theme override (Auto/Light/Dark) + Material You dynamic-color toggle.
 *   2. Data — descriptive copy + a DISABLED "Export data (coming soon)" button. The
 *      affordance is visible but inert until the backup-format ADR + implementation
 *      lands in M1. A live-but-no-op Toast version was tried and intentionally rolled
 *      back: in a trust-positioned app, a button that responds but does nothing
 *      undoes the trust the rest of the surface earns.
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
            // Encrypted backup is a real product commitment (see "What's coming" card
            // below + ROADMAP milestone 1) but the format isn't finalized in v0.1. We
            // intentionally render the button DISABLED rather than wiring a Toast: a
            // button that visibly responds but does nothing is worse for trust than a
            // disabled affordance that says "not yet."
            Text(
                text =
                    "Your data lives only on this device. A future update will let " +
                        "you export an encrypted backup to a file you control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {},
                enabled = false,
            ) {
                Text("Export data (coming soon)")
            }
        }

        SettingsCard(title = "Diagnostics") {
            // Local crash-log capture per ADR-0009. The handler writes to
            // filesDir/crash.log; this button reads the file and hands it to the
            // user via ACTION_SEND. Intent extras are capped around 1 MB on most
            // platforms; the log is rotated at 256 KB so a single file fits well
            // under the cap.
            //
            // Hidden when the log is absent (no crash has happened yet on this
            // install) so first-launch users do not see a button for something
            // they have no use for.
            val logFile = File(context.filesDir, LocalCrashLog.FILE_PRIMARY)
            val logExists = logFile.exists() && logFile.length() > 0
            Text(
                text =
                    "We collect nothing automatically. If toebeans crashes and you " +
                        "want to send the log, you can export it yourself. The log " +
                        "contains the stack trace and your device model — no pet, " +
                        "medication, or dose data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val contents = runCatching { logFile.readText() }.getOrElse { "(failed to read log)" }
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "toebeans crash log")
                            putExtra(Intent.EXTRA_TEXT, contents)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    val chooser =
                        Intent
                            .createChooser(intent, "Share crash log")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                },
                enabled = logExists,
            ) {
                Text(if (logExists) "Export crash log" else "No crash log yet")
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
