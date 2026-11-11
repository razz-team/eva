package com.razz.eva.test.saga

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.TestPrincipal
import com.razz.eva.saga.Saga
import io.kotest.matchers.shouldBe

// Only its type arguments are used: the tests below never run it.
abstract class ProbeSaga : Saga<TestPrincipal, Unit, ProbeSaga.Step, ProbeSaga.Done, ProbeSaga>() {
    interface Step : Saga.Intermediary<ProbeSaga>
    interface Done : Saga.Terminal<ProbeSaga>
}

// The spec bases supply the witness: these tests call witnessed fixtures with no mutating { }.
class SagaShouldSpecWitnessSpec : SagaShouldSpec<TestPrincipal, Unit, ProbeSaga.Step, ProbeSaga.Done, ProbeSaga>({

    should("call a witnessed factory and mutator in a test") {
        val employee = newEmployee(Name("Ann", "Lee"), randomDepartmentId(), "ann@test.com", BUBALEH)
        val department = newDepartment("Engineering", EmployeeId(), 1, BUBALEH)
        employee.changeDepartment(department).departmentId shouldBe department.id()
    }
})

class SagaBehaviorSpecWitnessSpec : SagaBehaviorSpec<TestPrincipal, Unit, ProbeSaga.Step, ProbeSaga.Done, ProbeSaga>({

    Given("An employee and a department") {
        val department = newDepartment("Engineering", EmployeeId(), 1, BUBALEH)

        Then("A test calls a witnessed factory and mutator") {
            val employee = newEmployee(Name("Ann", "Lee"), randomDepartmentId(), "ann@test.com", BUBALEH)
            employee.changeDepartment(department).departmentId shouldBe department.id()
        }
    }
})
