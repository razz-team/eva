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
 * Moves [Params.employee] to [department]. With [Params.extra] it moves one more employee the way [Params.how]
 * says: read through its proof, written undecided ([Params.undecided] in its place, unread), or read through its
 * proof and then [Params.decoy] written in its place. With [Params.beside] it updates an employee neither proof
 * decided; with [Params.renames] it renames a department without a proof, with [Params.adds] it adds a new one.
 * [Params.holds] holds the employee with `notChanged`, [Params.skips] reads it and writes nothing, [Params.stale]
 * writes or holds it at another version. With [answering] a failed write answers the employee the attempt decided.
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

    enum class How { PROVEN, UNDECIDED, DECOY }

    @Serializable
    data class Params(
        val employee: Proven<TestProof<Employee>, Employee>,
        val note: String,
        val extra: Proven<TestProof<Employee>, Employee>? = null,
        val how: How = How.PROVEN,
        @Transient val undecided: Employee? = null,
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
                How.UNDECIDED -> update(checkNotNull(params.undecided).changeDepartment(department))
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
