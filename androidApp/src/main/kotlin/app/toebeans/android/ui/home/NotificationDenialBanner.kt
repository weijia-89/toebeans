package app.toebeans.android.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import app.toebeans.core.notifications.NOTIFICATION_DENIAL_BANNER_COPY

/**
 * Non-dismissable Today banner for ADR-0011. The sentence is the whole control.
 * A tap opens the system notification settings for this app.
 */
@Composable
public fun NotificationDenialBanner(
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    copy: String = NOTIFICATION_DENIAL_BANNER_COPY,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenNotificationSettings),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = copy,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Warning: $copy"
                    },
        )
    }
}

/** Opens this app's notification settings, then app details if that screen is missing. */
public fun openNotificationSettings(context: Context) {
    val packageUri = android.net.Uri.parse("package:${context.packageName}")
    val notification =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
        }
    try {
        context.startActivity(notification)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = packageUri
                },
            )
        } catch (_: ActivityNotFoundException) {
            // The banner stays. The tap does nothing on this device.
        }
    }
}
