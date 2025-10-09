package com.razz.eva.persistence.jdbc

import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection

class HikariCachingPgDataSource : PGSimpleDataSource() {

    override fun getConnection(): Connection {
        return CachingConnection(super.getConnection())
    }

    override fun getConnection(user: String?, password: String?): Connection {
        return CachingConnection(super.getConnection(user, password))
    }
}
