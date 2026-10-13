package app.toebeans.android.ui.home

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp

/**
 * Non-dismissable Today banner for ADR-0002. Stays up while exact alarms are
 * denied. The button opens the system Alarms and reminders screen.
 *
 * The message uses clearAndSetSemantics so TalkBack announces it once. The
 * button stays its own node so it can be activated.
 */
@Composable
public fun ExactAlarmDeniedBanner(
    onOpenAlarmSettings: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = EXACT_ALARM_DENIED_MESSAGE,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Warning: $EXACT_ALARM_DENIED_MESSAGE"
                    },
            )
            TextButton(onClick = onOpenAlarmSettings) {
                Text("Open alarm settings")
            }
        }
    }
}

/**
 * API 31+ read of the same condition the actuator uses before it falls
 * back to a 10 minute window.
 */
public fun readExactAlarmDenied(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return false
    }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return ExactAlarmReliability.denied(
        requiresExactAlarmPermission = true,
        canScheduleExactAlarms = alarmManager.canScheduleExactAlarms(),
    )
}

/** Opens Alarms and reminders, then the app details screen if that intent is missing. */
public fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return
    }
    val packageUri = Uri.parse("package:${context.packageName}")
    val exact =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = packageUri
        }
    try {
        context.startActivity(exact)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = packageUri
                },
            )
        } catch (_: ActivityNotFoundException) {
            // Some devices have neither screen. The banner stays; the tap does nothing.
        }
    }
}
