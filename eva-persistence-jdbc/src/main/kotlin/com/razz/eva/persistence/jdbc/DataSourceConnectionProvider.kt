package com.razz.eva.persistence.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource

typealias HikariPoolConnectionProvider = DataSourceConnectionProvider

class DataSourceConnectionProvider(
    private val pool: DataSource,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO
) : JdbcConnectionProvider {

    override suspend fun acquire(): Connection {
        // if withContext is cancelled, even if the inner coroutine will continue running, no value will be returned
        // therefore need to add NonCancellable, so that the created connection _is_ returned
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
