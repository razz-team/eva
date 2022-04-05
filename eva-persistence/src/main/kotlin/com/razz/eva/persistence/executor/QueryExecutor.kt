package com.razz.eva.persistence.executor

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.exception.DataAccessException

interface QueryExecutor {

    suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R>

    suspend fun <R : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R>

    fun getExceptionMessage(e: DataAccessException): String?
}
