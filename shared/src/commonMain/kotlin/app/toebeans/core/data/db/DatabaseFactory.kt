package app.toebeans.core.data.db

import app.toebeans.core.db.ToebeansDatabase

/**
 * Platform-specific factory for the SQLDelight-backed [ToebeansDatabase].
 *
 * The factory pattern (vs. exposing a `SqlDriver` directly) gives each platform a
 * stable place to wire its driver-specific concerns:
 *
 * - **androidMain** constructs an `AndroidSqliteDriver`. ADR-0010 mandates that a
 *   `PRAGMA foreign_keys=ON` callback be attached at this layer — wiring of that
 *   callback is C1-int's job (it lives in `:androidApp`'s AppModule per the ADR's
 *   recommended location) and is intentionally deferred here.
 * - **jvmMain** constructs an in-memory `JdbcSqliteDriver`. This target exists so the
 *   schema can be exercised by JVM unit tests (`./gradlew :shared:jvmTest`) without
 *   the Android SDK.
 *
 * The factory does NOT depend on Koin; Koin wiring is C1-int's job. Each platform's
 * `actual` may declare additional constructor parameters (e.g. Android needs a
 * `Context`); callers pick the right constructor at the platform's wiring layer.
 *
 * Per AGENTS.md, no network or analytics dependencies touch this layer.
 */
public expect class DatabaseFactory {
    /**
     * Construct (and migrate, if needed) the [ToebeansDatabase] backing this factory.
     *
     * On Android this opens (or creates) the on-disk SQLite database file. On JVM
     * this creates a fresh in-memory database, applies the SQLDelight schema, and
     * returns the result — every call yields an isolated database, suitable for
     * test-per-method usage in `:shared:jvmTest`.
     */
    public fun create(): ToebeansDatabase
}
