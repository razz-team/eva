package com.razz.eva.persistence.vertx

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.Pool

class PgPoolConnectionProvider(
    private val pool: Pool
) : PgConnectionProvider {

    override suspend fun acquire(): PgConnection {
        return pool.connection.coAwait() as PgConnection
    }

    override suspend fun release(connection: PgConnection) {
        connection.close().coAwait()
    }
}
