package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import org.jooq.DSLContext
import org.jooq.DeleteQuery
import org.jooq.Param
import org.jooq.Query
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException

class JdbcQueryExecutor(
    private val transactionManager: JdbcTransactionManager,
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R> {
        return transactionManager.withConnection { connection ->
            DSL.using(connection, dslContext.settings())
                .preparedQuery(jooqQuery, table)
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT> {
        jooqQuery.setReturning()
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            DSL.using(connection, dslContext.settings())
                .preparedQuery(jooqQuery, table)
        }
    }

    override suspend fun <R : Record> executeDelete(
        dslContext: DSLContext,
        jooqQuery: DeleteQuery<R>,
        table: Table<R>,
    ): Int {
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            DSL.using(connection, dslContext.settings()).run {
                preparedQuery(jooqQuery).execute()
            }
        }
    }

    private fun <R : Record> DSLContext.preparedQuery(
        jooqQuery: Query,
        table: Table<R>,
    ): List<R> = preparedQuery(jooqQuery).coerce(table).fetch()

    private fun DSLContext.preparedQuery(jooqQuery: Query) = resultQuery(
        render(jooqQuery),
        *extractParams(jooqQuery)
            .values
            .filterNot(Param<*>::isInline)
            .toTypedArray()
    )

    override fun getConstraintName(e: DataAccessException): String? {
        return e.getCause(PSQLException::class.java)?.serverErrorMessage?.constraint
    }
}
