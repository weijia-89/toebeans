package app.toebeans.android.notifications

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * ADR-0012: [BootReceiver] is disabled in the manifest until a dose is scheduled.
 * [PackageManager.COMPONENT_ENABLED_STATE_DEFAULT] means "use the manifest", which is disabled.
 */
public object BootReceiverLifecycle {
    public fun enable(context: Context) {
        val component = ComponentName(context, BootReceiver::class.java)
        val packageManager = context.packageManager
        if (packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) {
            return
        }
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
