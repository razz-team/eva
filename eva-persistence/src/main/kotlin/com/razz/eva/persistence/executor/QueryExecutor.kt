package com.razz.eva.persistence.executor

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.PersistenceException
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Delete
import org.jooq.DeleteQuery
import org.jooq.Field
import org.jooq.Insert
import org.jooq.Merge
import org.jooq.Query
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.TableField
import org.jooq.Update
import org.jooq.impl.QOM

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
     * With explicit [returning] fields, the clause contains exactly those fields. Returned values
     * map onto the [table] record by field name: a returned field whose name matches a [table]
     * column populates that column whatever its origin, and returned names without a match are
     * dropped. Columns outside [returning] stay null, so such partial records are not suitable
     * for model hydration.
     *
     * Explicit [returning] must not be empty; use [executeQuery] for DML without `RETURNING`.
     * Explicit [returning] columns are requalified against the query's target table, so for a query
     * built on an aliased table the `RETURNING` clause renders alias-qualified columns.
     * Non-column expressions render as passed.
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

    companion object {

        /**
         * The `db.operation.name` for a query, taken from its jOOQ type. It does not parse rendered
         * SQL. Falls back to the dialect neutral verb when the query is none of the concrete DML types.
         */
        fun operationName(jooqQuery: Query): String = when (jooqQuery) {
            is Select<*> -> "SELECT"
            is Insert<*> -> "INSERT"
            is Update<*> -> "UPDATE"
            is Delete<*> -> "DELETE"
            is Merge<*> -> "MERGE"
            else -> "QUERY"
        }

        /**
         * The table a DML statement targets, for `db.collection.name` and the span name, when the caller
         * did not hand one over. Matches the DSL forms as well as the query objects: `deleteFrom(t)` is a
         * [QOM.Delete] and not a [DeleteQuery], so matching only the latter would name every such span
         * after nothing at all.
         */
        fun queryTarget(jooqQuery: Query): String? = when (jooqQuery) {
            is QOM.Insert<*> -> jooqQuery.`$into`()?.name
            is QOM.Update<*> -> jooqQuery.`$table`()?.name
            is QOM.Delete<*> -> jooqQuery.`$from`()?.name
            is QOM.Merge<*> -> jooqQuery.`$into`()?.name
            else -> null
        }

        /**
         * Requalifies [returning] columns against the target table of [jooqQuery]: a [TableField]
         * whose name the target table knows resolves to the target's own field, so fields of an
         * aliased table render with the alias. Non-column expressions and unknown fields are kept
         * as passed.
         */
        fun matchReturning(jooqQuery: StoreQuery<*>, returning: Collection<Field<*>>): List<Field<*>> {
            require(returning.isNotEmpty()) { "Returning fields must not be empty, use executeQuery for a row count" }
            val target = when (jooqQuery) {
                is QOM.Insert<*> -> jooqQuery.`$into`()
                is QOM.Update<*> -> jooqQuery.`$table`()
                else -> null
            }
            return if (target == null) {
                returning.toList()
            } else {
                returning.map { field ->
                    if (field is TableField<*, *>) target.field(field) ?: field else field
                }
            }
        }
    }
}
