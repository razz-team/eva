package com.razz.eva.uow.func

import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.jdbc.HikariPoolConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import com.razz.eva.persistence.jdbc.executor.JdbcQueryExecutor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.asCoroutineDispatcher
import java.time.Duration
import java.util.concurrent.Executors.newFixedThreadPool

class JdbcPersistenceModule(
    primaryConfig: DatabaseConfig,
    replicaConfig: DatabaseConfig
) : PersistenceModule() {

    private val primaryPool = poolProvider(primaryConfig, true)
    private val replicaPool = poolProvider(replicaConfig, false)
    private val blockingJdbcContext = newFixedThreadPool(primaryConfig.maxPoolSize.value()).asCoroutineDispatcher()
    private val primaryProvider = HikariPoolConnectionProvider(primaryPool, blockingJdbcContext)
    private val replicaProvider = HikariPoolConnectionProvider(replicaPool, blockingJdbcContext)

    override val transactionManager = JdbcTransactionManager(primaryProvider, replicaProvider, blockingJdbcContext)
    override val queryExecutor = JdbcQueryExecutor(transactionManager)

    override fun close() {
        primaryPool.close()
        replicaPool.close()
        blockingJdbcContext.close()
    }

    companion object Provider {

        fun poolProvider(config: DatabaseConfig, isPrimary: Boolean): HikariDataSource =
            HikariConfig().run {
                idleTimeout = Duration.ofMinutes(1).toMillis()
                jdbcUrl = config.jdbcURL.toString() + if (isPrimary) {
                    "?targetServerType=master"
                } else {
                    "?targetServerType=preferSlave&loadBalanceHosts=true"
                }
                username = config.user.toString()
                password = config.password.showPassword()
                maximumPoolSize = config.maxPoolSize.value()
                leakDetectionThreshold = 3000L
                HikariDataSource(this)
            }
    }
}
