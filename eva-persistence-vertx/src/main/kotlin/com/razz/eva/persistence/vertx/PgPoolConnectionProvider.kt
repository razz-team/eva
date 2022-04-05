package com.razz.eva.persistence.vertx

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnection
import io.vertx.pgclient.PgPool

class PgPoolConnectionProvider(
    private val pool: PgPool
) : PgConnectionProvider {

    override suspend fun acquire(): PgConnection {
        return pool.connection.await() as PgConnection
    }

    override suspend fun release(connection: PgConnection) {
        connection.close().await()
    }
}
