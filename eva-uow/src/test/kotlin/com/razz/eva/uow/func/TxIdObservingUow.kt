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
        val firstReadTxId = currentTxId()
        val secondReadTxId = currentTxId()
        val department = newDepartment(
            name = params.departmentName,
            boss = EmployeeId(randomUUID()),
            headcount = 1,
            ration = SHAKSHOUKA,
        )
        add(department)
        ObservedTxIds(firstReadTxId, secondReadTxId, department.id())
    }

    private suspend fun currentTxId(): Long {
        val query = dslContext.select(DSL.field("txid_current()", SQLDataType.BIGINT))
        return queryExecutor.executeSelect(dslContext, query, DSL.table(query)).single().value1()
    }
}
