package com.razz.eva.persistence.jdbc

import com.razz.eva.persistence.ConnectionWrapper
import com.razz.eva.persistence.DbEndpoint
import com.razz.eva.persistence.PoolRole
import java.sql.Connection
import kotlin.coroutines.CoroutineContext

internal class JdbcConnectionElement(
    override val connection: Connection,
    override val endpoint: DbEndpoint,
    override val role: PoolRole?,
) : ConnectionWrapper<Connection> {

    private val initialAutoCommit = connection.autoCommit

    override suspend fun begin() {
        connection.autoCommit = false
    }

    override suspend fun commit() {
        connection.commit()
        connection.autoCommit = initialAutoCommit
    }

    override suspend fun rollback() {
        // Hikari closes (swaps delegating connection to closed one) on SPI error
        // see [com.zaxxer.hikari.pool.ProxyConnection.checkException]
        if (connection.isClosed) return
        connection.rollback()
        connection.autoCommit = initialAutoCommit
    }

    companion object Key : CoroutineContext.Key<JdbcConnectionElement>
    override val key: CoroutineContext.Key<JdbcConnectionElement>
        get() = JdbcConnectionElement
}
