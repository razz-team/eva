package com.razz.eva.persistence.vertx

import com.razz.eva.persistence.ConnectionProvider
import com.razz.eva.persistence.ConnectionWrapper
import com.razz.eva.persistence.DbEndpoint
import com.razz.eva.persistence.PoolRole
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.tracing.PoolAttribution
import io.vertx.pgclient.PgConnection
import kotlin.coroutines.coroutineContext

class VertxTransactionManager(
    primaryProvider: ConnectionProvider<PgConnection>,
    replicaProvider: ConnectionProvider<PgConnection>,
    attribution: PoolAttribution = PoolAttribution.None,
) : TransactionManager<PgConnection>(primaryProvider, replicaProvider, attribution) {

    override fun wrapConnection(
        newConn: PgConnection,
        endpoint: DbEndpoint,
        role: PoolRole?,
    ): ConnectionWrapper<PgConnection> = VertxConnectionElement(newConn, endpoint, role)

    override suspend fun ctxConnection(): ConnectionWrapper<PgConnection>? =
        coroutineContext[VertxConnectionElement]

    override fun supportsPipelining(): Boolean = true
}
