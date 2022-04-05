package com.razz.eva.uow

import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.io.Closeable

abstract class PersistenceModule : Closeable {

    abstract val queryExecutor: QueryExecutor

    open val dslContext: DSLContext = DSL.using(
        SQLDialect.POSTGRES,
        Settings().withRenderNamedParamPrefix("$").withParamType(ParamType.NAMED)
    )

    abstract val transactionManager: TransactionManager<*>
}
