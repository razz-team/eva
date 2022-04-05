package com.razz.eva.persistence.jdbc

import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.persistence.ConnectionProvider
import com.razz.eva.persistence.ConnectionWrapper
import com.razz.eva.persistence.TransactionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import kotlin.coroutines.coroutineContext

class JdbcTransactionManager(
    primaryProvider: ConnectionProvider<Connection>,
    replicaProvider: ConnectionProvider<Connection>,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO
) : TransactionManager<Connection>(primaryProvider, replicaProvider) {

    override suspend fun <R> withConnection(block: suspend (Connection) -> R): R {
        return withContext(blockingJdbcContext) {
            super.withConnection(block)
        }
    }

    override suspend fun <R> inTransaction(mode: ConnectionMode, block: suspend (Connection) -> R): R {
        return withContext(blockingJdbcContext) {
            super.inTransaction(mode, block)
        }
    }

    override fun wrapConnection(newConn: Connection): ConnectionWrapper<Connection> =
        JdbcConnectionElement(newConn)

    override suspend fun ctxConnection(): Connection? =
        coroutineContext[JdbcConnectionElement]?.connection

    override fun supportsPipelining(): Boolean = false
}
