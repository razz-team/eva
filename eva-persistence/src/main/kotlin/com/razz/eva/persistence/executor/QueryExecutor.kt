package com.razz.eva.persistence.executor

import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.DataAccessException

interface QueryExecutor {

    suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>
    ): List<R>

    suspend fun <R : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<R>,
        table: Table<R>
    ): List<R>

    fun getExceptionMessage(e: DataAccessException): String?
}
