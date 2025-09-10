package com.razz.eva.uow

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.uow.CreateSoloDepartmentUow.Params
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.UUID.randomUUID

class CreateSoloDepartmentUow(
    executionContext: ExecutionContext,
    private val employeeRepo: EmployeeRepository,
    private val departmentRepo: DepartmentRepository,
) : UnitOfWork<TestPrincipal, Params, OwnedDepartment>(executionContext) {

    @Serializable
    data class Params(
        val bossName: Name,
        val bossEmail: String,
        val departmentName: String,
        val ration: Ration,
        override val idempotencyKey: IdempotencyKey
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(
        principal: TestPrincipal,
        params: Params
    ): Changes<OwnedDepartment> = changes {
        val existingDep: Department<*>? = departmentRepo.findByName(params.departmentName)
        val existingEmp: Employee? = employeeRepo.findByName(params.bossName)
        when (existingDep to existingEmp) {
            (null to null) -> createDep(params)
            else -> throw IllegalStateException("Department already exists")
        }
    }

    private fun ChangesDsl.createDep(params: Params): OwnedDepartment {
        val depId = DepartmentId(randomUUID())
        val empId = EmployeeId(randomUUID())
        val boss = Employee(
            empId, params.bossName, depId, params.bossEmail, params.ration,
            newState(
                EmployeeCreated(
                    empId, params.bossName, depId, params.bossEmail, params.ration
                )
            )
        )
        val dep = add(
            OwnedDepartment(
                depId, params.departmentName, boss.id(), 1, params.ration,
                newState(
                    OwnedDepartmentCreated(
                        depId, params.departmentName, boss.id(), 1, params.ration
                    )
                )
            )
        )
        add(boss)
        return dep
    }
}
