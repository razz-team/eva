package com.razz.eva.uow

import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.uow.HireEmployeesUow.Params
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import kotlinx.serialization.Serializable
import java.time.Duration
import java.util.UUID.randomUUID

class HireEmployeesUow(
    executionContext: ExecutionContext,
    private val departmentRepo: DepartmentRepository,
    private val employeeRepo: EmployeeRepository,
    retries: Int,
    private val forceAdd: Boolean,
) : UnitOfWork<TestPrincipal, Params, List<Employee>>(
    executionContext,
    Configuration(retry = StaleRecordFixedRetry(retries, Duration.ofMillis(100)))
) {

    @Serializable
    data class Params(
        val departmentId: DepartmentId,
        val names: List<Name>
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        var dep = checkNotNull(departmentRepo.find(params.departmentId))
        val emps = mutableListOf<Employee>()
        for (name in params.names) {
            val existing = employeeRepo.findByName(name)
            if (existing != null && !forceAdd) {
                emps += notChanged(existing)
            } else {
                val email = "${name.first}.${name.last}@${dep.name}.razz.team"
                val empId = EmployeeId(randomUUID())
                val emp = Employee(
                    id = empId,
                    name = name,
                    departmentId = params.departmentId,
                    email = email,
                    ration = dep.ration,
                    modelState = newState(
                        EmployeeCreated(
                            empId, name, params.departmentId, email, dep.ration
                        )
                    )
                )
                emps += add(emp)
                dep = dep.addEmployee(emp)
            }
        }
        update(dep)
        emps
    }
}
