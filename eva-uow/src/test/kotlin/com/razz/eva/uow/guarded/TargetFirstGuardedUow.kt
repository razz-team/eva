package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork

/**
 * Moves its employee to [department] first, then composes the child [child] builds over the moved employee: a
 * composition that writes its own model before the ones its children own.
 */
internal class TargetFirstGuardedUow<R : Any>(
    executionContext: ExecutionContext,
    private val department: Department<*>,
    private val child: (ExecutionContext, Employee) -> UnitOfWork<TestPrincipal, ChildParams, R>,
) : GuardedUnitOfWork<TestPrincipal, TestProof<*>, EmployeeParams, Employee>(
    executionContext,
    Configuration.default(),
    EmployeesGuarded,
    setOf(),
) {

    override suspend fun tryPerform(principal: TestPrincipal, params: EmployeeParams) = changes {
        val moved = update(params.employee.model()) { changeDepartment(department) }
        execute({ child(it, moved) }, principal) { ChildParams }
        moved
    }
}
