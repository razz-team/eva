package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/**
 * Composes [child] before it moves its target to [department]. With [swallowing] it catches the child's refusal
 * and goes on, so only its own write may land.
 */
internal class ComposingGuardedUow<R : Any>(
    executionContext: ExecutionContext,
    private val department: Department<*>,
    private val child: (ExecutionContext) -> UnitOfWork<TestPrincipal, ChildParams, R>,
    private val swallowing: Boolean,
) : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, ComposingGuardedUow.Params, Employee, Employee>(
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
        try {
            execute(child, principal) { ChildParams }
        } catch (ex: IllegalStateException) {
            if (!swallowing) throw ex
        }
        update(target.changeDepartment(department))
    }
}
