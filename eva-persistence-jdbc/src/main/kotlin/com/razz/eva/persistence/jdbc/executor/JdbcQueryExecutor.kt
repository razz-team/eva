package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Param
import org.jooq.Query
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException

class JdbcQueryExecutor(
    private val transactionManager: JdbcTransactionManager,
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R> {
        return transactionManager.withConnection { connection ->
            DSL.using(connection, dslContext.settings())
                .preparedQuery(jooqQuery, recordType)
        }
    }

    override suspend fun <R : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R> {
        jooqQuery.setReturning()
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            DSL.using(connection, dslContext.settings())
                .preparedQuery(jooqQuery, recordType)
        }
    }

    private fun <R : Record> DSLContext.preparedQuery(
        jooqQuery: Query,
        recordType: Class<out R>
    ) = fetch(
        render(jooqQuery),
        *extractParams(jooqQuery)
            .values
            .filterNot(Param<*>::isInline)
            .toTypedArray()
    ).into(recordType)

    override fun getExceptionMessage(e: DataAccessException): String? {
        return e.getCause(PSQLException::class.java)?.message
    }
}
