package com.razz.eva.persistence.executor

import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.StoreExecuted
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.Select
import org.jooq.StoreQuery
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
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R> {
        executions += SelectExecuted(dslContext, jooqQuery, fields, recordType)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .fetch(jooqQuery.getSQL(INLINED))
            .into(recordType)
    }

    override suspend fun <R : Record> executeStore(
        dslContext: DSLContext,
        jooqQuery: StoreQuery<R>,
        fields: List<Field<*>>,
        recordType: Class<out R>
    ): List<R> {
        executions += StoreExecuted(dslContext, jooqQuery, fields, recordType)
        return DSL.using(MockConnection(MockProvider(queries)), POSTGRES, dslContext.settings())
            .fetch(jooqQuery.getSQL(INLINED))
            .into(recordType)
    }

    sealed class ExecutionStep {

        data class StoreExecuted(
            val dslContext: DSLContext,
            val jooqQuery: StoreQuery<out Record>,
            val fields: List<Field<*>>,
            val recordType: Class<out Record>
        ) : ExecutionStep()

        data class SelectExecuted(
            val dslContext: DSLContext,
            val jooqQuery: Select<out Record>,
            val fields: List<Field<*>>,
            val recordType: Class<out Record>
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
