package com.razz.eva.uow.func

import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.config.ExecutorType

open class TransactionalModule(
    dbConfig: DatabaseConfig,
) : PersistenceModule() {

    private val persistenceModule: PersistenceModule = when (dbConfig.executorType) {
        ExecutorType.VERTX -> VertxPersistenceModule(dbConfig, dbConfig)
        ExecutorType.JDBC -> JdbcPersistenceModule(dbConfig, dbConfig)
    }

    override val queryExecutor = persistenceModule.queryExecutor
    override val dslContext = persistenceModule.dslContext
    override val transactionManager = persistenceModule.transactionManager

    override fun close() {
        persistenceModule.close()
    }
}
