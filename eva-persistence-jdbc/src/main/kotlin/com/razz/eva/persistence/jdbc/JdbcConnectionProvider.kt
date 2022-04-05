package com.razz.eva.persistence.jdbc

import com.razz.eva.persistence.ConnectionProvider
import java.sql.Connection

interface JdbcConnectionProvider : ConnectionProvider<Connection> {

    override suspend fun acquire(): Connection

    override suspend fun release(connection: Connection)
}
