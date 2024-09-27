package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.tracing.QueryTracingListenerProvider
import com.razz.eva.tracing.use
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.OpenTelemetry.noop
import io.opentelemetry.api.trace.Span
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
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
        tag: String?,
    ): List<R> {
        return span(tag ?: "JdbcQueryExecutor::select::${table.name}").use {
            transactionManager.withConnection { connection ->
                dslContext.using(connection)
                    .preparedQuery(jooqQuery, table)
            }
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
        tag: String?
    ): List<ROUT> {
        jooqQuery.setReturning()
        return span(tag ?: "JdbcQueryExecutor::store::${table.name}").use {
            transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
                dslContext.using(connection)
                    .preparedQuery(jooqQuery, table)
            }
        }
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
        tag: String?,
    ): Int {
        return span(tag ?: "JdbcQueryExecutor").use {
            transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
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
    }

    private fun <R : Record> DSLContext.preparedQuery(
        jooqQuery: Query,
        table: Table<R>,
    ): List<R> = resultQuery(
        render(jooqQuery),
        *extractParams(jooqQuery)
            .values
            .filterNot(Param<*>::isInline)
            .toTypedArray()
    ).coerce(table).fetch()

    override fun getConstraintName(ex: DataAccessException): String? {
        return ex.getCause(PSQLException::class.java)?.serverErrorMessage?.constraint
    }

    private fun DSLContext.using(connection: Connection): DSLContext {
        val newContext = DSL.using(connection, settings())
        newContext.configuration().set(QueryTracingListenerProvider(openTelemetry))
        return newContext
    }

    private fun span(name: String) = Span.current()?.let {
        openTelemetry
            .getTracer("eva")
            .spanBuilder(name)
            .startSpan()
    }
}
