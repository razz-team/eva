package com.razz.eva.persistence.vertx.executor

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
import com.razz.eva.persistence.executor.QueryExecutor.Constraint
import com.razz.eva.persistence.postgres.PgHelpers.PG_CONNECTION_UNAVAILABLE
import com.razz.eva.persistence.postgres.PgHelpers.PG_UNIQUE_VIOLATION
import io.vertx.core.json.Json
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgConnection
import io.vertx.pgclient.PgException
import io.vertx.sqlclient.ClosedConnectionException
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlResult
import io.vertx.sqlclient.Tuple
import io.vertx.sqlclient.impl.ListTuple
import org.jooq.Converter
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSON
import org.jooq.JSONB
import org.jooq.Query
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.SQLStateClass
import org.jooq.exception.SQLStateClass.C08_CONNECTION_EXCEPTION
import org.jooq.exception.SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION
import org.jooq.exception.SQLStateClass.C40_TRANSACTION_ROLLBACK
import org.jooq.impl.SQLDataType
import org.jooq.postgres.extensions.types.Inet
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class VertxQueryExecutor(
    private val transactionManager: TransactionManager<PgConnection>,
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R> {
        return transactionManager.withConnection { connection ->
            val rows = executeQuery(connection, dslContext, jooqQuery, table.fields(), table)
            rows.toList()
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
        returning: Collection<Field<*>>?,
    ): List<ROUT> {
        require(returning == null || returning.isNotEmpty()) {
            "Returning fields must not be empty, use executeQuery for a row count"
        }
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            val fields = if (returning == null) {
                jooqQuery.setReturning()
                table.fields()
            } else {
                jooqQuery.setReturning(returning)
                returning.toTypedArray()
            }
            val rows = executeQuery(connection, dslContext, jooqQuery, fields, table)
            rows.toList()
        }
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int {
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            connection.preparedQuery(dslContext.renderNamedParams(jooqQuery))
                .execute(bindParams(dslContext, jooqQuery)).map(SqlResult<*>::rowCount).coAwait()
        }
    }

    private suspend inline fun <R : Record> executeQuery(
        connection: PgConnection,
        dslContext: DSLContext,
        jooqQuery: Query,
        fields: Array<out Field<*>>,
        table: Table<R>,
    ): RowSet<R> = connection.preparedQuery(dslContext.renderNamedParams(jooqQuery)).mapping { row ->
        convertRowToRecord(dslContext, row, fields, table)
    }.execute(bindParams(dslContext, jooqQuery)).coAwait()

    private fun bindParams(
        dslContext: DSLContext,
        jooqQuery: Query,
    ): ListTuple = ListTuple(
        dslContext.extractParams(jooqQuery).values.filterNot { it.isInline }.map { bound ->
            when (val value = bound.value) {
                is JSON -> Json.decodeValue(value.data()) ?: Tuple.JSON_NULL
                is JSONB -> Json.decodeValue(value.data()) ?: Tuple.JSON_NULL
                is Instant -> LocalDateTime.ofInstant(value, UTC)
                is LocalDate -> value
                is Inet -> io.vertx.pgclient.data.Inet().setAddress(value.address()).setNetmask(value.prefix())
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val converter = bound.converter as Converter<Any, Any>
                    converter.to(value)
                }
            }
        },
    )

    private fun <R : Record> convertRowToRecord(
        dslContext: DSLContext,
        row: Row,
        fields: Array<out Field<*>>,
        table: Table<R>,
    ): R {
        val values = arrayOfNulls<Any>(fields.size)
        for (i in fields.indices) {
            val field = fields[i]
            values[i] = when {
                field.dataType.sqlDataType == SQLDataType.JSON -> row.getJson(i)?.let {
                    if (it == Tuple.JSON_NULL) JSON.json("null") else JSON.json(Json.encode(it))
                }
                field.dataType.sqlDataType == SQLDataType.JSONB -> row.getJson(i)?.let {
                    if (it == Tuple.JSON_NULL) JSONB.jsonb("null") else JSONB.jsonb(Json.encode(it))
                }
                field.dataType.sqlDataType == SQLDataType.TIMESTAMP -> row.getLocalDateTime(i)?.toInstant(UTC)
                field.dataType.sqlDataType == SQLDataType.DATE -> row.getLocalDate(i)
                field.dataType.sqlDataType == SQLDataType.NUMERIC -> row.getBigDecimal(i)
                field.type == Inet::class.java -> (row.getValue(i) as? io.vertx.pgclient.data.Inet)?.let {
                    Inet.inet(it.address, it.netmask)
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val converter = field.converter as Converter<Any, Any>
                    converter.from(row.get(converter.fromType(), i))
                }
            }
        }

        val record = dslContext.newRecord(*fields)
        record.fromArray(*values)
        record.touched(false)
        return record.into(table)
    }

    override fun extractConstraintName(ex: Exception): Constraint? {
        if (ex !is PgException) {
            return null
        }

        return Constraint(ex.constraint)
    }

    override fun extractUniqueConstraintName(ex: Exception, table: Table<*>): Constraint? {
        if (ex !is PgException) {
            return null
        }

        return when (ex.sqlState) {
            PG_UNIQUE_VIOLATION -> Constraint(ex.constraint)
            else -> null
        }
    }

    override fun extractModelException(ex: Exception, table: Table<*>, modelId: ModelId<*>): PersistenceException? {
        return when {
            ex.connectionDead() -> ConnectionException(ex)
            ex !is PgException -> null
            ex.sqlState == PG_UNIQUE_VIOLATION -> UniqueModelRecordViolationException(
                modelId = modelId,
                tableName = table.name,
                constraintName = ex.constraint,
            )
            ex.sqlStateClass == C23_INTEGRITY_CONSTRAINT_VIOLATION -> ModelRecordConstraintViolationException(
                modelId = modelId,
                tableName = table.name,
                constraintName = ex.constraint,
            )
            // https://www.postgresql.org/message-id/flat/CANbGkDhq9gZnEouo2PZHP3HGMAJKk7fZf3eU3Q8g46Y-1uGZ-w%40mail.gmail.com#e5de345d77abe0184e394f0701bb8bc5
            //  According to the thread above, transaction error with message message
            //  "tuple to be locked was already moved to another partition due to concurrent update"
            //  is thrown when a record was moved to another partition in transaction T1,
            //  and concurrent transaction T0 is trying to update the same record.
            //  This should not cause transaction rollback in T0 due to serialisation error,
            //  rather we should fail due to version mismatch (stale record).
            ex.sqlStateClass == C40_TRANSACTION_ROLLBACK -> StaleRecordException(modelId, table.name)
            ex.connectionUnavailable() -> ConnectionException(ex)
            else -> ModelPersistingGenericException(modelId, ex)
        }
    }

    override fun extractConnectionException(ex: Exception): ConnectionException? = when {
        ex.connectionDead() -> ConnectionException(ex)
        ex is PgException && ex.connectionUnavailable() -> ConnectionException(ex)
        else -> null
    }

    // the vertx client has no wrapping layer: a socket death surfaces either as its
    // ClosedConnectionException or as a raw io exception, both without a sql state to match on
    private fun Exception.connectionDead(): Boolean = this is ClosedConnectionException || this is IOException

    private fun PgException.connectionUnavailable(): Boolean =
        sqlStateClass == C08_CONNECTION_EXCEPTION || sqlState in PG_CONNECTION_UNAVAILABLE
}

private val PgException.sqlStateClass get() = SQLStateClass.fromCode(sqlState)
