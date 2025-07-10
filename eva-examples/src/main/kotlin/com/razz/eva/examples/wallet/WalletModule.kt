package com.razz.eva.examples.wallet

import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.jdbc.HikariPoolConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import com.razz.eva.persistence.jdbc.dataSource
import com.razz.eva.persistence.jdbc.executor.JdbcQueryExecutor
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.withFactory
import io.opentelemetry.api.OpenTelemetry.noop
import java.time.Clock
import java.time.ZoneOffset.UTC
import java.util.concurrent.Executors.newFixedThreadPool
import kotlinx.coroutines.asCoroutineDispatcher
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class WalletModule(databaseConfig: DatabaseConfig) {

    /**
     * Query executor definition
     */
    val transactionManager = JdbcTransactionManager(
        primaryProvider = HikariPoolConnectionProvider(dataSource(databaseConfig, isPrimary = true)),
        replicaProvider = HikariPoolConnectionProvider(dataSource(databaseConfig, isPrimary = false)),
        blockingJdbcContext = newFixedThreadPool(databaseConfig.maxPoolSize.value()).asCoroutineDispatcher()
    )
    val queryExecutor = JdbcQueryExecutor(transactionManager, noop())
    val dslContext: DSLContext = DSL.using(
        POSTGRES,
        Settings().withRenderNamedParamPrefix("$").withParamType(ParamType.NAMED)
    )

    /**
     * Persisting definition
     */
    val walletRepo = WalletRepository(queryExecutor, dslContext)
    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = ModelRepos(Wallet::class hasRepo walletRepo),
        eventRepository = JooqEventRepository(queryExecutor, dslContext, noop())
    )

    /**
     * Unit of work executor definition
     */
    val clock = Clock.tickMillis(UTC)

    val uowx: UnitOfWorkExecutor = UnitOfWorkExecutor(
        persisting = persisting,
        openTelemetry = noop(),
        clock = clock,
        factories = listOf(
            CreateWalletUow::class withFactory { executionContext -> CreateWalletUow(walletRepo, executionContext) }
        )
    )
}
