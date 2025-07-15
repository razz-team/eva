package com.razz.eva.persistence.executor

import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.QueryExecuted
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.StoreExecuted
import org.jooq.DMLQuery
import org.jooq.DSLContext
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

    override suspend fun <RIN : Record, ROUT : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<RIN>,
        table: Table<ROUT>,
    ): List<ROUT> {
        executions += StoreExecuted(dslContext, jooqQuery, table)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .fetch(jooqQuery.getSQL(INLINED))
            .into(table)
    }

    override suspend fun <R : Record> executeQuery(
        dslContext: DSLContext,
        jooqQuery: DMLQuery<R>,
    ): Int {
        executions += QueryExecuted(dslContext, jooqQuery)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .execute(jooqQuery.getSQL(INLINED))
    }

    override suspend fun executeSetSession(
        dslContext: DSLContext,
        sessionParamName: String,
        sessionParamValue: String,
    ) {
        return
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

        data class QueryExecuted(
            val dslContext: DSLContext,
            val jooqQuery: DMLQuery<out Record>,
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

    override fun getConstraintName(ex: DataAccessException): String? = null
}
