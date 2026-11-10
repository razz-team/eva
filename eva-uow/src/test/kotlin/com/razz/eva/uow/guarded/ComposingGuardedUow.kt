package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork

/**
 * Composes [child] before it moves its employee to [department]. With [swallowing] it catches the child's refusal
 * and goes on, so only its own changes are written.
 */
internal class ComposingGuardedUow<R : Any>(
    executionContext: ExecutionContext,
    private val department: Department<*>,
    private val child: (ExecutionContext) -> UnitOfWork<TestPrincipal, ChildParams, R>,
    private val swallowing: Boolean,
) : GuardedUnitOfWork<TestPrincipal, TestProof<*>, EmployeeParams, Employee>(
    executionContext,
    Configuration.default(),
    EmployeesGuarded,
    setOf(),
) {

    override suspend fun tryPerform(principal: TestPrincipal, params: EmployeeParams) = changes {
        try {
            execute(child, principal) { ChildParams }
        } catch (ex: IllegalStateException) {
            if (!swallowing) throw ex
        }
        update(params.employee.model().changeDepartment(department))
    }
}
