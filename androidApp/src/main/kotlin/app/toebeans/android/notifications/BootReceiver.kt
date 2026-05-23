package app.toebeans.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.toebeans.android.ToebeansApp
import kotlinx.datetime.Clock

/**
 * BroadcastReceiver for device reboot. Rehydrates the 72-hour dose-alarm horizon after
 * [Intent.ACTION_BOOT_COMPLETED] by delegating to [ToebeansApp.rehydrateBootAlarms].
 *
 * Vibe-dangerous: alarms lost across reboot are a medication-critical failure mode. The
 * SQLDelight query path is still a stub (zero alarms until persistence lands in the receiver
 * process); see [BootReceiverTest] for the full contract and follow-on slices.
 */
public class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val scheduledCount = ToebeansApp.rehydrateBootAlarms(context.applicationContext)
        // ADR-0012 specifies a LocalCrashLog BOOT_REPLAY_OK marker; wiring that append path
        // is deferred until LocalCrashLog gains a non-crash write API (separate slice).
        Log.i(
            TAG,
            "BOOT_REPLAY_OK reminders=$scheduledCount at=${Clock.System.now()}",
        )
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
