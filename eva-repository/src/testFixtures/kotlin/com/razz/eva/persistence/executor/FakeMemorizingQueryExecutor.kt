package com.razz.eva.persistence.executor

import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.DeleteExecuted
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.StoreExecuted
import org.jooq.DSLContext
import org.jooq.DeleteQuery
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.TableRecord
import org.jooq.conf.ParamType.INLINED
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockExecuteContext
import org.jooq.tools.jdbc.MockResult
import java.util.*

class FakeMemorizingQueryExecutor(
    private val queries: Deque<List<TableRecord<*>>> = ArrayDeque(),
    private var executions: List<ExecutionStep> = listOf()
) : QueryExecutor {

    val executionHistory: List<ExecutionStep>
        get() = executions

    val lastExecution: ExecutionStep
        get() = executions.last()

    fun expectQueryFor(vararg records: TableRecord<*>) {
        queries.push(records.toList())
    }

    override suspend fun <R : Record> executeSelect(
        dslContext: DSLContext,
        jooqQuery: Select<R>,
        table: Table<R>,
    ): List<R> {
        executions += SelectExecuted(dslContext, jooqQuery, table)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .fetch(jooqQuery.getSQL(INLINED))
            .into(table)
    }

    override suspend fun <R : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<R>,
        table: Table<R>,
    ): List<R> {
        executions += StoreExecuted(dslContext, jooqQuery, table)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .fetch(jooqQuery.getSQL(INLINED))
            .into(table)
    }

    override suspend fun <R : Record> executeDelete(
        dslContext: DSLContext,
        jooqQuery: DeleteQuery<R>,
        table: Table<R>,
    ): Int {
        executions += DeleteExecuted(dslContext, jooqQuery)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .execute(jooqQuery.getSQL(INLINED))
    }

    sealed class ExecutionStep {

        data class StoreExecuted(
            val dslContext: DSLContext,
            val jooqQuery: StoreQuery<out Record>,
            val table: Table<out Record>,
        ) : ExecutionStep()

        data class SelectExecuted(
            val dslContext: DSLContext,
            val jooqQuery: Select<out Record>,
            val table: Table<out Record>,
        ) : ExecutionStep()

        data class DeleteExecuted(
            val dslContext: DSLContext,
            val jooqQuery: DeleteQuery<out Record>,
        ) : ExecutionStep()
    }

    class MockProvider(private val queries: Deque<List<TableRecord<*>>> = ArrayDeque()) : MockDataProvider {

        override fun execute(ctx: MockExecuteContext): Array<MockResult> {
            return when (val q = queries.pollFirst()) {
                null -> arrayOf()
                else -> {
                    val result = DSL.using(q.first().configuration()).newResult(*q.first().getTable().fields())
                    q.forEach { result.add(it) }
                    val mockResult = MockResult(q.size, result)
                    arrayOf(mockResult)
                }
            }
        }
    }

    override fun getExceptionMessage(e: DataAccessException): String? = null
}
