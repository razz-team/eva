package com.razz.eva.repository

import com.razz.eva.persistence.executor.QueryExecutor
import org.jooq.Table
import org.jooq.exception.DataAccessException

object PgHelpers {
    const val PG_UNIQUE_VIOLATION = "23505"

    fun extractUniqueConstraintName(queryExecutor: QueryExecutor, table: Table<*>, e: DataAccessException): String? {
        val message = queryExecutor.getExceptionMessage(e)
        return message?.findAnyOf(table.indexes.filter { it.unique }.map { it.name }.toSet())?.second
    }

    fun extractConstraintName(queryExecutor: QueryExecutor, table: Table<*>, e: DataAccessException): String? {
        val message = queryExecutor.getExceptionMessage(e)
        return message?.findAnyOf(table.checks.map { it.name }.toSet())?.second
    }
}
