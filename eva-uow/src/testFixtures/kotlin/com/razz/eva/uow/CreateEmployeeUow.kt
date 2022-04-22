package com.razz.eva.uow

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.uow.params.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.UUID.randomUUID

class CreateEmployeeUow(
    clock: Clock,
    private val departmentRepo: DepartmentRepository,
) : UnitOfWork<TestPrincipal, CreateEmployeeUow.Params, Employee>(clock) {

    @Serializable
    data class Params(
        val departmentId: DepartmentId,
        val name: Name,
        val email: String,
        val ration: Ration,
        override val idempotencyKey: IdempotencyKey
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        val empId = EmployeeId(randomUUID())
        val emp = Employee(
            id = empId,
            name = params.name,
            departmentId = params.departmentId,
            email = params.email,
            ration = params.ration,
            entityState = newState(
                EmployeeCreated(
                    empId, params.name, params.departmentId, params.email, params.ration
                )
            )
        )
        when (val dep = departmentRepo.find(params.departmentId)) {
            null -> throw IllegalStateException("no department")
            else -> update(dep.addEmployee(emp))
        }
        add(emp)
        emp
    }
}
