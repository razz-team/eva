package com.razz.eva.persistence.jdbc

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

class HikariPoolConnectionProvider(
    private val pool: HikariDataSource,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO
) : JdbcConnectionProvider {

    override suspend fun acquire(): Connection {
        return withContext(blockingJdbcContext) {
            pool.connection
        }
    }

    override suspend fun release(connection: Connection) =
        withContext(blockingJdbcContext) {
            connection.close()
        }
}
