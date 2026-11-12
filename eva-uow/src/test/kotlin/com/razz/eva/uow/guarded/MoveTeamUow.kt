package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal

/** Moves every employee of the team to [department], each under its own proof, but the one [skipping] names. */
internal class MoveTeamUow(
    executionContext: ExecutionContext,
    private val department: Department<*>,
    private val skipping: EmployeeId?,
) : GuardedUnitOfWork<TestPrincipal, TestProof<*>, TeamParams, List<Employee>>(
    executionContext,
    Configuration.default(),
    EmployeesGuarded,
    setOf(),
) {

    override suspend fun tryPerform(principal: TestPrincipal, params: TeamParams) = changes {
        params.team.map { it.model() }.filter { it.id() != skipping }
            .map { update(it) { changeDepartment(department) } }
    }
}
