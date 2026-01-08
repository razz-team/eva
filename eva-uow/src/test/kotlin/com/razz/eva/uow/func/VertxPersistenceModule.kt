package com.razz.eva.uow.func

import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxTransactionManager
import com.razz.eva.persistence.vertx.executor.VertxQueryExecutor
import io.vertx.core.Vertx.vertx
import io.vertx.core.VertxOptions
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import java.util.function.Predicate

class VertxPersistenceModule(
    primaryConfig: DatabaseConfig,
    replicaConfig: DatabaseConfig
) : PersistenceModule() {

    private val primaryPool = poolProvider(primaryConfig, true)
    private val replicaPool = poolProvider(replicaConfig, false)
    private val primaryProvider = PgPoolConnectionProvider(primaryPool)
    private val replicaProvider = PgPoolConnectionProvider(replicaPool)

    override val transactionManager = VertxTransactionManager(primaryProvider, replicaProvider)
    override val queryExecutor = VertxQueryExecutor(transactionManager)

    override fun close() {
        primaryPool.close()
        replicaPool.close()
    }

    companion object Provider {

        fun poolProvider(config: DatabaseConfig, isPrimary: Boolean): Pool {
            val vertx = vertx(VertxOptions())
            check(config.nodes.size == 1 || !isPrimary) {
                "Primary pool must be configured with single db node"
            }
            val options = config.nodes.map { node ->
                PgConnectOptions().apply {
                    cachePreparedStatements = true
                    preparedStatementCacheMaxSize = 2048
                    preparedStatementCacheSqlFilter = Predicate { sql -> sql.length < 10_000 }
                    pipeliningLimit = 256
                    user = config.user.toString()
                    password = config.password.showPassword()
                    host = node.host()
                    database = config.name.toString()
                    port = node.port()
                }
            }
            return PgBuilder
                .pool()
                .connectingTo(options)
                .with(PoolOptions().apply { maxSize = config.maxPoolSize.value() })
                .using(vertx)
                .build()
        }
    }
}
