package com.razz.eva.repository

import com.razz.eva.persistence.executor.QueryExecutor
import org.jooq.Table
import org.jooq.exception.DataAccessException

object PgHelpers {
    const val PG_UNIQUE_VIOLATION = "23505"

    fun extractUniqueConstraintName(queryExecutor: QueryExecutor, table: Table<*>, ex: DataAccessException): String? {
        val constraintName = queryExecutor.getConstraintName(ex)
        return if (table.comment == "PARTITIONED") {
            constraintName
        } else {
            table.keys.firstOrNull { it.name == constraintName }?.name
                ?: table.indexes.firstOrNull { it.unique && it.name == constraintName }?.name
        }
    }

    fun extractConstraintName(queryExecutor: QueryExecutor, ex: DataAccessException): String? {
        return queryExecutor.getConstraintName(ex)
    }
}
