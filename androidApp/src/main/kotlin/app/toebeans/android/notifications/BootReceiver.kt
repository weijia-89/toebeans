package app.toebeans.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for device reboot. Rehydrates the 72-hour dose-alarm horizon after
 * [Intent.ACTION_BOOT_COMPLETED].
 *
 * Vibe-dangerous: alarms lost across reboot are a medication-critical failure mode. Phase 1 is
 * manifest + no-op scaffold only; full SQLDelight replay lands in phase 2 per test-as-spec in
 * [BootReceiverTest].
 */
public class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        // Phase 1 scaffold: survive empty DB without scheduling. Phase 2 will query DoseEvents
        // and call AndroidNotificationActuator.schedule for the 72h window.
        Log.d(TAG, "BOOT_COMPLETED received; 72h alarm rehydration not yet implemented")
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
