package com.razz.eva.uow.func

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.events.UowEvent.UowName
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.uow.HireEmployeesUow
import com.razz.eva.uow.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.random.Random.Default.nextInt
import java.util.UUID.randomUUID

class WriteTxScopeSpec : PersistenceBaseSpec({

    val departmentRepo = module.departmentRepo
    val employeeRepo = module.employeeRepo
    val writableRepository = module.writableRepository

    Given("A department exists") {
        val department = writableRepository.add(
            newDepartment(
                name = "fulluow_dep${nextInt(100000)}",
                boss = EmployeeId(randomUUID()),
                headcount = 1,
                ration = SHAKSHOUKA,
            ),
        )

        When("Principal performs a FULL_UOW scoped uow") {
            module.publishedUowEvents.clear()
            val hired = module.uowxFullUowScope.execute(HireEmployeesUow::class, TestPrincipal) {
                HireEmployeesUow.Params(
                    department.id(),
                    listOf(Name("Elu", "Thingol"), Name("Finwe", "Noldoran")),
                )
            }

            Then("Employees and department update are committed in one transaction") {
                hired.size shouldBe 2
                hired.forEach { employee ->
                    employee.isPersisted() shouldBe true
                    val persisted = requireNotNull(employeeRepo.find(employee.id()))
                    persisted.departmentId shouldBe department.id()
                    persisted.version() shouldBe employee.version()
                }
                departmentRepo.find(department.id())?.headcount shouldBe 3
            }

            Then("The uow event is published after the commit") {
                module.publishedUowEvents.size shouldBe 1
                module.publishedUowEvents.single().uowName shouldBe UowName("HireEmployeesUow")
            }
        }
    }

    Given("A department exists and will be updated concurrently before the flush") {
        val department = writableRepository.add(
            newDepartment(
                name = "fulluow_stale_dep${nextInt(100000)}",
                boss = EmployeeId(randomUUID()),
                headcount = 1,
                ration = SHAKSHOUKA,
            ),
        )
        val employeeOutOfUow = newEmployee(
            name = Name("Beren", "Erchamion${nextInt(100000)}"),
            departmentId = department.id(),
            email = "beren.erchamion${nextInt(100000)}@razz.com",
            ration = SHAKSHOUKA,
        )
        var updatedOutOfUow = false
        module.departmentPreUpdate.onPreUpdate(department.id()) {
            if (!updatedOutOfUow) {
                updatedOutOfUow = true
                writableRepository.apply {
                    add(employeeOutOfUow)
                    update(
                        checkNotNull(departmentRepo.find(department.id()))
                            .addEmployee(employeeOutOfUow),
                    )
                }
            }
        }

        When("Principal performs a FULL_UOW scoped uow with a retry budget") {
            module.publishedUowEvents.clear()
            val hired = module.uowxFullUowScope.execute(HireEmployeesUow::class, TestPrincipal) {
                HireEmployeesUow.Params(
                    department.id(),
                    listOf(Name("Luthien", "Tinuviel")),
                )
            }

            Then("First attempt rolls back on the stale department and the retry commits") {
                hired.size shouldBe 1
                requireNotNull(employeeRepo.find(hired.single().id())).departmentId shouldBe department.id()
                departmentRepo.find(department.id())?.headcount shouldBe 3
                employeeRepo.find(employeeOutOfUow.id())?.departmentId shouldBe department.id()
            }

            Then("Only the committed attempt is published") {
                module.publishedUowEvents.size shouldBe 1
            }
        }
    }

    Given("Another department exists and will be updated concurrently before the flush") {
        val department = writableRepository.add(
            newDepartment(
                name = "fulluow_noretry_dep${nextInt(100000)}",
                boss = EmployeeId(randomUUID()),
                headcount = 1,
                ration = SHAKSHOUKA,
            ),
        )
        val employeeOutOfUow = newEmployee(
            name = Name("Turin", "Turambar${nextInt(100000)}"),
            departmentId = department.id(),
            email = "turin.turambar${nextInt(100000)}@razz.com",
            ration = SHAKSHOUKA,
        )
        var updatedOutOfUow = false
        module.departmentPreUpdate.onPreUpdate(department.id()) {
            if (!updatedOutOfUow) {
                updatedOutOfUow = true
                writableRepository.apply {
                    add(employeeOutOfUow)
                    update(
                        checkNotNull(departmentRepo.find(department.id()))
                            .addEmployee(employeeOutOfUow),
                    )
                }
            }
        }

        When("Principal performs a FULL_UOW scoped uow without retries") {
            module.publishedUowEvents.clear()
            val attempt = suspend {
                module.uowxFullUowScopeNoRetries.execute(HireEmployeesUow::class, TestPrincipal) {
                    HireEmployeesUow.Params(
                        department.id(),
                        listOf(Name("Nienor", "Niniel")),
                    )
                }
            }

            Then("The whole attempt rolls back leaving no partial state") {
                shouldThrow<StaleRecordException> { attempt() }
                departmentRepo.find(department.id())?.headcount shouldBe 2
                employeeRepo.findByName(Name("Nienor", "Niniel")) shouldBe null
            }

            Then("Nothing is published for the rolled back attempt") {
                module.publishedUowEvents.size shouldBe 0
            }
        }
    }
})
