package com.razz.eva.persistence.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource
import kotlinx.coroutines.currentCoroutineContext

typealias HikariPoolConnectionProvider = DataSourceConnectionProvider

class DataSourceConnectionProvider(
    private val pool: DataSource,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO
) : JdbcConnectionProvider {

    override suspend fun acquire(): Connection {
        // fail-fast if current coroutine was cancelled before acquiring a connection
        currentCoroutineContext().ensureActive()

        // here are 2 issues to solve:
        // 1. if the current coroutine is cancelled and we don't have NonCancellable at all,
        //    the resulting connection will be discarded even if there is really nothing to cancel in this function
        // 2. even if we put NonCancellable in the context, but also change a dispatcher,
        //    the resulting connection will still be discarded, because withContext has a prompt cancellation guarantee,
        //    withContext will still throw a CancellationException regardless of the presence of NonCancellable,
        //    if the current coroutine is cancelled before withContext starts executing the block
        //
        // see https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html
        return withContext(NonCancellable) { // do not change a dispatcher when doing NonCancellable
            withContext(blockingJdbcContext) { // change a dispatcher separately
                pool.connection
            }
        }
    }

    override suspend fun release(connection: Connection) =
        // If we close current coroutine (by service/http/call/etc timeout f.e.) -
        // we can't call withContext() block, because it will throw an exception.
        withContext(NonCancellable) {
            withContext(blockingJdbcContext) {
                connection.close()
            }
        }
}
