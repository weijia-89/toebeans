package app.toebeans.core.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase

/**
 * JVM `actual` for [DatabaseFactory]. Constructs a fresh in-memory SQLite database
 * via [JdbcSqliteDriver] and applies the SQLDelight schema before returning.
 *
 * Intended for `:shared:jvmTest` and any other host-JVM consumer (none exist at
 * v0.1; production runs on Android). Each [create] call returns an isolated
 * database — there is no shared state across instances. The driver is held by
 * the returned database; close the database to release it.
 *
 * Foreign-key enforcement is NOT enabled here. JVM tests that depend on FK
 * cascade semantics should issue `PRAGMA foreign_keys=ON` themselves; the v0.1
 * schema-smoke test exercises empty selects only and does not depend on cascades.
 */
public actual class DatabaseFactory {
    public actual fun create(): ToebeansDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToebeansDatabase.Schema.create(driver)
        return ToebeansDatabase(driver)
    }
}
