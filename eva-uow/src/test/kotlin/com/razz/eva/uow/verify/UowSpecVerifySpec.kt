package com.razz.eva.uow.verify

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.test.domain.persistentStateV1
import com.razz.eva.test.uow.UowBehaviorSpec
import com.razz.eva.uow.ChangesAccumulator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.UUID.randomUUID

class UowSpecVerifySpec : UowBehaviorSpec({

    val name = Name("Ivan", "Ivanov")
    val oldDepId = randomDepartmentId()
    val newDepId = randomDepartmentId()
    val newDep = OwnedDepartment(
        newDepId, "new and cool", EmployeeId(randomUUID()), 1, BUBALEH,
        persistentStateV1(),
    )

    fun newEmployee(id: EmployeeId) = Employee(
        id = id,
        name = name,
        departmentId = oldDepId,
        email = "ivan.ivanov@lame.dep",
        ration = BUBALEH,
        modelState = newState(EmployeeCreated(id, name, oldDepId, "ivan.ivanov@lame.dep", BUBALEH)),
    )

    fun existingEmployee(id: EmployeeId) = Employee(
        id = id,
        name = name,
        departmentId = oldDepId,
        email = "ivan.ivanov@lame.dep",
        ration = BUBALEH,
        modelState = persistentStateV1(),
    )

    Given("Changes adding a model and returning the very same instance") {
        val employeeId = EmployeeId(randomUUID())
        val employee = newEmployee(employeeId)
        val changes = ChangesAccumulator().withAddedModel(employee).withResult(employee)

        When("addsAndReturns verifies with a block that counts its own invocations") {
            var invocations = 0

            changes verifyInOrder {
                addsAndReturns<Employee> { invocations++ }
                emits<EmployeeCreated> { }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("addsAndReturns verifies with a block that nests emits") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<Employee> {
                        departmentId shouldBe oldDepId
                        emits<EmployeeCreated> {
                            employeeId shouldBe employee.id()
                        }
                    }
                }
            }

            Then("Nested emits consumes the single event and the spec ends clean") {
                verifying()
            }
        }

        When("addsAndReturns verifies with a block that does not hold") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<Employee> { departmentId shouldBe newDepId }
                    emits<EmployeeCreated> { }
                }
            }

            Then("The block failure surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }

        When("addsAndReturns is given an id via the equality verifier") {
            var invocations = 0

            changes verifyInOrder {
                addsAndReturns<Employee>(employeeId) { invocations++ }
                emits<EmployeeCreated> { }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("addsAndReturns is given an id that does not match") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<Employee>(EmployeeId(randomUUID())) { }
                    emits<EmployeeCreated> { }
                }
            }

            Then("The id mismatch surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }
    }

    Given("Changes adding a model and returning an equal but different instance") {
        val employeeId = EmployeeId(randomUUID())
        val added = newEmployee(employeeId)
        val twin = newEmployee(employeeId)
        val changes = ChangesAccumulator().withAddedModel(added).withResult(twin)

        When("addsAndReturns verifies the added model") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<Employee> { }
                    emits<EmployeeCreated> { }
                }
            }

            Then("The result is reported as not being the added model") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "Result is not the added model"
                exception.message shouldContain "equal by value, but a different instance"
            }
        }
    }

    Given("Changes updating a model and returning the very same instance") {
        val employeeId = EmployeeId(randomUUID())
        val employee = existingEmployee(employeeId).changeDepartment(newDep)
        val changes = ChangesAccumulator().withUpdatedModel(employee).withResult(employee)

        When("updatesAndReturns verifies with a block that counts its own invocations") {
            var invocations = 0

            changes verifyInOrder {
                updatesAndReturns<Employee> { invocations++ }
                emits<DepartmentChanged> { }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("updatesAndReturns verifies with a block that nests emits") {
            val verifying = {
                changes verifyInOrder {
                    updatesAndReturns<Employee> {
                        departmentId shouldBe newDepId
                        emits<DepartmentChanged> {
                            newDepartmentId shouldBe newDepId
                        }
                    }
                }
            }

            Then("Nested emits consumes the single event and the spec ends clean") {
                verifying()
            }
        }

        When("updatesAndReturns is given an id via the equality verifier") {
            var invocations = 0

            changes verifyInOrder {
                updatesAndReturns<Employee>(employeeId) { invocations++ }
                emits<DepartmentChanged> { }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }
    }

    Given("Changes updating a model and returning an unrelated value") {
        val employeeId = EmployeeId(randomUUID())
        val employee = existingEmployee(employeeId).changeDepartment(newDep)
        val changes = ChangesAccumulator().withUpdatedModel(employee).withResult(employeeId)

        When("updatesAndReturns verifies the updated model") {
            val verifying = {
                changes verifyInOrder {
                    updatesAndReturns<Employee> { }
                    emits<DepartmentChanged> { }
                }
            }

            Then("The result is reported as not being the updated model") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "Result is not the updated model"
            }
        }
    }
})
