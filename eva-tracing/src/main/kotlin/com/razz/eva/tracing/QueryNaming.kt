package com.razz.eva.tracing

import org.jooq.Delete
import org.jooq.Insert
import org.jooq.Merge
import org.jooq.Query
import org.jooq.Select
import org.jooq.Update
import org.jooq.impl.QOM

/**
 * Names for a database span, taken from the jOOQ query object.
 *
 * The semantic conventions ask for `{db.operation.name} {target}`. A constant name puts every database call
 * in one span metric bucket, and the table set bounds the cardinality of this one.
 */
object QueryNaming {

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
     */
    fun queryTarget(jooqQuery: Query?): String? = when (jooqQuery) {
        is QOM.Insert<*> -> jooqQuery.`$into`()?.name
        is QOM.Update<*> -> jooqQuery.`$table`()?.name
        is QOM.Delete<*> -> jooqQuery.`$from`()?.name
        is QOM.Merge<*> -> jooqQuery.`$into`()?.name
        is Select<*> -> jooqQuery.`$from`().firstOrNull()?.let { (it as? org.jooq.Table<*>)?.name }
        else -> null
    }

    fun spanName(jooqQuery: Query?): String {
        val operation = operationName(jooqQuery)
        val target = queryTarget(jooqQuery)
        return if (target == null) operation else "$operation $target"
    }
}
