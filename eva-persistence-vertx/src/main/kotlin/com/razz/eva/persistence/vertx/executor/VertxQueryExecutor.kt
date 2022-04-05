package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.vertx.core.json.Json
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.impl.ArrayTuple
import org.jooq.Converter
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSON
import org.jooq.JSONB
import org.jooq.Query
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.exception.DataAccessException
import org.jooq.impl.SQLDataType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class VertxQueryExecutor(
    private val transactionManager: VertxTransactionManager
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R> {
        return transactionManager.withConnection { connection ->
            val rows = executeQuery(connection, dslContext, jooqQuery, fields, recordType)
            rows.toList()
        }
    }

    override suspend fun <R : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R> {
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            jooqQuery.setReturning()
            val rows = executeQuery(connection, dslContext, jooqQuery, fields, recordType)
            rows.toList()
        }
    }

    private suspend inline fun <R : Record> executeQuery(
        connection: PgConnection,
        dslContext: DSLContext,
        jooqQuery: Query,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ) = connection.preparedQuery(dslContext.renderNamedParams(jooqQuery)).mapping {
        convertRowToRecord(dslContext, it, fields, recordType)
    }.execute(bindParams(dslContext, jooqQuery)).await()

    private fun bindParams(
        dslContext: DSLContext,
        jooqQuery: Query
    ) = ArrayTuple(
        dslContext.extractParams(jooqQuery).values.filterNot { it.isInline }.map { bound ->
            when (val value = bound.value) {
                is JSON -> Json.decodeValue(value.data())
                is JSONB -> Json.decodeValue(value.data())
                is Instant -> LocalDateTime.ofInstant(value, UTC)
                is LocalDate -> value
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val converter = bound.converter as Converter<Any, Any>
                    converter.to(value)
                }
            }
        }
    )

    private fun <R : Record?> convertRowToRecord(
        dslContext: DSLContext,
        row: Row,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): R {
        val values = arrayOfNulls<Any>(fields.size)
        for (i in fields.indices) {
            val field = fields[i]
            values[i] = when (field.dataType.sqlDataType) {
                SQLDataType.JSON -> row.getJson(i)?.let { JSON.json(it.toString()) }
                SQLDataType.JSONB -> row.getJson(i)?.let { JSONB.jsonb(it.toString()) }
                SQLDataType.TIMESTAMP -> row.getLocalDateTime(i)?.toInstant(UTC)
                SQLDataType.DATE -> row.getLocalDate(i)
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val converter = field.converter as Converter<Any, Any>
                    converter.from(row.get(converter.fromType(), i))
                }
            }
        }

        val record = dslContext.newRecord(fields)
        record.fromArray(*values)
        record.changed(false)
        return record.into(recordType)
    }

    override fun getExceptionMessage(e: DataAccessException): String? = null
}
