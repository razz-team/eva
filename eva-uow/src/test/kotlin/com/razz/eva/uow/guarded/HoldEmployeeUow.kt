package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork

/** A plain composable child that calls notChanged on an employee, a guarded model, with no proof. */
internal class HoldEmployeeUow(
    executionContext: ExecutionContext,
    private val employee: Employee,
) : UnitOfWork<TestPrincipal, ChildParams, Employee>(executionContext) {

    override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes {
        notChanged(employee)
    }
}
