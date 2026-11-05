package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Model
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

/**
 * Moves its target to [department]. With [extra] it moves one more employee under that proof, the way [how] says:
 * read through the proof, written undecided, or read through the proof and then [decoy] written in its place. With
 * [beside] it updates an employee that is neither; with [renames] it renames a department without a proof, with
 * [adds] it adds a new one. [readOnly] only reads the target.
 */
internal class MoveEmployeeUow(
    executionContext: ExecutionContext,
    unguarded: Set<KClass<out Model<*, *>>>,
    private val department: Department<*>,
    private val configuration: Configuration = Configuration.default(),
) : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, MoveEmployeeUow.Params, Employee, Employee>(
    executionContext,
    configuration,
    EmployeesGuarded,
    unguarded,
) {

    enum class How { PROVEN, UNDECIDED, DECOY }

    @Serializable
    data class Params(
        val note: String,
        val extra: Proven<TestProof<Employee>, Employee>? = null,
        val how: How = How.PROVEN,
        @Transient val decoy: Employee? = null,
        @Transient val adds: Department<*>? = null,
        @Transient val beside: Employee? = null,
        @Transient val renames: Department<*>? = null,
        @Transient val readOnly: Boolean = false,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun perform(principal: TestPrincipal, params: Params) = changes {
        params.extra?.let { extra ->
            when (params.how) {
                How.PROVEN -> update(extra.model().changeDepartment(department))
                How.UNDECIDED -> update(extra.proof.target.changeDepartment(department))
                How.DECOY -> {
                    extra.model()
                    update(checkNotNull(params.decoy).changeDepartment(department))
                }
            }
        }
        params.adds?.let { add(it) }
        params.beside?.let { update(it.changeDepartment(department)) }
        params.renames?.let { update(it.rename("Renamed")) }
        if (params.readOnly) notChanged(target) else update(target.changeDepartment(department))
    }

    /** A `changes { }` outside `tryPerform`, which the kernel refuses. */
    suspend fun stray(): GuardedChanges<Employee> = changes { update(target) }
}
