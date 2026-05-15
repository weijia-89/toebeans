package app.toebeans.android

import android.app.Application

/**
 * Application entry point.
 *
 * v0.1: intentionally minimal. Slice 1 will wire up:
 *   - Koin DI container
 *   - NotificationChannel registration for "medication-critical"
 *   - Boot-time scheduler rehydration
 *
 * Per AGENTS.md, the wiring of those is vibe-dangerous and requires its own test-as-spec PR.
 */
class ToebeansApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO(slice-1): initialize Koin, NotificationChannel("medication-critical"), and
        //                reschedule pending DoseEvents within the 72h horizon.
        //                Implement only AFTER the failing scheduler tests pass.
    }
}
