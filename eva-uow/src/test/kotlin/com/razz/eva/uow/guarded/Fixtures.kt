package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.TestPrincipal

internal object Fixtures {

    fun department(name: String = "Engineering"): OrphanedDepartment =
        OrphanedDepartment(randomDepartmentId(), name, 3, BUBALEH, persistentState(V1, null))

    fun employee(id: EmployeeId = EmployeeId(), department: DepartmentId = randomDepartmentId()): Employee =
        Employee(id, Name("Ada", "Lovelace"), department, "ada@test.com", BUBALEH, persistentState(V1, null))

    /** A proof for [TestPrincipal] on [target] whose decider allows every reload but one in [refused]. */
    fun proof(target: Employee, refused: Set<Department<*>> = setOf()): TestProof<Employee> =
        TestProof(TestPrincipal, target) { reloaded ->
            if (refused.any { it.id() == reloaded.departmentId }) throw Refused("moved out of reach")
        }
}
