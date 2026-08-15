package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.ConnectionException
import com.razz.eva.persistence.PersistenceException.ModelPersistingGenericException
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.executor.QueryExecutor.Companion.matchReturning
import com.razz.eva.persistence.executor.QueryExecutor.Constraint
import com.razz.eva.persistence.postgres.PgHelpers.PG_CONNECTION_UNAVAILABLE
import com.razz.eva.persistence.postgres.PgHelpers.PG_UNIQUE_VIOLATION
import com.razz.eva.persistence.executor.QueryExecutor.Companion.operationName
import com.razz.eva.persistence.executor.QueryExecutor.Companion.queryTarget
import com.razz.eva.tracing.DatabaseSpans
import com.razz.eva.tracing.DatabaseSpans.setServer
import com.razz.eva.tracing.use
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.OpenTelemetry.noop
import kotlinx.coroutines.withContext
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Param
import org.jooq.Query
import org.jooq.Record
import org.jooq.ResultQuery
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLStateClass.C08_CONNECTION_EXCEPTION
import org.jooq.exception.SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION
import org.jooq.exception.SQLStateClass.C40_TRANSACTION_ROLLBACK
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException
import java.sql.Connection
import kotlin.coroutines.EmptyCoroutineContext

class JdbcQueryExecutor(
    private val transactionManager: TransactionManager<Connection>,
    private val openTelemetry: OpenTelemetry = noop(),
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R> {
        val sql = dslContext.render(jooqQuery)
        return traced(jooqQuery, table, sql) { span ->
            transactionManager.withConnection { connected ->
                span?.setServer(connected.endpoint.address, connected.endpoint.port, connected.endpoint.database, connected.role?.name)
                val connection = connected.value
                dslContext.using(connection).preparedQuery(sql, jooqQuery).coerce(table).fetch()
            }
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
        returning: Collection<Field<*>>?,
    ): List<ROUT> {
        val fields = returning?.let { matchReturning(jooqQuery, it) }
        // The returning clause is part of the statement, so it has to be set before the SQL is rendered
        // for both the span attribute and the execution.
        if (fields == null) jooqQuery.setReturning() else jooqQuery.setReturning(fields)
        val sql = dslContext.render(jooqQuery)
        return traced(jooqQuery, table, sql) { span ->
            transactionManager.inTransaction(REQUIRE_EXISTING) { connected ->
                span?.setServer(connected.endpoint.address, connected.endpoint.port, connected.endpoint.database, connected.role?.name)
                val connection = connected.value
                val prepared = dslContext.using(connection).preparedQuery(sql, jooqQuery)
                if (fields == null) {
                    prepared.coerce(table).fetch()
                } else {
                    prepared.coerce(fields).fetch().map { it.into(table) }
                }
            }
        }
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int {
        val sql = dslContext.render(jooqQuery)
        return traced(jooqQuery, null, sql) { span ->
            transactionManager.inTransaction(REQUIRE_EXISTING) { connected ->
                span?.setServer(connected.endpoint.address, connected.endpoint.port, connected.endpoint.database, connected.role?.name)
                val connection = connected.value
                dslContext.using(connection).run {
                    execute(sql, *bindValues(jooqQuery))
                }
            }
        }
    }

    private fun DSLContext.preparedQuery(
        sql: String,
        jooqQuery: Query,
    ): ResultQuery<Record> = resultQuery(sql, *bindValues(jooqQuery))

    private fun DSLContext.bindValues(jooqQuery: Query): Array<Any?> = extractParams(jooqQuery)
        .values
        .filterNot(Param<*>::isInline)
        .toTypedArray()

    override fun extractConstraintName(ex: Exception): Constraint? {
        val dataAccessException = ex as? DataAccessException ?: return null
        val name = dataAccessException.getCause(PSQLException::class.java)?.serverErrorMessage?.constraint
        return Constraint(name)
    }

    override fun extractUniqueConstraintName(ex: Exception, table: Table<*>): Constraint? {
        if ((ex as? DataAccessException)?.sqlState() != PG_UNIQUE_VIOLATION) {
            return null
        }

        val constraintName = extractConstraintName(ex)
        return if (table.comment == "PARTITIONED") {
            constraintName
        } else {
            val nonPartitionedConstraint = table.keys.firstOrNull { it.name == constraintName?.name }?.name
                ?: table.indexes.firstOrNull { it.unique && it.name == constraintName?.name }?.name
            Constraint(nonPartitionedConstraint)
        }
    }

    override fun extractModelException(ex: Exception, table: Table<*>, modelId: ModelId<*>): PersistenceException? {
        val dae = ex as? DataAccessException ?: return null
        return when {
            dae.sqlState() == PG_UNIQUE_VIOLATION -> UniqueModelRecordViolationException(
                modelId = modelId,
                tableName = table.name,
                constraintName = extractUniqueConstraintName(dae, table)?.name,
            )
            dae.sqlStateClass() == C23_INTEGRITY_CONSTRAINT_VIOLATION -> ModelRecordConstraintViolationException(
                modelId = modelId,
                tableName = table.name,
                constraintName = extractConstraintName(dae)?.name,
            )
            // https://www.postgresql.org/message-id/flat/CANbGkDhq9gZnEouo2PZHP3HGMAJKk7fZf3eU3Q8g46Y-1uGZ-w%40mail.gmail.com#e5de345d77abe0184e394f0701bb8bc5
            //  According to the thread above, transaction error with message message
            //  "tuple to be locked was already moved to another partition due to concurrent update"
            //  is thrown when a record was moved to another partition in transaction T1,
            //  and concurrent transaction T0 is trying to update the same record.
            //  This should not cause transaction rollback in T0 due to serialisation error,
            //  rather we should fail due to version mismatch (stale record).
            dae.sqlStateClass() == C40_TRANSACTION_ROLLBACK -> StaleRecordException(modelId, table.name)
            dae.connectionUnavailable() -> ConnectionException(ex)
            else -> ModelPersistingGenericException(modelId, ex)
        }
    }

    override fun extractConnectionException(ex: Exception): ConnectionException? {
        val dae = ex as? DataAccessException ?: return null
        return if (dae.connectionUnavailable()) ConnectionException(ex) else null
    }

    private fun DataAccessException.connectionUnavailable(): Boolean =
        sqlStateClass() == C08_CONNECTION_EXCEPTION || sqlState() in PG_CONNECTION_UNAVAILABLE

    private fun DSLContext.using(connection: Connection): DSLContext {
        val configWithConnection = configuration()
            .derive(connection)
            .derive(settings())

        return DSL.using(configWithConnection)
    }

    private suspend fun <T> traced(
        jooqQuery: Query,
        table: Table<*>?,
        sql: String,
        block: suspend (Span?) -> T,
    ): T {
        if (!DatabaseSpans.tracing()) {
            return block(null)
        }
        // The span is built inside withContext, because withContext runs ensureActive first and a span
        // built outside it would never end for a call cancelled before it starts.
        return withContext(EmptyCoroutineContext) {
            val span = DatabaseSpans.querySpan(
                openTelemetry = openTelemetry,
                operation = operationName(jooqQuery),
                target = table?.name ?: queryTarget(jooqQuery),
                sql = sql,
            )
            span.use {
                block(span)
            }
        }
    }
}
