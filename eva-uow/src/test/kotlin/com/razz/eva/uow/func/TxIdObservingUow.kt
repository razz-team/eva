package com.razz.eva.uow.func

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWork
import com.razz.eva.uow.WriteTxScope
import com.razz.eva.uow.composable.UnitOfWork as ComposableUnitOfWork
import com.razz.eva.uow.func.TxIdObservingUow.Params
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.util.UUID.randomUUID

/**
 * Observes which transaction each of its perform reads runs in via `txid_current()` and adds one
 * department, whose row `xmin` is the flush transaction id. Lets a spec prove whether perform reads
 * and the flush share one transaction (FULL_UOW) or run in separate ones (FLUSH).
 */
class TxIdObservingUow(
    executionContext: ExecutionContext,
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    writeTxScope: WriteTxScope,
) : UnitOfWork<TestPrincipal, Params, TxIdObservingUow.ObservedTxIds>(
    executionContext,
    Configuration(writeTxScope = writeTxScope),
) {

    @Serializable
    data class Params(val departmentName: String) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    data class ObservedTxIds(
        val firstReadTxId: Long,
        val secondReadTxId: Long,
        val departmentId: DepartmentId,
    )

    override suspend fun tryPerform(principal: TestPrincipal, params: Params): Changes<ObservedTxIds> = changes {
        val firstReadTxId = currentTxId(queryExecutor, dslContext)
        val secondReadTxId = currentTxId(queryExecutor, dslContext)
        val department = newDepartment(
            name = params.departmentName,
            boss = EmployeeId(randomUUID()),
            headcount = 1,
            ration = SHAKSHOUKA,
        )
        add(department)
        ObservedTxIds(firstReadTxId, secondReadTxId, department.id())
    }
}

/**
 * Composable counterpart of [TxIdObservingUow]: the parent reads `txid_current()`, composes
 * [TxIdObservingChildUow] (whose only job is to read `txid_current()` too) and adds one department.
 * Lets a spec prove that a composed child's reads join the parent's FULL_UOW transaction.
 */
class ComposedTxIdObservingUow(
    executionContext: ExecutionContext,
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    writeTxScope: WriteTxScope,
) : ComposableUnitOfWork<TestPrincipal, ComposedTxIdObservingUow.Params, ComposedTxIdObservingUow.ObservedTxIds>(
    executionContext,
    Configuration(writeTxScope = writeTxScope),
) {

    @Serializable
    data class Params(val departmentName: String) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    data class ObservedTxIds(
        val parentReadTxId: Long,
        val childReadTxId: Long,
        val departmentId: DepartmentId,
    )

    override suspend fun tryPerform(principal: TestPrincipal, params: Params): Changes<ObservedTxIds> = changes {
        val parentReadTxId = currentTxId(queryExecutor, dslContext)
        val childReadTxId = execute(
            { ctx -> TxIdObservingChildUow(ctx, queryExecutor, dslContext) },
            principal,
        ) {
            TxIdObservingChildUow.Params(params.departmentName)
        }
        val department = newDepartment(
            name = params.departmentName,
            boss = EmployeeId(randomUUID()),
            headcount = 1,
            ration = SHAKSHOUKA,
        )
        add(department)
        ObservedTxIds(parentReadTxId, childReadTxId, department.id())
    }
}

class TxIdObservingChildUow(
    executionContext: ExecutionContext,
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
) : ComposableUnitOfWork<TestPrincipal, TxIdObservingChildUow.Params, Long>(executionContext) {

    @Serializable
    data class Params(val marker: String) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params): Changes<Long> =
        noChanges(currentTxId(queryExecutor, dslContext))
}

private suspend fun currentTxId(queryExecutor: QueryExecutor, dslContext: DSLContext): Long {
    val query = dslContext.select(DSL.field("txid_current()", SQLDataType.BIGINT))
    return queryExecutor.executeSelect(dslContext, query, DSL.table(query)).single().value1()
}
