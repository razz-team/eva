package com.razz.eva.test.repository

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.repository.TestEvaRepositoryHelper
import io.kotest.matchers.shouldBe

// The spec bases supply the witness: these tests call witnessed fixtures with no mutating { }.
class RepositoryShouldSpecWitnessSpec : RepositoryShouldSpec(TestEvaRepositoryHelper, {

    should("call a witnessed factory and mutator in a test") {
        val employee = newEmployee(Name("Ann", "Lee"), randomDepartmentId(), "ann@test.com", BUBALEH)
        val department = newDepartment("Engineering", EmployeeId(), 1, BUBALEH)
        employee.changeDepartment(department).departmentId shouldBe department.id()
    }
})

class RepositorySpecWitnessSpec : RepositorySpec(TestEvaRepositoryHelper, {

    Given("An employee and a department") {
        val department = newDepartment("Engineering", EmployeeId(), 1, BUBALEH)

        Then("A test calls a witnessed factory and mutator") {
            val employee = newEmployee(Name("Ann", "Lee"), randomDepartmentId(), "ann@test.com", BUBALEH)
            employee.changeDepartment(department).departmentId shouldBe department.id()
        }
    }
})
