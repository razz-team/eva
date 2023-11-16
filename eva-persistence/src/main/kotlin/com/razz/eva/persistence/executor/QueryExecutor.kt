package com.razz.eva.persistence.executor

import org.jooq.DSLContext
import org.jooq.DeleteQuery
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.DataAccessException

interface QueryExecutor {

    suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R>

    suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT>

    suspend fun <R : Record> executeDelete(
        dslContext: DSLContext,
        jooqQuery: DeleteQuery<R>,
        table: Table<R>,
    ): Int

    fun getConstraintName(e: DataAccessException): String?
}
