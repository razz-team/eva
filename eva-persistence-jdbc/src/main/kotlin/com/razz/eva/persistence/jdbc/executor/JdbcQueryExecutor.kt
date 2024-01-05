package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import kotlinx.serialization.json.Json.Default.configuration
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.postgresql.util.PSQLException

class JdbcQueryExecutor(
    private val transactionManager: JdbcTransactionManager,
) : QueryExecutor {

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R> {
        return transactionManager.withConnection { connection ->
            jooqQuery.apply { configuration()?.set(connection) }.fetch()
        }
    }

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT> {
        jooqQuery.setReturning()
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            jooqQuery.apply { configuration()?.set(connection) }.execute()
            jooqQuery.returnedRecords.map { it.into(table) }
        }
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int {
        return transactionManager.inTransaction(REQUIRE_EXISTING) { connection ->
            jooqQuery.apply { configuration()?.set(connection) }.execute()
        }
    }

    override fun getConstraintName(e: DataAccessException): String? {
        return e.getCause(PSQLException::class.java)?.serverErrorMessage?.constraint
    }
}
