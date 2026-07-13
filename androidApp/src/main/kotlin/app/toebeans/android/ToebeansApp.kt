package app.toebeans.android

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import app.toebeans.android.crash.LocalCrashLog
import app.toebeans.android.data.SqliteForeignKeysCallback
import app.toebeans.android.di.appModule
import app.toebeans.android.notifications.AndroidNotificationActuator
import app.toebeans.android.notifications.RequestCodeAllocator
import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.notifications.ReminderLookup
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.notifications.SqlDelightReminderLookup
import app.toebeans.core.scheduler.ReminderRescheduler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlin.time.Duration.Companion.hours

/**
 * Application entry point. Initializes the Koin DI container with SQLDelight-backed
 * repositories via [app.toebeans.android.di.appModule], and installs the local crash-log
 * handler (ADR-0009).
 *
 * All four repository contracts bind SQLDelight impls on `toebeans.db`
 * ([app.toebeans.core.data.SqlDelightDoseEventRepository] for doses). Alarm dispatch in the
 * receiver process reads the same file via [reminderLookupForReceiver].
 *
 * Still pending (milestone 1 vibe-dangerous work):
 *   - NotificationChannel("medication-critical") registration.
 *
 * Each pending item lands in its own test-as-spec PR per AGENTS.md.
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

        // Sweep stale pending doses to MISSED on every app foreground.
        // Runs after Koin is ready so DoseEventRepository is available.
        runMissedDoseSweeper()
    }

    /**
     * Transitions all PENDING doses whose scheduled time is older than
     * [ReminderRescheduler.MISSED_DOSE_TIMEOUT_HOURS] to MISSED status.
     * Called on every app cold start and after boot rehydration.
     */
    private fun runMissedDoseSweeper() {
        val doseEventRepository: DoseEventRepository =
            org.koin.java.KoinJavaComponent.get(
                DoseEventRepository::class.java,
            )
        val cutoff = Clock.System.now() - ReminderRescheduler.MISSED_DOSE_TIMEOUT_HOURS.hours
        // Fire-and-forget: if it fails, we'll sweep again on next foreground.
        // Exceptions are logged by Koin's coroutine scope but don't crash the app.
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                doseEventRepository.markStalePendingAsMissed(cutoff)
            } catch (e: SQLiteException) {
                android.util.Log.w("ToebeansApp", "Missed dose sweep failed", e)
            } catch (e: IllegalStateException) {
                android.util.Log.w("ToebeansApp", "Missed dose sweep failed", e)
            }
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
         * Queries pending [ScheduledReminder] rows inside the 72-hour horizon via SQLDelight
         * ([loadPendingRemindersInHorizon]) and re-registers each with AlarmManager. Does not
         * materialize new dose rows; schedule-create → dose-row projection is a separate slice.
         *
         * Also runs [markStalePendingAsMissed] to transition stale pending doses to MISSED
         * before rehydration, so boot doesn't resurface outdated alarms.
         *
         * @return count of reminders passed to [AndroidNotificationActuator.schedule].
         */
        public fun rehydrateBootAlarms(context: Context): Int {
            // Sweep stale pending doses BEFORE rehydrating alarms.
            // This prevents re-scheduling alarms for doses that should already be marked missed.
            sweepStalePendingDosesOnBoot(context)

            val reminders = loadPendingRemindersInHorizon(context)
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
         * Runs the missed-dose sweep outside the Koin graph for the boot receiver path.
         * Uses direct SQLDelight to work in the receiver process.
         */
        private fun sweepStalePendingDosesOnBoot(context: Context) {
            try {
                val database = openReceiverDatabase(context)
                val now = Clock.System.now()
                val cutoff = now - ReminderRescheduler.MISSED_DOSE_TIMEOUT_HOURS.hours
                database.doseEventQueries.markStalePendingAsMissed(
                    resolved_at = cutoff.toEpochMilliseconds(),
                    scheduled_at = cutoff.toEpochMilliseconds(),
                )
            } catch (e: SQLiteException) {
                android.util.Log.w("ToebeansApp", "Boot sweep failed", e)
            } catch (e: IllegalStateException) {
                android.util.Log.w("ToebeansApp", "Boot sweep failed", e)
            }
        }

        /**
         * Returns every pending [ScheduledReminder] whose [ScheduledReminder.scheduledAt] falls in
         * `[now, now + REHYDRATE_HORIZON_HOURS)`.
         *
         * Opens the receiver-process SQLDelight database directly (ADR-0010 FK callback); in-memory
         * fakes from the UI process are not visible here.
         */
        internal fun loadPendingRemindersInHorizon(context: Context): List<ScheduledReminder> {
            val now = Clock.System.now()
            val horizonEnd = now + REHYDRATE_HORIZON_HOURS.hours
            val database = openReceiverDatabase(context)
            return database.doseEventQueries
                .selectPendingDoseEventsInRangeActive(
                    scheduled_at = now.toEpochMilliseconds(),
                    scheduled_at_ = horizonEnd.toEpochMilliseconds(),
                ).executeAsList()
                .map { row ->
                    ScheduledReminder(
                        id = row.id,
                        scheduleId = row.schedule_id,
                        scheduledAt = Instant.fromEpochMilliseconds(row.scheduled_at),
                        // Names populated at fire time via SqlDelightReminderLookup
                        medicationName = "",
                        petName = "",
                    )
                }
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

        /**
         * Test seam so Robolectric can inject an isolated in-memory database without touching
         * the on-disk `toebeans.db` file used in production receiver lookups.
         */
        @VisibleForTesting
        @JvmField
        var receiverDatabaseFactory: ((Context) -> ToebeansDatabase)? = null

        private var cachedReceiverDatabase: ToebeansDatabase? = null

        /**
         * Opens (or reuses) the SQLDelight database for BroadcastReceiver entry points that
         * run outside the Koin graph. Uses the same ADR-0010 FK callback as [appModule].
         */
        internal fun openReceiverDatabase(context: Context): ToebeansDatabase {
            receiverDatabaseFactory?.let { factory -> return factory(context) }
            return cachedReceiverDatabase ?: DatabaseFactory(
                context = context.applicationContext,
                callback = SqliteForeignKeysCallback(),
            ).create().also { cachedReceiverDatabase = it }
        }

        internal fun reminderLookupForReceiver(context: Context): ReminderLookup =
            SqlDelightReminderLookup(openReceiverDatabase(context))

        @VisibleForTesting
        fun resetReceiverDatabaseCacheForTests() {
            cachedReceiverDatabase = null
            receiverDatabaseFactory = null
        }

        // Mirrors the value rendered in Settings → About. When versioning gains a build
        // pipeline source-of-truth (BuildConfig.VERSION_NAME), this constant gets
        // replaced with that reference in a single edit.
        const val APP_VERSION_NAME = "0.1.0"
    }
}
