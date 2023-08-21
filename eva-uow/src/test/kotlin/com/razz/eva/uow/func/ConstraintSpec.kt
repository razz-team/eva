package com.razz.eva.uow.func

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.uow.test.HireEmployeesUow
import com.razz.eva.uow.test.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.UUID.randomUUID

class ConstraintSpec : PersistenceBaseSpec({

    Given("Models exist") {
        val department = module.writableRepository.add(
            newDepartment(
                name = "backend",
                boss = EmployeeId(randomUUID()),
                headcount = 1,
                ration = SHAKSHOUKA
            )
        )
        val employee = module.writableRepository.add(
            newEmployee(
                name = Name("K", "🍄"),
                departmentId = department.id(),
                email = "K.🍄@backend.razz.team",
                ration = SHAKSHOUKA
            )
        )

        When("Principal tries to perform uow and break unique constraints") {
            val attempt = suspend {
                module.uowx.execute(HireEmployeesUow::class, TestPrincipal) {
                    HireEmployeesUow.Params(
                        department.id(),
                        listOf(Name("K", "🍄"), Name("I", "🍋"), Name("A", "Kaplin"))
                    )
                }
            }

            Then("Uow fails due to DB constraints") {
                val ex = shouldThrow<UniqueModelRecordViolationException> { attempt() }
                ex.constraintName shouldBe "employees_name_idx"
            }

            And("Uow changes are not applied") {
                module.employeeRepo.findByName(Name("K", "🍄"))?.id() shouldBe employee.id()
                module.employeeRepo.findByName(Name("I", "🍋")) shouldBe null
                module.employeeRepo.findByName(Name("A", "Kaplin")) shouldBe null
            }
        }

        When("Principal tries to perform uow and break data constraints") {
            val attempt = suspend {
                module.uowx.execute(HireEmployeesUow::class, TestPrincipal) {
                    HireEmployeesUow.Params(
                        department.id(),
                        listOf(Name("Nursultan Äbishuly Nazarbayev", "01"))
                    )
                }
            }

            Then("Uow fails due to too long value") {
                val ex = shouldThrow<ModelRecordConstraintViolationException> { attempt() }
                ex.constraintName shouldBe "employees_first_name_len_check"
            }

            And("Uow changes are not applied") {
                module.employeeRepo.findByName(Name("Nursultan Äbishuly Nazarbayev", "01")) shouldBe null
            }
        }
    }
})
