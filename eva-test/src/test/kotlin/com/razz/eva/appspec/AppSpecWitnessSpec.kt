package com.razz.eva.appspec

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import io.kotest.matchers.shouldBe

class AppSpecWitnessSpec : AppSpec({

    should("call a witnessed factory and mutator in a test") {
        val employee = newEmployee(Name("Ann", "Lee"), randomDepartmentId(), "ann@test.com", BUBALEH)
        val department = newDepartment("Engineering", EmployeeId(), 1, BUBALEH)
        appName shouldBe "probe-app"
        employee.changeDepartment(department).departmentId shouldBe department.id()
    }
})

class ResourceSpecWitnessSpec : ResourceSpec({

    should("call a witnessed factory and mutator in a test of a derived base") {
        val employee = newEmployee(Name("Ann", "Lee"), randomDepartmentId(), "ann@test.com", BUBALEH)
        val department = newDepartment("Engineering", EmployeeId(), 1, BUBALEH)
        route shouldBe "/employees"
        appName shouldBe "probe-app"
        employee.changeDepartment(department).departmentId shouldBe department.id()
    }
})
