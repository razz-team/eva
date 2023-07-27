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
import io.kotest.core.spec.IsolationMode.InstancePerLeaf
import io.kotest.matchers.shouldBe
import java.util.UUID.randomUUID
import kotlin.random.Random.Default.nextInt

class StaleRecordSpec : PersistenceBaseSpec({

    isolationMode = InstancePerLeaf

    val departmentRepo = module.departmentRepo
    val employeeRepo = module.employeeRepo

    Given("Models exist") {
        val department = module.writableRepository.add(
            newDepartment(
                name = "stale_dep${nextInt(100)}",
                boss = EmployeeId(randomUUID()),
                headcount = 1,
                ration = SHAKSHOUKA
            )
        )

        And("Model will be updated before uow transaction is completed") {
            val employeeOutOfUow = newEmployee(
                name = Name("Igor", "Dmitri${nextInt(100)}"),
                departmentId = department.id(),
                email = "igor.dmitri${nextInt(100)}@razz.com",
                ration = SHAKSHOUKA
            )

            var updatedOutOfUow = false
            module.departmentPreUpdate.onPreUpdate(department.id()) {
                if (!updatedOutOfUow) {
                    updatedOutOfUow = true
                    module.writableRepository.apply {
                        add(employeeOutOfUow)
                        update(
                            checkNotNull(departmentRepo.find(department.id()))
                                .addEmployee(employeeOutOfUow)
                        )
                    }
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

                    departmentRepo.find(department.id())?.headcount shouldBe 2
                    employeeRepo.findByName(Name("Nik", "Dennis")) shouldBe null
                }
            }

            When("Principal performs retriable uow") {
                val avengers = module.uowxRetries.execute(HireEmployeesUow::class, TestPrincipal) {
                    HireEmployeesUow.Params(
                        department.id(),
                        listOf(Name("Ser", "Pryt"), Name("Ser", "Posp"), Name("Pryt", "Posp"))
                    )
                }

                Then("Uow is completed and changes are applied") {
                    departmentRepo.find(department.id())?.headcount shouldBe 5
                    avengers.size shouldBe 3
                    avengers.forEach { sp ->
                        val persisted = employeeRepo.find(sp.id())!!
                        persisted.departmentId shouldBe department.id()
                        persisted.version() shouldBe sp.version()
                        sp.isPersisted() shouldBe true
                    }
                    employeeRepo.find(employeeOutOfUow.id())?.departmentId shouldBe department.id()
                }
            }
        }
    }
})
