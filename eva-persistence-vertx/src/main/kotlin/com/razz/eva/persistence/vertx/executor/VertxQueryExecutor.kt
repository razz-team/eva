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
import com.razz.eva.persistence.executor.QueryExecutor.Companion.matchReturning
import com.razz.eva.persistence.AcquiredEndpoint
import com.razz.eva.persistence.executor.QueryExecutor.Companion.operationName
import com.razz.eva.persistence.executor.QueryExecutor.Companion.queryTarget
import com.razz.eva.persistence.executor.QueryExecutor.Constraint
import com.razz.eva.tracing.DatabaseSpans
import com.razz.eva.tracing.DatabaseSpans.setServer
import com.razz.eva.tracing.use
import com.razz.eva.persistence.postgres.PgHelpers.PG_CONNECTION_UNAVAILABLE
import com.razz.eva.persistence.postgres.PgHelpers.PG_UNIQUE_VIOLATION
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.OpenTelemetry.noop
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
import kotlinx.coroutines.withContext
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
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset.UTC

class VertxQueryExecutor(
    private val transactionManager: TransactionManager<PgConnection>,
    private val openTelemetry: OpenTelemetry = noop(),
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R> {
        val sql = dslContext.renderNamedParams(jooqQuery)
        return traced(jooqQuery, table, sql) {
            transactionManager.withConnection { connection ->
                val rows = executeQuery(connection, dslContext, jooqQuery, sql, table.fields(), table)
                rows.toList()
            }
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
        returning: Collection<Field<*>>?,
    ): List<ROUT> {
        val matched = returning?.let { matchReturning(jooqQuery, it) }
        // The returning clause is part of the statement, so it has to be set before the SQL is rendered
        // for both the span attribute and the execution.
        val fields = if (matched == null) {
            jooqQuery.setReturning()
            table.fields()
        } else {
            jooqQuery.setReturning(matched)
            matched.toTypedArray()
        }
        val sql = dslContext.renderNamedParams(jooqQuery)
        return traced(jooqQuery, table, sql) {
            transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
                val rows = executeQuery(connection, dslContext, jooqQuery, sql, fields, table)
                rows.toList()
            }
        }
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int {
        val sql = dslContext.renderNamedParams(jooqQuery)
        return traced(jooqQuery, null, sql) {
            transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
                connection.preparedQuery(sql)
                    .execute(bindParams(dslContext, jooqQuery)).map(SqlResult<*>::rowCount).coAwait()
            }
        }
    }

    private suspend inline fun <R : Record> executeQuery(
        connection: PgConnection,
        dslContext: DSLContext,
        jooqQuery: Query,
        sql: String,
        fields: Array<out Field<*>>,
        table: Table<R>,
    ): RowSet<R> = connection.preparedQuery(sql).mapping { row ->
        convertRowToRecord(dslContext, row, fields, table)
    }.execute(bindParams(dslContext, jooqQuery)).coAwait()

    private suspend fun <T> traced(
        jooqQuery: Query,
        table: Table<*>?,
        sql: String,
        block: suspend () -> T,
    ): T {
        if (!DatabaseSpans.tracing()) {
            return block()
        }
        val acquired = AcquiredEndpoint()
        // The span is built inside withContext, not before it: withContext runs ensureActive first, so a
        // span created outside would never be ended for a call cancelled before it starts. The manager fills
        // the slot as it goes to a pool, and span.use makes the span current, which is what nests the
        // connection acquisition spans underneath it and records failure on it.
        return withContext(acquired) {
            val span = DatabaseSpans.querySpan(
                openTelemetry = openTelemetry,
                operation = operationName(jooqQuery),
                target = table?.name ?: queryTarget(jooqQuery),
                sql = sql,
            )
            span.use {
                try {
                    block()
                } finally {
                    val endpoint = acquired.endpoint
                    val role = acquired.role
                    if (endpoint != null && role != null) {
                        span.setServer(endpoint.address, endpoint.port, endpoint.database, role.name)
                    }
                }
            }
        }
    }

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
                // Arrays of the types handled above bypass the converter too. Going through it and mapping
                // back would agree with the scalar branches only for a converter that round trips a wall
                // clock, as com.razz.jooq.converter.InstantConverter does; one built on Timestamp.from would
                // disagree by the JVM offset.
                is Array<*> -> value.nativeArray() ?: run {
                    @Suppress("UNCHECKED_CAST")
                    val converter = bound.converter as Converter<Any, Any>
                    javaTimeValue(converter.to(value))
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val converter = bound.converter as Converter<Any, Any>
                    // A forced type such as com.razz.jooq.converter.InstantConverter targets JDBC, so it
                    // hands back a java.sql temporal that the vertx pg client cannot encode. The branches
                    // above cover the same types arriving without a converter; everything converted has to
                    // be mapped across here instead. Arrays land here too.
                    javaTimeValue(converter.to(value))
                }
            }
        },
    )

    // Elements mapped by the same rule the scalar branches use, or null when the component type is not one
    // of those and the jOOQ converter has to run instead.
    private fun Array<*>.nativeArray(): Any? = when (this::class.java.componentType) {
        Instant::class.java -> Array(size) { i -> (this[i] as Instant?)?.let { LocalDateTime.ofInstant(it, UTC) } }
        LocalDate::class.java -> this
        LocalDateTime::class.java -> this
        else -> null
    }

    private fun javaTimeValue(value: Any?): Any? = when (value) {
        is Timestamp -> value.toLocalDateTime()
        is Date -> value.toLocalDate()
        is Time -> value.toLocalTime()
        is Array<*> -> value.javaTimeArray()
        else -> value
    }

    private fun Array<*>.javaTimeArray(): Any {
        // Vertx resolves an array encoder by the array's own class, not by its elements, so the result has to
        // carry the java.time component type and not merely hold remapped elements. The component type is
        // matched exactly, so an Object[] or a java.util.Date[] holding java.sql values falls back to the
        // elements rather than reaching vertx unmapped.
        val component = componentTarget() ?: elementTarget() ?: return this
        val remapped = java.lang.reflect.Array.newInstance(component, size)
        forEachIndexed { index, element -> java.lang.reflect.Array.set(remapped, index, javaTimeValue(element)) }
        return remapped
    }

    private fun Array<*>.componentTarget(): Class<*>? = when (this::class.java.componentType) {
        Timestamp::class.java -> LocalDateTime::class.java
        Date::class.java -> LocalDate::class.java
        Time::class.java -> LocalTime::class.java
        else -> null
    }

    private fun Array<*>.elementTarget(): Class<*>? = when (firstOrNull { it != null }) {
        is Timestamp -> LocalDateTime::class.java
        is Date -> LocalDate::class.java
        is Time -> LocalTime::class.java
        else -> null
    }

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
