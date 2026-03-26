package com.razz.eva.examples.wallet

import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.jdbc.DataSourceConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import com.razz.eva.persistence.jdbc.dataSource
import com.razz.eva.persistence.jdbc.executor.JdbcQueryExecutor
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import com.razz.eva.uow.withFactory
import io.opentelemetry.api.OpenTelemetry.noop
import kotlinx.coroutines.asCoroutineDispatcher
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.time.Clock
import java.time.ZoneOffset.UTC
import java.util.concurrent.Executors.newFixedThreadPool

class WalletModule(databaseConfig: DatabaseConfig) {

    /**
     * Query executor definition
     */
    val primaryMaxPoolSize = databaseConfig.maxPoolSize.value() // in this example primary and replica have the same size
    val replicaMaxPoolSize = databaseConfig.maxPoolSize.value()

    // dispatcher must have at least primary+replica number of threads, otherwise it will cause deadlocks
    val dispatcher = newFixedThreadPool(primaryMaxPoolSize + replicaMaxPoolSize).asCoroutineDispatcher()
    val transactionManager = JdbcTransactionManager(
        primaryProvider = DataSourceConnectionProvider(
            pool = dataSource(databaseConfig, isPrimary = true),
            blockingJdbcContext = dispatcher,
            poolMaxSize = primaryMaxPoolSize
        ),
        replicaProvider = DataSourceConnectionProvider(
            pool = dataSource(databaseConfig, isPrimary = false),
            blockingJdbcContext = dispatcher,
            poolMaxSize = replicaMaxPoolSize
        ),
        blockingJdbcContext = dispatcher,
    )
    val queryExecutor = JdbcQueryExecutor(transactionManager, noop())
    val dslContext: DSLContext = DSL.using(
        POSTGRES,
        Settings().withRenderNamedParamPrefix("$").withParamType(ParamType.NAMED),
    )

    /**
     * Persisting definition
     */
    val walletRepo = WalletRepository(queryExecutor, dslContext)
    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = ModelRepos(Wallet::class hasRepo walletRepo),
        entityRepos = EntityRepos(),
        eventRepository = JooqEventRepository(queryExecutor, dslContext, noop()),
        paramsSerializer = KotlinxParamsSerializer(),
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
            CreateWalletUow::class withFactory { executionContext -> CreateWalletUow(walletRepo, executionContext) },
        ),
    )
}
