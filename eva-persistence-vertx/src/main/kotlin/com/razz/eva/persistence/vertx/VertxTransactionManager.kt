package com.razz.eva.persistence.vertx

import com.razz.eva.persistence.ConnectionProvider
import com.razz.eva.persistence.ConnectionWrapper
import com.razz.eva.persistence.TransactionManager
import io.vertx.pgclient.PgConnection
import kotlin.coroutines.coroutineContext

class VertxTransactionManager(
    primaryProvider: ConnectionProvider<PgConnection>,
    replicaProvider: ConnectionProvider<PgConnection>
) : TransactionManager<PgConnection>(primaryProvider, replicaProvider) {

    override fun wrapConnection(newConn: PgConnection): ConnectionWrapper<PgConnection> =
        VertxConnectionElement(newConn)

    override suspend fun ctxConnection(): PgConnection? =
        coroutineContext[VertxConnectionElement]?.connection

    override fun supportsPipelining(): Boolean = true
}
