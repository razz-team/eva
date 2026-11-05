package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/**
 * Moves its target to [department] first, then composes the child [child] builds over the moved target: a
 * composition that writes its own model before the ones its children own.
 */
internal class TargetFirstGuardedUow<R : Any>(
    executionContext: ExecutionContext,
    private val department: Department<*>,
    private val child: (ExecutionContext, Employee) -> UnitOfWork<TestPrincipal, ChildParams, R>,
) : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, TargetFirstGuardedUow.Params, Employee, Employee>(
    executionContext,
    Configuration.default(),
    EmployeesGuarded,
    setOf(),
) {

    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun perform(principal: TestPrincipal, params: Params) = changes {
        val moved = update(target.changeDepartment(department))
        execute({ child(it, moved) }, principal) { ChildParams }
        moved
    }
}
