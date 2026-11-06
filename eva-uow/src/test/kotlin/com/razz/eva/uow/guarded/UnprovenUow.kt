package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal

/** A guarded unit of work with no proven field, declaring departments: [body] is its whole block. */
internal class UnprovenUow(
    executionContext: ExecutionContext,
    private val body: suspend GuardedChangesDsl<TestProof<*>>.() -> Any,
) : GuardedUnitOfWork<TestPrincipal, TestProof<*>, ChildParams, Any>(
    executionContext,
    Configuration.default(),
    EmployeesGuarded,
    setOf(Department::class),
) {

    override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes { body() }
}
