package app.toebeans.core.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.toebeans.core.db.ToebeansDatabase

/**
 * Open base for ADR-0010 foreign-key enforcement. The `:androidApp` subclass
 * ([app.toebeans.android.data.SqliteForeignKeysCallback]) is what Koin wires at runtime.
 */
public open class SqliteForeignKeysCallbackBase : AndroidSqliteDriver.Callback(ToebeansDatabase.Schema) {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

/**
 * Android `actual` for [DatabaseFactory]. Constructs an [AndroidSqliteDriver] over the
 * on-disk SQLite database file named [databaseName] (defaults to `toebeans.db`).
 *
 * Pass [callback] from `:androidApp`'s [app.toebeans.android.data.SqliteForeignKeysCallback]
 * (or any [AndroidSqliteDriver.Callback]) so ADR-0010 FK enforcement is active on every
 * connection. The default is [SqliteForeignKeysCallbackBase] for callers that omit it.
 *
 * The factory takes a [Context] (typically the application context) because
 * Android's SQLite driver opens the database file via the platform's
 * `SQLiteOpenHelper` infrastructure.
 */
public actual class DatabaseFactory(
    private val context: Context,
    private val databaseName: String = "toebeans.db",
    private val callback: SqliteForeignKeysCallbackBase = SqliteForeignKeysCallbackBase(),
) {
    public actual fun create(): ToebeansDatabase {
        val driver =
            AndroidSqliteDriver(
                schema = ToebeansDatabase.Schema,
                context = context,
                name = databaseName,
                callback = callback,
            )
        return ToebeansDatabase(driver)
    }
}

/**
 * Opens a driver with [callback], reads `PRAGMA foreign_keys`, and closes the driver.
 * Used by the ADR-0010 Robolectric contract in `:androidApp` to assert FK enforcement
 * on the same connection-open path the app uses.
 */
public fun probeForeignKeysEnabled(
    context: Context,
    databaseName: String,
    callback: SqliteForeignKeysCallbackBase,
): Long {
    val driver =
        AndroidSqliteDriver(
            schema = ToebeansDatabase.Schema,
            context = context,
            name = databaseName,
            callback = callback,
        )
    return try {
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA foreign_keys",
                mapper = { cursor ->
                    QueryResult.Value(
                        if (cursor.next().value) cursor.getLong(0) else 0L,
                    )
                },
                parameters = 0,
            ).value ?: 0L
    } finally {
        driver.close()
    }
}
