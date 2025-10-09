package com.razz.eva.persistence.jdbc

import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection

class HikariCachingPgDataSource(
    private val cacheSize: Int = 1024,
    private val preparedStatementCacheSqlFilter: (String) -> Boolean = { true },
) : PGSimpleDataSource() {

    override fun getConnection(): Connection {
        return CachingConnection(
            delegate = super.getConnection(),
            preparedStatementCache = LruCache(cacheSize),
            preparedStatementCacheSqlFilter = preparedStatementCacheSqlFilter,
        )
    }

    override fun getConnection(user: String?, password: String?): Connection {
        return CachingConnection(
            delegate = super.getConnection(user, password),
            preparedStatementCache = LruCache(cacheSize),
            preparedStatementCacheSqlFilter = preparedStatementCacheSqlFilter,
        )
    }
}
