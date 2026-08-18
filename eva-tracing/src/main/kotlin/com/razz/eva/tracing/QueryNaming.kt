package com.razz.eva.tracing

import org.jooq.Delete
import org.jooq.Insert
import org.jooq.Merge
import org.jooq.Query
import org.jooq.Select
import org.jooq.Table
import org.jooq.Update
import org.jooq.impl.QOM

/**
 * Names for a database span, taken from the jOOQ query object.
 *
 * The semantic conventions ask for `{db.operation.name} {target}`. A constant name puts every database call
 * in one span metric bucket, and the table set bounds the cardinality of this one.
 */
internal object QueryNaming {

    fun operationName(jooqQuery: Query?): String = when (jooqQuery) {
        is Select<*> -> "SELECT"
        is Insert<*> -> "INSERT"
        is Update<*> -> "UPDATE"
        is Delete<*> -> "DELETE"
        is Merge<*> -> "MERGE"
        else -> "QUERY"
    }

    /**
     * The table a statement targets. Matches the DSL forms as well as the query objects: `deleteFrom(t)` is
     * a [QOM.Delete] and not a `DeleteQuery`, so matching only the latter would name nothing at all.
     *
     * A merge upsert returns null. `org.jooq.impl.MergeUpsert` implements [Merge] and not [QOM.Merge].
     *
     * A select over a join reports the leftmost table of the from clause.
     */
    fun queryTarget(jooqQuery: Query?): String? = when (jooqQuery) {
        is QOM.Insert<*> -> jooqQuery.`$into`()?.name
        is QOM.Update<*> -> jooqQuery.`$table`()?.name
        is QOM.Delete<*> -> jooqQuery.`$from`()?.name
        is QOM.Merge<*> -> jooqQuery.`$into`()?.name
        is Select<*> -> primaryTable(jooqQuery.`$from`().firstOrNull())
        else -> null
    }

    // jOOQ models a join as a table of its own, and that table is named `join`, so a select over one reported
    // that word as the collection. A repository selects from its own table and joins the rest, which makes the
    // leftmost table the one the statement is about.
    private tailrec fun primaryTable(table: Table<*>?): String? = when (table) {
        null -> null
        is QOM.JoinTable<*, *> -> primaryTable(table.`$table1`())
        else -> table.name
    }

    fun spanName(jooqQuery: Query?): String {
        val operation = operationName(jooqQuery)
        val target = queryTarget(jooqQuery)
        return if (target == null) operation else "$operation $target"
    }
}
