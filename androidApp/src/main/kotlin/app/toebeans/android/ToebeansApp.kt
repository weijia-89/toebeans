package app.toebeans.android

import android.app.Application
import app.toebeans.android.crash.LocalCrashLog
import app.toebeans.android.di.appModule
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
 *   - Boot-time scheduler rehydration of pending DoseEvents within the 72h horizon.
 *   - Replacement of FakePetRepository et al. with SQLDelight-backed implementations.
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

    private companion object {
        // Mirrors the value rendered in Settings → About. When versioning gains a build
        // pipeline source-of-truth (BuildConfig.VERSION_NAME), this constant gets
        // replaced with that reference in a single edit.
        const val APP_VERSION_NAME = "0.1.0"
    }
}
