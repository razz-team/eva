package com.razz.eva.persistence.jdbc

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ShardingKey

internal class CachingConnection(
    private val delegate: Connection,
    private val preparedStatementCache: LruCache<String, CachedPreparedStatement>,
    private val preparedStatementCacheSqlFilter: (String) -> Boolean,
) : Connection by delegate {

    override fun prepareStatement(sql: String): PreparedStatement {
        if (!preparedStatementCacheSqlFilter(sql)) {
            return delegate.prepareStatement(sql)
        }
        val (added, evicted) = preparedStatementCache.cache(sql) { _ ->
            val delegated = delegate.prepareStatement(sql)
            CachedPreparedStatement(delegated)
        }
        evicted.forEach { it.recycle() }
        return added
    }

    override fun close() {
        preparedStatementCache.forEach { (_, value) -> value.recycle() }
        delegate.close()
    }

    // Default methods are not delegated

    override fun beginRequest() {
        delegate.beginRequest()
    }

    override fun endRequest() {
        delegate.endRequest()
    }

    override fun setShardingKeyIfValid(
        shardingKey: ShardingKey?,
        superShardingKey: ShardingKey?,
        timeout: Int,
    ): Boolean {
        return delegate.setShardingKeyIfValid(shardingKey, superShardingKey, timeout)
    }

    override fun setShardingKeyIfValid(shardingKey: ShardingKey?, timeout: Int): Boolean {
        return delegate.setShardingKeyIfValid(shardingKey, timeout)
    }

    override fun setShardingKey(shardingKey: ShardingKey?, superShardingKey: ShardingKey?) {
        delegate.setShardingKey(shardingKey, superShardingKey)
    }

    override fun setShardingKey(shardingKey: ShardingKey?) {
        delegate.setShardingKey(shardingKey)
    }
}
