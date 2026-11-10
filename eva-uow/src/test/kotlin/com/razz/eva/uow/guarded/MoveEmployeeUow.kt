package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Model
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

/**
 * Moves [Params.employee] to [department]. With [Params.extra] it moves one more employee the way [Params.how] says:
 * read with model(); [Params.unread] updated in its place without a read; or read, and then [Params.decoy] updated in
 * its place. [Params.beside] updates an employee no Proven field holds, [Params.renames] renames a department
 * without a proof, [Params.adds] adds a new department. [Params.holds] calls `notChanged` on the employee,
 * [Params.skips] reads it and registers nothing, [Params.stale] registers it at another version. With [answering]
 * onFailure returns the employee the failed attempt read.
 */
internal class MoveEmployeeUow(
    executionContext: ExecutionContext,
    unguarded: Set<KClass<out Model<*, *>>>,
    private val department: Department<*>,
    configuration: Configuration = Configuration.default(),
    private val answering: Boolean = false,
) : GuardedUnitOfWork<TestPrincipal, TestProof<*>, MoveEmployeeUow.Params, Employee>(
    executionContext,
    configuration,
    EmployeesGuarded,
    unguarded,
) {

    enum class How { PROVEN, UNREAD, DECOY }

    @Serializable
    data class Params(
        val employee: Proven<TestProof<Employee>, Employee>,
        val note: String,
        val extra: Proven<TestProof<Employee>, Employee>? = null,
        val how: How = How.PROVEN,
        @Transient val unread: Employee? = null,
        @Transient val decoy: Employee? = null,
        @Transient val adds: Department<*>? = null,
        @Transient val beside: Employee? = null,
        @Transient val renames: Department<*>? = null,
        @Transient val holds: Boolean = false,
        @Transient val skips: Boolean = false,
        @Transient val stale: Employee? = null,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        params.extra?.let { extra ->
            when (params.how) {
                How.PROVEN -> update(extra.model().changeDepartment(department))
                How.UNREAD -> update(checkNotNull(params.unread).changeDepartment(department))
                How.DECOY -> {
                    extra.model()
                    update(checkNotNull(params.decoy).changeDepartment(department))
                }
            }
        }
        params.adds?.let { add(it) }
        params.beside?.let { update(it.changeDepartment(department)) }
        params.renames?.let { update(it.rename("Renamed")) }
        val employee = params.employee.model()
        when {
            params.holds -> notChanged(params.stale ?: employee)
            params.skips -> employee
            else -> update((params.stale ?: employee).changeDepartment(department))
        }
    }

    override suspend fun onFailure(attempt: FailedAttempt, params: Params, ex: PersistenceException): Employee =
        if (answering) attempt[params.employee] else throw ex
}
