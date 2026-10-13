package app.toebeans.android.ui.reliability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Owner-facing copy for the ADR-0002 exact-alarm denial banner.
 * The 10 minute figure is the actuator fallback window.
 */
internal const val DOWNGRADED_RELIABILITY_MESSAGE: String =
    "Dose reminders can arrive up to 10 minutes late because exact alarms are off."

internal const val DOWNGRADED_RELIABILITY_ACTION: String = "Turn on exact alarms"

internal fun exactAlarmSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }

/**
 * Opens the system exact-alarm screen. If this device has no such screen, the
 * banner stays up and the tap does nothing.
 */
internal fun openExactAlarmSettings(context: Context) {
    try {
        context.startActivity(exactAlarmSettingsIntent(context.packageName))
    } catch (_: ActivityNotFoundException) {
        // Some devices do not expose the exact-alarm screen. The banner stays.
    }
}

/**
 * Reads [ExactAlarmReliability.denied] and re-reads the permission on resume so a
 * grant made in system Settings clears the banner when the owner comes back.
 */
@Composable
internal fun rememberExactAlarmDenied(reliability: ExactAlarmReliability): Boolean {
    val denied by reliability.denied.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, reliability) {
        // First composition often happens after ON_RESUME has already fired.
        reliability.refresh()
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    reliability.refresh()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return denied
}

/**
 * Persistent warning while exact alarms are denied. No dismiss control: the
 * condition is still true until the owner turns exact alarms on.
 *
 * tertiaryContainer matches the night-dose warning. The message uses
 * clearAndSetSemantics so TalkBack announces it once. The action stays its own
 * node so it can be activated.
 */
@Composable
internal fun DowngradedReliabilityBanner(
    onTurnOnExactAlarms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = DOWNGRADED_RELIABILITY_MESSAGE,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Warning: $DOWNGRADED_RELIABILITY_MESSAGE"
                    },
            )
            TextButton(onClick = onTurnOnExactAlarms) {
                Text(DOWNGRADED_RELIABILITY_ACTION)
            }
        }
    }
}
