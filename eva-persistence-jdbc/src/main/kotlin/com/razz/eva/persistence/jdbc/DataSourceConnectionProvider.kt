package com.razz.eva.persistence.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource
import kotlin.coroutines.coroutineContext

typealias HikariPoolConnectionProvider = DataSourceConnectionProvider

class DataSourceConnectionProvider(
    private val pool: DataSource,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO
) : JdbcConnectionProvider {

    override suspend fun acquire(): Connection {
        coroutineContext.ensureActive() // explicitly check for cancellation before acquiring a connection

        // if withContext is cancelled, regardless of the inner block result, the cancellation exception will be thrown
        // here there is nothing to cancel, and we do want to get the connection back regardless of the cancellation
        return withContext(blockingJdbcContext + NonCancellable) {
            pool.connection
        }
    }

    override suspend fun release(connection: Connection) =
        // If we close current coroutine (by service/http/call/etc timeout f.e.) -
        // we can't call withContext() block, because it will throw an exception.
        withContext(blockingJdbcContext + NonCancellable) {
            connection.close()
        }
}
