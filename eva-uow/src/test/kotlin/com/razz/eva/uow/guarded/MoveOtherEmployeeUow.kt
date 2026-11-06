package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork

/** A plain composable child that writes an employee: a guarded model, which a guarded parent refuses. */
internal class MoveOtherEmployeeUow(
    executionContext: ExecutionContext,
    private val employee: Employee,
    private val department: Department<*>,
) : UnitOfWork<TestPrincipal, ChildParams, Employee>(executionContext) {

    override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes {
        update(employee.changeDepartment(department))
    }
}
