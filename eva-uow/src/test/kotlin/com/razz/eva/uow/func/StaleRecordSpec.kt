package com.razz.eva.uow.func

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.uow.HireEmployeesUow
import com.razz.eva.uow.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import java.util.UUID.randomUUID

class StaleRecordSpec : PersistenceBaseSpec({

    Given("Models exist") {
        val department = module.writableRepository.add(
            newDepartment(
                name = "stale_dep",
                boss = EmployeeId(randomUUID()),
                headcount = 1,
                ration = SHAKSHOUKA
            )
        )

        var updatedOutOfUow = false
        And("Model will be updated before uow transaction is completed") {
            coEvery {
                module.departmentPreUpdate.invoke(match { it.id() == department.id() })
            } coAnswers {
                val newEmployee = newEmployee(
                    name = Name("Fake", "Dmitri"),
                    departmentId = department.id(),
                    email = "fake@razz.com",
                    ration = SHAKSHOUKA
                )
                if (!updatedOutOfUow) {
                    updatedOutOfUow = true
                    module.writableRepository.update(
                        checkNotNull(module.departmentRepo.find(department.id()))
                            .addEmployee(newEmployee)
                    )
                }
            }

            When("Principal tries to perform uow") {
                val attempt = suspend {
                    module.uowx.execute(HireEmployeesUow::class, TestPrincipal) {
                        HireEmployeesUow.Params(
                            department.id(),
                            listOf(Name("Nik", "Dennis"))
                        )
                    }
                }

                Then("Uow fails and changes are not applied") {
                    shouldThrow<StaleRecordException> { attempt() }
                    module.departmentRepo.find(department.id())?.headcount shouldBe 2
                }
            }
        }
    }
})
