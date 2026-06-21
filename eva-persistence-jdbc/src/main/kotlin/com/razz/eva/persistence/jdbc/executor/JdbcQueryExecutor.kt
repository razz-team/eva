package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.ModelPersistingGenericException
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.postgres.PgHelpers.PG_UNIQUE_VIOLATION
import com.razz.eva.tracing.QueryTracingListenerProvider
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.OpenTelemetry.noop
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Param
import org.jooq.Query
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION
import org.jooq.exception.SQLStateClass.C40_TRANSACTION_ROLLBACK
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException
import java.sql.Connection

class JdbcQueryExecutor(
    private val transactionManager: TransactionManager<Connection>,
    private val openTelemetry: OpenTelemetry = noop(),
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
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
                        .toTypedArray(),
                )
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
            .toTypedArray(),
    ).coerce(table).fetch()

    override fun extractConstraintName(ex: Exception): String? {
        val dataAccessException = ex as? DataAccessException ?: return null
        return dataAccessException.getCause(PSQLException::class.java)?.serverErrorMessage?.constraint
    }

    override fun extractUniqueConstraintName(ex: Exception, table: Table<*>): String? {
        if ((ex as? DataAccessException)?.sqlState() != PG_UNIQUE_VIOLATION) {
            return null
        }

        val constraintName = extractConstraintName(ex)
        return if (table.comment == "PARTITIONED") {
            constraintName
        } else {
            table.keys.firstOrNull { it.name == constraintName }?.name
                ?: table.indexes.firstOrNull { it.unique && it.name == constraintName }?.name
        }
    }

    override fun extractModelException(ex: Exception, table: Table<*>, modelId: ModelId<*>): PersistenceException {
        val dae = ex as? DataAccessException
        return when {
            dae?.sqlState() == PG_UNIQUE_VIOLATION -> UniqueModelRecordViolationException(
                modelId = modelId,
                tableName = table.name,
                constraintName = extractUniqueConstraintName(dae, table),
            )

            dae?.sqlStateClass() == C23_INTEGRITY_CONSTRAINT_VIOLATION -> ModelRecordConstraintViolationException(
                modelId = modelId,
                tableName = table.name,
                constraintName = extractConstraintName(dae),
            )

            // https://www.postgresql.org/message-id/flat/CANbGkDhq9gZnEouo2PZHP3HGMAJKk7fZf3eU3Q8g46Y-1uGZ-w%40mail.gmail.com#e5de345d77abe0184e394f0701bb8bc5
            //  According to the thread above, transaction error with message message
            //  "tuple to be locked was already moved to another partition due to concurrent update"
            //  is thrown when a record was moved to another partition in transaction T1,
            //  and concurrent transaction T0 is trying to update the same record.
            //  This should not cause transaction rollback in T0 due to serialisation error,
            //  rather we should fail due to version mismatch (stale record).
            dae?.sqlStateClass() == C40_TRANSACTION_ROLLBACK -> StaleRecordException(modelId, table.name)

            else -> ModelPersistingGenericException(modelId, ex)
        }
    }

    private fun DSLContext.using(connection: Connection): DSLContext {
        val configWithConnection = configuration()
            .derive(connection)
            .derive(settings())
            .derive(QueryTracingListenerProvider(openTelemetry))

        return DSL.using(configWithConnection)
    }
}
