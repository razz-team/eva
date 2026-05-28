package com.razz.eva.persistence.jdbc

import com.razz.eva.tracing.withSpan
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource
import kotlin.coroutines.coroutineContext

typealias HikariPoolConnectionProvider = DataSourceConnectionProvider

class DataSourceConnectionProvider(
    private val pool: DataSource,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO,
    private val openTelemetry: OpenTelemetry? = null,
    poolMaxSize: Int = Int.MAX_VALUE,
) : JdbcConnectionProvider {

    private val semaphore = Semaphore(poolMaxSize)

    override suspend fun acquire(): Connection {
        coroutineContext.ensureActive() // fail-fast if current coroutine was cancelled before acquiring a connection

        // acquire a permit before acquiring a connection from the pool,
        // so we can be sure that won't just reserve a thread and wait for a connection to be available
        openTelemetry.withSpan(spanName = "semaphore-acquire", parameters = {
            setAttribute("semaphore.availablePermits", semaphore.availablePermits.toLong())
        }) {
            semaphore.acquire()
        }

        // here are 2 issues to solve:
        // 1. if the current coroutine is cancelled and we don't have NonCancellable at all,
        //    the resulting connection will be discarded even if there is really nothing to cancel in this function
        // 2. even if we put NonCancellable in the context, but also change a dispatcher,
        //    the resulting connection will still be discarded, because withContext has a prompt cancellation guarantee,
        //    withContext will still throw a CancellationException regardless of the presence of NonCancellable,
        //    if the current coroutine is cancelled before withContext starts executing the block
        //
        // see https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html
        return try {
            withContext(NonCancellable) { // do not change a dispatcher when doing NonCancellable
                withContext(blockingJdbcContext) { // change a dispatcher separately
                    openTelemetry.withSpan("connection-acquire") {
                        pool.connection
                    }
                }
            }
        } catch (t: Throwable) {
            // if we failed to acquire a connection, we should release the permit, otherwise it will be leaked
            semaphore.release()
            throw t
        }
    }

    override suspend fun release(connection: Connection) =
        // If we close current coroutine (by service/http/call/etc timeout f.e.) -
        // we can't call withContext() block, because it will throw an exception.
        try {
            withContext(NonCancellable) { // do not change a dispatcher when doing NonCancellable
                withContext(blockingJdbcContext) { // change a dispatcher separately
                    openTelemetry.withSpan("connection-release") {
                        connection.close()
                    }
                }
            }
        } finally {
            semaphore.release() // release the permit after closing the connection
        }
}
