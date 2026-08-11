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
     * Executes [jooqQuery] with a `RETURNING` clause, replacing any `RETURNING` clause
     * already set on the query.
     *
     * With [returning] omitted or null, the clause expands to all columns of the query's own table
     * and the returned [ROUT] records are fully populated.
     * With explicit [returning] fields, the clause contains exactly those fields and only the ones
     * belonging to [table] are populated on the returned records.
     *
     * Explicit [returning] must not be empty; use [executeQuery] for DML without `RETURNING`.
     */
    suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
        returning: Collection<Field<*>>? = null,
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
