package com.razz.eva.examples.wallet

import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.jdbc.HikariPoolConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import com.razz.eva.persistence.jdbc.dataSource
import com.razz.eva.persistence.jdbc.executor.JdbcQueryExecutor
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.hasRepo
import com.razz.eva.serialization.json.JsonFormat.json
import com.razz.eva.uow.Clocks
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.serialization.kotlinx.Serialization
import com.razz.eva.uow.withFactory
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.opentracing.noop.NoopTracerFactory
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
    val transactionManager = JdbcTransactionManager(
        primaryProvider = HikariPoolConnectionProvider(dataSource(databaseConfig, isPrimary = true)),
        replicaProvider = HikariPoolConnectionProvider(dataSource(databaseConfig, isPrimary = false)),
        blockingJdbcContext = newFixedThreadPool(databaseConfig.maxPoolSize.value()).asCoroutineDispatcher()
    )
    val queryExecutor = JdbcQueryExecutor(transactionManager)
    val dslContext: DSLContext = DSL.using(
        POSTGRES,
        Settings().withRenderNamedParamPrefix("$").withParamType(ParamType.NAMED)
    )

    /**
     * Persisting definition
     */
    val tracer = NoopTracerFactory.create()
    val walletRepo = WalletRepository(queryExecutor, dslContext)
    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = ModelRepos(Wallet::class hasRepo walletRepo),
        eventRepository = JooqEventRepository(queryExecutor, dslContext, tracer),
        encoders = { p ->
            when (p) {
                is com.razz.eva.uow.serialization.kotlinx.UowParams<*> -> Serialization.Encoder(json)
                else -> com.razz.eva.uow.Serialization.Encoder.NOOP
            }
        },
    )

    /**
     * Unit of work executor definition
     */
    val clock = Clock.tickMillis(UTC)
    private fun frozenClock(): Clock = Clocks.fixedUTC(clock.instant())

    val uowx: UnitOfWorkExecutor = UnitOfWorkExecutor(
        persisting = persisting,
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry(),
        factories = listOf(
            CreateWalletUow::class withFactory { CreateWalletUow(walletRepo, frozenClock()) }
        )
    )
}
