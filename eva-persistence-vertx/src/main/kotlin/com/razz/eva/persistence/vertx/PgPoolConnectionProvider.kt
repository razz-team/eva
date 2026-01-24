package com.razz.eva.persistence.vertx

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.Pool
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PgPoolConnectionProvider(
    private val pool: Pool,
) : PgConnectionProvider {

    override suspend fun acquire(): PgConnection {
        return withContext(NonCancellable) {
            pool.connection.coAwait() as PgConnection
        }
    }

    override suspend fun release(connection: PgConnection) {
        withContext(NonCancellable) {
            connection.close().coAwait()
        }
    }
}
