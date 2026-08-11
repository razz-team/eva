package com.razz.eva.persistence.executor

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.PersistenceException
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table

interface QueryExecutor {

    suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R>

    /**
     * Executes [jooqQuery] with a `RETURNING` clause of all [table] columns,
     * replacing any `RETURNING` clause already set on the query.
     * Returns fully populated [ROUT] records.
     *
     * Use the overload with explicit [Field]s to control which columns come back,
     * or [executeQuery] to skip `RETURNING` and get an affected row count.
     */
    suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT>

    /**
     * Executes [jooqQuery] with a `RETURNING` clause of exactly [returning] fields,
     * replacing any `RETURNING` clause already set on the query.
     * Returns [ROUT] records where only the [returning] fields belonging to [table] are populated.
     *
     * [returning] must not be empty; use [executeQuery] for DML without `RETURNING`.
     */
    suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
        returning: Collection<Field<*>>,
    ): List<ROUT>

    suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int

    fun extractConstraintName(ex: Exception): Constraint?

    fun extractUniqueConstraintName(ex: Exception, table: Table<*>): Constraint?

    fun extractModelException(ex: Exception, table: Table<*>, modelId: ModelId<*>): PersistenceException?

    fun extractConnectionException(ex: Exception): PersistenceException.ConnectionException?

    @JvmInline
    value class Constraint(val name: String?)
}
