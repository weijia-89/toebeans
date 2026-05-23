package app.toebeans.android.data

import app.toebeans.core.data.db.SqliteForeignKeysCallbackBase

/**
 * AndroidSqliteDriver.Callback that enforces foreign-key constraints on every
 * database connection opened by the toebeans Android driver. See ADR-0010 for
 * the rationale and the primary-source citations.
 */
public typealias SqliteForeignKeysCallback = SqliteForeignKeysCallbackBase
