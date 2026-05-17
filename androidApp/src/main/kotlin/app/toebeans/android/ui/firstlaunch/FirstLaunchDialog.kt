package app.toebeans.android.ui.firstlaunch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.data.loadDemoData
import app.toebeans.android.preferences.FirstLaunchPreferences

/**
 * First-launch onboarding prompt. Shown once on app first open; offers to populate the
 * in-memory fake repositories with the Luna + Rufus demo data (useful for reviewers who
 * want to see the populated UI immediately) or start with an empty state (the path real
 * caregivers will take).
 *
 * Either choice marks the dialog as seen and never shows it again on this install.
 *
 * The dialog is non-dismissable by tap-outside or back-press: we want an explicit choice
 * so the user understands the demo-data state of the app. Both buttons are equal-weight
 * (no preferred default) because we genuinely don't know which path the user wants.
 *
 * **Accessibility.** The dialog title and both buttons carry explicit semantics so
 * TalkBack users get a clear flow. The dialog has a single dismiss path (one of the two
 * buttons), so there is no ambiguity about how to proceed.
 */
@Composable
public fun FirstLaunchDialogHost(prefs: FirstLaunchPreferences) {
    val seen by prefs.firstLaunchSeen.collectAsStateWithLifecycle()
    if (seen) return
    FirstLaunchDialog(
        onLoadDemoData = {
            loadDemoData()
            prefs.markSeen()
        },
        onStartEmpty = { prefs.markSeen() },
    )
}

@Composable
internal fun FirstLaunchDialog(
    onLoadDemoData: () -> Unit,
    onStartEmpty: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            // No-op: we require an explicit button tap. Tap-outside / back-press do not
            // dismiss the dialog because the choice carries real demo-state consequences.
        },
        title = {
            Text(
                text = "Welcome to toebeans",
                modifier = Modifier.semantics { contentDescription = "Welcome to toebeans, first launch onboarding" },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        "toebeans is a local-first medication reminder for the animals you love. " +
                            "Nothing leaves your device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        "Would you like to start with a couple of example pets (Rufus the dog, " +
                            "Luna the cat on a twice-daily medication) so you can see how the app " +
                            "looks, or start fresh and add your own?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        "Either choice can be changed by adding or deleting pets later. The demo " +
                            "data uses placeholder pets — it does not represent a real medical " +
                            "recommendation.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onStartEmpty,
                modifier = Modifier.semantics { contentDescription = "Start with an empty pet list" },
            ) {
                Text("Start fresh")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLoadDemoData,
                modifier = Modifier.semantics { contentDescription = "Load Rufus and Luna demo data" },
            ) {
                Text("Load demo data")
            }
        },
    )
}
