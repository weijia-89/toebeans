package app.toebeans.android

import android.app.Application
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.toebeans.android.crash.LocalCrashLog
import app.toebeans.android.di.appModule
import app.toebeans.android.notifications.AndroidNotificationActuator
import app.toebeans.android.notifications.RequestCodeAllocator
import app.toebeans.core.notifications.ScheduledReminder
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application entry point. Initializes the Koin DI container with the UI scaffold's
 * fake-repository wiring, and installs the local crash-log handler (ADR-0009).
 *
 * Still pending (milestone 1 vibe-dangerous work):
 *   - NotificationChannel("medication-critical") registration.
 *   - Replacement of FakePetRepository et al. with SQLDelight-backed implementations.
 *   - Boot rehydration SQLDelight query path (stub schedules zero alarms until persistence
 *     lands in the receiver process; see [rehydrateBootAlarms]).
 *
 * Each of those lands in its own test-as-spec PR per AGENTS.md.
 */
class ToebeansApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install the crash-log handler BEFORE Koin so a startup-time DI failure still
        // produces a local crash record. The handler always delegates to the previously
        // installed default handler (Android's) so the OS sees the crash normally; we
        // are only adding a local side-channel for the user to export through Settings.
        LocalCrashLog.forApplication(context = this, buildVersionName = APP_VERSION_NAME).install()

        startKoin {
            androidLogger(Level.ERROR) // INFO would log every injection; chatty in adb logcat.
            androidContext(this@ToebeansApp)
            modules(appModule)
        }
    }

    public companion object {
        /**
         * How far ahead of [kotlinx.datetime.Clock.System.now] boot rehydration re-schedules
         * pending dose alarms. Matches [docs/ARCHITECTURE.md] `AUTO_RESCHEDULE_HORIZON_HOURS`.
         */
        public const val REHYDRATE_HORIZON_HOURS: Int = 72

        /**
         * Re-schedule AlarmManager entries for pending doses within the 72-hour horizon.
         *
         * Called from [app.toebeans.android.notifications.BootReceiver] on
         * [android.content.Intent.ACTION_BOOT_COMPLETED]. Runs outside Koin because
         * BroadcastReceivers execute in a separate process where in-memory fakes are empty.
         *
         * **Phase 2 stub:** the SQLDelight lookup is not wired yet; [loadPendingRemindersInHorizon]
         * returns an empty list so fresh installs and pre-persistence builds schedule zero alarms
         * without crashing. A follow-on slice replaces the stub with a direct DB query once all
         * four repositories are swapped in the receiver process.
         *
         * @return count of reminders passed to [AndroidNotificationActuator.schedule].
         */
        public fun rehydrateBootAlarms(context: Context): Int {
            val reminders = loadPendingRemindersInHorizon()
            if (reminders.isEmpty()) {
                return 0
            }
            val actuator = notificationActuatorFor(context)
            for (reminder in reminders) {
                actuator.schedule(reminder)
            }
            return reminders.size
        }

        /**
         * Stub until SQLDelight is reachable from the boot receiver process. Returns every
         * pending [ScheduledReminder] whose [ScheduledReminder.scheduledAt] falls in
         * `[now, now + REHYDRATE_HORIZON_HOURS)`.
         */
        internal fun loadPendingRemindersInHorizon(): List<ScheduledReminder> {
            // Separate-process receiver cannot see FakeDoseEventRepository state. Real impl
            // opens the SQLDelight driver directly (ADR-0010 FK callback) and queries unfired
            // rows inside the horizon window.
            return emptyList()
        }

        internal fun notificationActuatorFor(context: Context): AndroidNotificationActuator {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            return AndroidNotificationActuator(
                context = context,
                alarmManager = alarmManager,
                notificationManager = NotificationManagerCompat.from(context),
                requestCodeAllocator = RequestCodeAllocator.fromContext(context),
            )
        }

        // Mirrors the value rendered in Settings → About. When versioning gains a build
        // pipeline source-of-truth (BuildConfig.VERSION_NAME), this constant gets
        // replaced with that reference in a single edit.
        const val APP_VERSION_NAME = "0.1.0"
    }
}
