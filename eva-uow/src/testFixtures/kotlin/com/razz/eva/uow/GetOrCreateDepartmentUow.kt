package com.razz.eva.uow

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.Ration
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.uow.GetOrCreateDepartmentUow.Params
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable
import java.util.UUID

class GetOrCreateDepartmentUow(
    executionContext: ExecutionContext,
    private val departmentRepo: DepartmentRepository
) : UnitOfWork<TestPrincipal, Params, OwnedDepartment>(executionContext) {

    @Serializable
    data class Params(
        val boss: EmployeeId,
        val departmentName: String,
        val ration: Ration
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params): Changes<OwnedDepartment> {
        val existingDep = departmentRepo.findByBoss(params.boss)
        return if (existingDep != null) {
            // Get path: the department already exists, so return it without recording a uow_event.
            abstain(existingDep)
        } else {
            changes {
                add(createDep(params.boss, params.departmentName, params.ration))
            }
        }
    }

    private fun createDep(boss: EmployeeId, name: String, ration: Ration): OwnedDepartment {
        val depId = DepartmentId(UUID.randomUUID())
        return OwnedDepartment(
            depId, name, boss, 1, ration, newState(OwnedDepartmentCreated(depId, name, boss, 1, ration))
        )
    }
}
