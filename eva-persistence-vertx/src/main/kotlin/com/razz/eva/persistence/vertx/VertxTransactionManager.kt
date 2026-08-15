package com.razz.eva.persistence.vertx

import com.razz.eva.persistence.Connected
import com.razz.eva.persistence.ConnectionProvider
import com.razz.eva.persistence.ConnectionWrapper
import com.razz.eva.persistence.TransactionManager
import io.vertx.pgclient.PgConnection
import kotlin.coroutines.coroutineContext

class VertxTransactionManager(
    primaryProvider: ConnectionProvider<PgConnection>,
    replicaProvider: ConnectionProvider<PgConnection>,
) : TransactionManager<PgConnection>(primaryProvider, replicaProvider) {

    override fun wrapConnection(connected: Connected<PgConnection>): ConnectionWrapper<PgConnection> =
        VertxConnectionElement(connected)

    override suspend fun ctxConnection(): Connected<PgConnection>? =
        coroutineContext[VertxConnectionElement]?.connected

    override fun supportsPipelining(): Boolean = true
}
