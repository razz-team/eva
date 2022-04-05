package com.razz.eva.persistence.vertx

import com.razz.eva.persistence.ConnectionProvider
import io.vertx.pgclient.PgConnection

interface PgConnectionProvider : ConnectionProvider<PgConnection> {

    override suspend fun acquire(): PgConnection

    override suspend fun release(connection: PgConnection)
}
