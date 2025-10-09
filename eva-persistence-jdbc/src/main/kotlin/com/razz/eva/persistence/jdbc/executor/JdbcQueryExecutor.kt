package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.tracing.QueryTracingListenerProvider
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.OpenTelemetry.noop
import java.sql.Connection
import org.jooq.DMLQuery
import org.jooq.DSLContext
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
    private val transactionManager: TransactionManager<Connection>,
    private val openTelemetry: OpenTelemetry = noop(),
    private val cachePreparedStatements: Boolean,
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>
    ): List<R> {
        return transactionManager.withConnection { connection ->
            dslContext.using(connection).preparedQuery(jooqQuery, table)
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT> {
        jooqQuery.setReturning()
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            dslContext.using(connection).preparedQuery(jooqQuery, table)
        }
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int {
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            dslContext.using(connection).run {
                execute(
                    render(jooqQuery),
                    *extractParams(jooqQuery)
                        .values
                        .filterNot(Param<*>::isInline)
                        .toTypedArray()
                )
            }
        }
    }

    /**
     * If `cachePreparedStatements` is true, we are delegating the statement lifecycle to the connection pool
     * We are instructing jooq to keep the statement open after the execution
     * https://www.jooq.org/doc/latest/manual/sql-execution/reusing-statements/
     * In that case, jooq will create an instance of `CloseableResultQuery`
     * which is an implementation of `java.lang.AutoCloseable`. We are not closing it, so the statement remains open.
     * The connection pool will close the statement when the connection is closed (returned to the pool)
     * If `cachePreparedStatements` is false, jooq will close the statement after execution
     * as it is the default behavior.
     */
    private fun <R : Record> DSLContext.preparedQuery(
        jooqQuery: Query,
        table: Table<R>,
    ): List<R> = resultQuery(
        render(jooqQuery),
        *extractParams(jooqQuery)
            .values
            .filterNot(Param<*>::isInline)
            .toTypedArray()
    ).keepStatement(cachePreparedStatements)
        .coerce(table)
        .fetch()

    override fun getConstraintName(ex: DataAccessException): String? {
        return ex.getCause(PSQLException::class.java)?.serverErrorMessage?.constraint
    }

    private fun DSLContext.using(connection: Connection): DSLContext {
        val configWithConnection = configuration()
            .derive(connection)
            .derive(settings())
            .derive(QueryTracingListenerProvider(openTelemetry))

        return DSL.using(configWithConnection)
    }
}
