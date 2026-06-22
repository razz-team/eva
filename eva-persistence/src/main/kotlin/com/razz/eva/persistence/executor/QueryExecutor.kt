package com.razz.eva.persistence.executor

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.PersistenceException
import org.jooq.DMLQuery
import org.jooq.DSLContext
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

    suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT>

    suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int

    fun extractConstraintName(ex: Exception): String?

    fun extractUniqueConstraintName(ex: Exception, table: Table<*>): String?

    fun extractModelException(ex: Exception, table: Table<*>, modelId: ModelId<*>): PersistenceException?
}
