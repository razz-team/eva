package com.razz.eva.uow

import com.razz.eva.domain.Department
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Tag
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.uow.composable.UnitOfWork as ComposableUnitOfWork
import kotlinx.serialization.Serializable

class InternalMobilityUow(
    executionContext: ExecutionContext,
    private val employeeRepo: EmployeeRepository,
    private val departmentRepo: DepartmentRepository,
) : ComposableUnitOfWork<TestPrincipal, InternalMobilityUow.Params, Unit>(executionContext) {

    @Serializable
    data class Params(
        val employees: List<EmployeeId>,
        val departmentId: DepartmentId,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params): Changes<Unit> = changes {
        var newDep = checkNotNull(departmentRepo.find(params.departmentId))
        val oldDeps = mutableMapOf<DepartmentId, Department<*>>()
        for (empId in params.employees) {
            val existingEmp = checkNotNull(employeeRepo.find(empId))
            check(newDep.ration == existingEmp.ration) { "Ration doesn't match" }
            val oldDep = when (val dep = oldDeps[existingEmp.departmentId]) {
                null -> checkNotNull(departmentRepo.find(existingEmp.departmentId))
                else -> dep
            }
            update(existingEmp.changeDepartment(newDep))
            oldDeps[existingEmp.departmentId] = oldDep.removeEmployee(existingEmp)
            newDep = newDep.addEmployee(existingEmp)
            add(
                Tag.tag(
                    newDep.id().id,
                    "transfer-${empId.id}",
                    "from-${oldDep.id().id}",
                ),
            )
        }
        update(newDep)
        for (oldDep in oldDeps.values) {
            update(oldDep)
            delete(
                Tag.tag(
                    oldDep.id().id,
                    "full-staff",
                    "true",
                ),
            )
        }
    }
}
