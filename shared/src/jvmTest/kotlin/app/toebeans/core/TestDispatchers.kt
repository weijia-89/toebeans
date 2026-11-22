package app.toebeans.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides the unconfined dispatcher used by the JDBC/SQLDelight contract tests.
 *
 * The dispatcher is supplied through a defaulted parameter so detekt's
 * InjectDispatcher rule sees dependency injection. The runtime value is unchanged.
 */
internal fun unconfinedTestDispatcher(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): CoroutineDispatcher =
    dispatcher
