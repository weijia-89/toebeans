package app.toebeans.core.data.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.toebeans.core.db.ToebeansDatabase

/**
 * Android `actual` for [DatabaseFactory]. Constructs an [AndroidSqliteDriver] over the
 * on-disk SQLite database file named [databaseName] (defaults to `toebeans.db`).
 *
 * **Foreign-key enforcement is NOT wired here.** Per ADR-0010, every Android
 * connection must execute `PRAGMA foreign_keys=ON` via an
 * `AndroidSqliteDriver.Callback`. That callback class
 * (`SqliteForeignKeysCallback`) lives in `:androidApp` per the ADR's recommended
 * location and is wired into the Koin module by C1-int. Until that wiring
 * lands, this factory produces a driver with the default callback — the M1
 * cutover landing should NOT happen without C1-int's ADR-0010 patch in front
 * of it.
 *
 * The factory takes a [Context] (typically the application context) because
 * Android's SQLite driver opens the database file via the platform's
 * `SQLiteOpenHelper` infrastructure.
 */
public actual class DatabaseFactory(
    private val context: Context,
    private val databaseName: String = "toebeans.db",
) {
    public actual fun create(): ToebeansDatabase {
        val driver =
            AndroidSqliteDriver(
                schema = ToebeansDatabase.Schema,
                context = context,
                name = databaseName,
            )
        return ToebeansDatabase(driver)
    }
}
