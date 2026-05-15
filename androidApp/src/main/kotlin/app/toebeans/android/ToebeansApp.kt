package app.toebeans.android

import android.app.Application
import app.toebeans.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application entry point. Initializes the Koin DI container with the UI scaffold's
 * fake-repository wiring.
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
        startKoin {
            androidLogger(Level.ERROR) // INFO would log every injection; chatty in adb logcat.
            androidContext(this@ToebeansApp)
            modules(appModule)
        }
    }
}
